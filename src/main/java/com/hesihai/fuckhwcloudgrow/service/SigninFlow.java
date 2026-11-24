package com.hesihai.fuckhwcloudgrow.service;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.hesihai.fuckhwcloudgrow.client.GrowthApi;
import com.hesihai.fuckhwcloudgrow.client.GrowthApiException;
import com.hesihai.fuckhwcloudgrow.client.dto.BeansInfoResponse;
import com.hesihai.fuckhwcloudgrow.client.dto.SignStatusResponse;
import com.hesihai.fuckhwcloudgrow.client.dto.SignSubmitOutcome;
import com.hesihai.fuckhwcloudgrow.client.dto.UserInfoResponse;
import com.hesihai.fuckhwcloudgrow.config.HwcloudGrowProperties;
import com.hesihai.fuckhwcloudgrow.model.FailCode;
import com.hesihai.fuckhwcloudgrow.model.RunTrigger;
import com.hesihai.fuckhwcloudgrow.model.SigninResult;
import com.hesihai.fuckhwcloudgrow.model.SigninStatus;
import com.hesihai.fuckhwcloudgrow.util.SleepUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * 签到业务编排：只依赖 {@link GrowthApi}，因此 Java HTTP 与浏览器内 fetch 共用同一套逻辑。
 *
 * <p>失败时抛出 {@link GrowthApiException}，由 {@link SigninService} 决定降级、重试或记录失败。</p>
 */
@Slf4j
@Component
public class SigninFlow {

    private final HwcloudGrowProperties properties;

    public SigninFlow(HwcloudGrowProperties properties) {
        this.properties = properties;
    }

    /**
     * 判断签到状态是否已标记今日已签到（signFlag == 1）。
     */
    private static boolean isSignedToday(SignStatusResponse status) {
        return ObjectUtil.isNotNull(status) && ObjectUtil.equal(status.getSignFlag(), 1);
    }

    /**
     * 构造“已完成签到”的状态，供结果展示。
     */
    private static SignStatusResponse signedStatus() {
        SignStatusResponse status = new SignStatusResponse();
        status.setSignFlag(1);
        return status;
    }

    /**
     * 执行签到：查状态、已签则返回，未签则提交并直接使用提交结果。
     *
     * @param api               签到接口实现（HTTP 或 Playwright）
     * @param runId             任务唯一 ID
     * @param trigger           触发来源
     * @param startedAt         任务开始时间
     * @param humanIntervention 是否经过人工验证（浏览器登录时可能为 true）
     * @return 签到结果
     * @throws GrowthApiException 业务失败或网络异常，由 {@link SigninService} 决定降级与重试
     */
    public SigninResult execute(GrowthApi api, String runId, RunTrigger trigger,
                                Instant startedAt, boolean humanIntervention) {
        UserInfoResponse user = api.getUserInfo();
        SignStatusResponse current = safeSignStatus(api);

        // 账号名优先取接口返回值，最后才回退到配置，避免不同环境记录不一致
        String accountName = StrUtil.firstNonBlank(
                ObjectUtil.isNotNull(user) ? user.getDomainName() : null,
                ObjectUtil.isNotNull(user) ? user.getIamId() : null,
                properties.getAccountName(),
                properties.getUsername());
        if (accountName == null) {
            throw new GrowthApiException(FailCode.CONFIG_MISSING,
                    "无法确定签到账号名（domain_name/iam_id/HWCLOUD_ACCOUNT_NAME 均为空）");
        }

        BeansInfoResponse before = safeBeans(api);
        if (isSignedToday(current)) {
            return SigninResultFactory.success(runId, trigger, SigninStatus.ALREADY_SIGNED, true,
                    accountName, current, before, before, "今日已签到", api.source(),
                    humanIntervention, startedAt);
        }

        SignSubmitOutcome outcome = submitWithRetry(api);
        if (!outcome.isSuccess()) {
            throw new GrowthApiException(FailCode.UNKNOWN,
                    StrUtil.blankToDefault(outcome.getVendorMessage(), "签到请求未成功"),
                    outcome.getVendorCode(), false, outcome.getRawBody());
        }
        log.info("签到响应：signed={}, points={}, totalPoints={}",
                outcome.isSigned(), outcome.getPoints(), outcome.getTotalPoints());
        // 平台保证签到幂等；只要接口确认调用后今日已签到，就视为成功，不再用积分变化判断
        if (!outcome.isSigned()) {
            throw new GrowthApiException(FailCode.UNKNOWN,
                    StrUtil.blankToDefault(outcome.getVendorMessage(), "签到接口未确认今日已签到"),
                    outcome.getVendorCode(), false, outcome.getRawBody());
        }
        BeansInfoResponse after = afterPoints(api, outcome);
        return SigninResultFactory.success(runId, trigger, SigninStatus.SUCCESS, false,
                accountName, signedStatus(), before, after, "签到成功", api.source(),
                humanIntervention, startedAt);
    }

    /**
     * 状态查询失败不应阻塞签到：只有登录态失效与 WAF 拦截值得向上传递。
     */
    private SignStatusResponse safeSignStatus(GrowthApi api) {
        try {
            return api.getSignStatus();
        } catch (GrowthApiException e) {
            if (e.getCode() == FailCode.SESSION_EXPIRED || e.getCode() == FailCode.WAF_BLOCKED) {
                throw e;
            }
            log.warn("查询签到任务状态失败，按未知状态继续：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 积分只用于展示，查询失败不影响签到结果。
     */
    private BeansInfoResponse safeBeans(GrowthApi api) {
        try {
            return api.getBeans();
        } catch (GrowthApiException e) {
            log.warn("查询积分失败，本次结果不展示积分：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 签到后的积分优先取接口返回值，缺失时再查一次（失败则为空）。
     */
    private BeansInfoResponse afterPoints(GrowthApi api, SignSubmitOutcome outcome) {
        if (ObjectUtil.isNotNull(outcome.getTotalPoints())) {
            return new BeansInfoResponse(outcome.getTotalPoints());
        }
        return safeBeans(api);
    }

    /**
     * 可重试错误做有限次随机退避重试，其他错误直接上抛。
     */
    private SignSubmitOutcome submitWithRetry(GrowthApi api) {
        int attempts = Math.max(1, properties.getApi().getMaxAttempts());
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                return api.submitSign();
            } catch (GrowthApiException e) {
                if (e.isRetryable() && attempt < attempts) {
                    long base = Math.max(1, properties.getApi().getRetryBackoff().toMillis());
                    long delay = RandomUtil.randomLong(base, base * 3);
                    log.info("签到请求可重试，{}ms 后重试（{}/{}）", delay, attempt, attempts);
                    SleepUtils.sleep(Duration.ofMillis(delay),
                            ie -> new GrowthApiException(FailCode.NETWORK, "签到等待被中断", ie));
                    continue;
                }
                throw e;
            }
        }
        // 循环内所有分支要么返回要么抛异常，编译器无法推断此点不可达
        throw new IllegalStateException("unreachable");
    }
}