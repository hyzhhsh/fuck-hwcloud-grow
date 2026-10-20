package com.hesihai.fuckhwcloudgrow.service;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.hesihai.fuckhwcloudgrow.client.GrowthApi;
import com.hesihai.fuckhwcloudgrow.client.GrowthApiException;
import com.hesihai.fuckhwcloudgrow.client.dto.BeansInfoResponse;
import com.hesihai.fuckhwcloudgrow.client.dto.MemberInfoResponse;
import com.hesihai.fuckhwcloudgrow.client.dto.SignStatusResponse;
import com.hesihai.fuckhwcloudgrow.client.dto.SignSubmitOutcome;
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
     * 执行完整签到流程：查状态、已签则返回，未签则提交并轮询确认。
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
        MemberInfoResponse member = api.getMemberInfo();
        SignStatusResponse current = api.getSignStatus();
        if (ObjectUtil.isNull(current)) {
            throw new GrowthApiException(FailCode.UNKNOWN, "签到状态响应为空");
        }

        // 账号名优先取接口返回值，最后才回退到配置，避免不同环境记录不一致
        String accountName = StrUtil.firstNonBlank(
                ObjectUtil.isNotNull(member) ? member.getMemAlias() : null,
                ObjectUtil.isNotNull(member) ? member.getMemName() : null,
                properties.getAccountName(),
                properties.getUsername());
        if (accountName == null) {
            throw new GrowthApiException(FailCode.CONFIG_MISSING,
                    "无法确定签到账号名（memAlias/memName/HWCLOUD_ACCOUNT_NAME 均为空）");
        }

        if (isSignedToday(current)) {
            BeansInfoResponse beans = api.getBeans();
            return SigninResultFactory.success(runId, trigger, SigninStatus.ALREADY_SIGNED, true,
                    accountName, current, beans, beans, "今日已签到", api.source(),
                    humanIntervention, startedAt);
        }

        BeansInfoResponse before = api.getBeans();
        SignSubmitOutcome outcome = submitWithBusyRetry(api, accountName);
        if (outcome.isAlreadySigned()) {
            SignStatusResponse latest = safeLatestStatus(api, current);
            BeansInfoResponse after = api.getBeans();
            return SigninResultFactory.success(runId, trigger, SigninStatus.ALREADY_SIGNED, true,
                    accountName, latest, before, after, "今日已签到", api.source(),
                    humanIntervention, startedAt);
        }
        if (!outcome.isSuccess()) {
            throw new GrowthApiException(FailCode.UNKNOWN,
                    StrUtil.blankToDefault(outcome.getVendorMessage(), "签到请求未成功"),
                    outcome.getVendorCode(), false, outcome.getRawBody());
        }

        // 提交成功后轮询 sign_flag，给厂商侧状态同步留出时间
        SignStatusResponse latest = current;
        boolean synced = false;
        int attempts = Math.max(1, properties.getRun().getStatusPollAttempts());
        for (int i = 0; i < attempts; i++) {
            latest = api.getSignStatus();
            if (isSignedToday(latest)) {
                synced = true;
                break;
            }
            if (i < attempts - 1) {
                SleepUtils.sleep(properties.getRun().getStatusPollInterval(),
                        e -> new GrowthApiException(FailCode.NETWORK, "签到等待被中断", e));
            }
        }

        BeansInfoResponse after = api.getBeans();
        // 轮询结束时 sign_flag 未变也不报失败：签到请求已被受理，状态只是尚未同步
        String message = synced ? "签到成功" : "签到请求已提交，状态同步中";
        if (!synced) {
            log.warn("签到请求已成功，但轮询 {} 次后 sign_flag 仍未变为 1", attempts);
        }
        return SigninResultFactory.success(runId, trigger, SigninStatus.SUCCESS, false,
                accountName, latest, before, after, message, api.source(),
                humanIntervention, startedAt);
    }

    /**
     * 签到人数较多时做有限次随机退避重试，其他错误直接上抛。
     */
    private SignSubmitOutcome submitWithBusyRetry(GrowthApi api, String accountName) {
        int attempts = Math.max(1, properties.getApi().getMaxAttempts());
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                return api.submitSign(accountName);
            } catch (GrowthApiException e) {
                if (e.getCode() == FailCode.SIGN_BUSY && attempt < attempts) {
                    long base = Math.max(1, properties.getApi().getSignBusyBackoff().toMillis());
                    long delay = RandomUtil.randomLong(base, base * 3);
                    log.info("签到人数较多，{}ms 后重试（{}/{}）", delay, attempt, attempts);
                    SleepUtils.sleep(Duration.ofMillis(delay),
                            ie -> new GrowthApiException(FailCode.NETWORK, "签到等待被中断", ie));
                    continue;
                }
                throw e;
            }
        }
        // 循环内所有分支要么返回要么抛异常（最后一次 busy 也会走 throw e），
        // 编译器无法推断此点不可达，此防御性抛出实际不会执行
        throw new IllegalStateException("unreachable");
    }

    /**
     * 已签到分支查询最新状态失败时不影响主结果，退回提交前的状态。
     */
    private SignStatusResponse safeLatestStatus(GrowthApi api, SignStatusResponse fallback) {
        try {
            return api.getSignStatus();
        } catch (GrowthApiException e) {
            return fallback;
        }
    }
}
