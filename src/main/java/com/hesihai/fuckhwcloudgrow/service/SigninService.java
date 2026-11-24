package com.hesihai.fuckhwcloudgrow.service;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.hesihai.fuckhwcloudgrow.auth.CredentialService;
import com.hesihai.fuckhwcloudgrow.auth.CredentialStatus;
import com.hesihai.fuckhwcloudgrow.auth.StoredCookie;
import com.hesihai.fuckhwcloudgrow.client.GrowthApi;
import com.hesihai.fuckhwcloudgrow.client.GrowthApiException;
import com.hesihai.fuckhwcloudgrow.client.HttpAccess;
import com.hesihai.fuckhwcloudgrow.client.HuaweiUrls;
import com.hesihai.fuckhwcloudgrow.client.dto.SignStatusResponse;
import com.hesihai.fuckhwcloudgrow.client.dto.UserInfoResponse;
import com.hesihai.fuckhwcloudgrow.config.HwcloudGrowProperties;
import com.hesihai.fuckhwcloudgrow.model.FailCode;
import com.hesihai.fuckhwcloudgrow.model.LiveStatus;
import com.hesihai.fuckhwcloudgrow.model.RunTrigger;
import com.hesihai.fuckhwcloudgrow.model.SigninResult;
import com.hesihai.fuckhwcloudgrow.model.SigninStatus;
import com.hesihai.fuckhwcloudgrow.notify.XizhiNotifier;
import com.hesihai.fuckhwcloudgrow.service.task.AutomationLock;
import com.hesihai.fuckhwcloudgrow.service.task.TaskContext;
import com.hesihai.fuckhwcloudgrow.storage.SigninResultStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * 每日签到任务：并发控制、HTTP 执行、结果持久化与通知。
 *
 * <p>登录态由 {@link CredentialService} 负责，本类不再启动浏览器。</p>
 */
@Slf4j
@Service
public class SigninService {

    private final HwcloudGrowProperties properties;
    private final CredentialService credentials;
    private final HttpAccess httpAccess;
    private final SigninFlow signinFlow;
    private final SigninResultStore resultStore;
    private final XizhiNotifier notifier;
    private final AutomationLock automationLock;

    public SigninService(HwcloudGrowProperties properties, CredentialService credentials,
                         HttpAccess httpAccess, SigninFlow signinFlow,
                         SigninResultStore resultStore, XizhiNotifier notifier,
                         AutomationLock automationLock) {
        this.properties = properties;
        this.credentials = credentials;
        this.httpAccess = httpAccess;
        this.signinFlow = signinFlow;
        this.resultStore = resultStore;
        this.notifier = notifier;
        this.automationLock = automationLock;
        log.info("签到配置：cron={}, zone={}, 账号已配置={}, 密码已配置={}",
                properties.getCron(), properties.getZone(),
                StrUtil.isNotBlank(properties.getUsername()),
                StrUtil.isNotEmpty(properties.getPassword()));
    }

    private static String apiFailureMessage(GrowthApiException e) {
        String message = StrUtil.blankToDefault(e.getMessage(), "华为云接口调用失败");
        if (StrUtil.isBlank(e.getVendorCode())) {
            return message;
        }
        return message + "（" + e.getVendorCode() + "）";
    }

    /**
     * 执行一次签到（旧接口入口）：先由凭据层确保登录态，再走 HTTP 签到，结果按状态推送通知。
     *
     * @param trigger 触发来源
     * @return 签到结果
     * @throws SigninBusyException 已有任务正在执行
     */
    public SigninResult run(RunTrigger trigger) {
        if (!automationLock.tryAcquire()) {
            throw new SigninBusyException("任务正在执行中，请稍后重试");
        }
        try {
            String runId = IdUtil.randomUUID();
            Instant startedAt = Instant.now();
            CredentialStatus credential = credentials.ensureValid(List.of(HuaweiUrls.API_HOST), runId);
            SigninResult result = credential.valid()
                    ? executeHttp(runId, trigger, startedAt, credential.isHumanIntervention())
                    : credentialFailure(runId, trigger, startedAt, credential);
            resultStore.save(result);
            notifyResult(result);
            return result;
        } finally {
            automationLock.release();
        }
    }

    /**
     * 在批次内执行签到：凭据已由编排器确认，这里只发 HTTP 请求。
     *
     * @param trigger 触发来源
     * @param ctx     批次上下文；为 null 时按独立执行处理
     * @return 签到结果
     */
    public SigninResult run(RunTrigger trigger, TaskContext ctx) {
        if (!automationLock.tryAcquire()) {
            throw new SigninBusyException("任务正在执行中，请稍后重试");
        }
        try {
            String runId = ObjectUtil.isNull(ctx) ? IdUtil.randomUUID() : ctx.runId();
            Instant startedAt = Instant.now();
            SigninResult result = executeHttp(runId, trigger, startedAt, false);
            resultStore.save(result);
            if (ObjectUtil.isNull(ctx) || ctx.notifySelf()) {
                notifyResult(result);
            }
            return result;
        } finally {
            automationLock.release();
        }
    }

    public SigninResult lastResult() {
        return resultStore.loadLast();
    }

    /**
     * 只读查询当前登录态与签到状态；只走 HTTP，不启动浏览器。
     */
    public LiveStatus queryStatus() {
        List<StoredCookie> cookies = httpAccess.cookies();
        if (CollUtil.isEmpty(cookies)) {
            return LiveStatus.builder()
                    .sessionValid(false)
                    .message("未找到本地登录态，请先执行签到任务")
                    .build();
        }
        try (GrowthApi api = httpAccess.growthApi()) {
            UserInfoResponse user = api.getUserInfo();
            SignStatusResponse status = querySignStatusQuietly(api);
            return LiveStatus.builder()
                    .sessionValid(true)
                    .accountName(StrUtil.firstNonBlank(
                            ObjectUtil.isNotNull(user) ? user.getDomainName() : null,
                            ObjectUtil.isNotNull(user) ? user.getIamId() : null))
                    .signFlag(ObjectUtil.isNotNull(status) ? status.getSignFlag() : null)
                    .signedToday(ObjectUtil.isNotNull(status)
                            && ObjectUtil.equal(status.getSignFlag(), 1))
                    .beans(ObjectUtil.isNotNull(user) ? user.getTotalPoints() : null)
                    .message(ObjectUtil.isNotNull(status) ? "查询成功" : "查询成功（签到任务状态暂不可用）")
                    .build();
        } catch (GrowthApiException e) {
            return LiveStatus.builder()
                    .sessionValid(false)
                    .message("查询失败：" + apiFailureMessage(e))
                    .build();
        } catch (Exception e) {
            return LiveStatus.builder()
                    .sessionValid(false)
                    .message("查询失败：" + e.getMessage())
                    .build();
        }
    }

    private SigninResult executeHttp(String runId, RunTrigger trigger, Instant startedAt,
                                     boolean humanIntervention) {
        try (GrowthApi api = httpAccess.growthApi()) {
            return signinFlow.execute(api, runId, trigger, startedAt, humanIntervention);
        } catch (GrowthApiException e) {
            return SigninResultFactory.failure(runId, trigger, startedAt, e.getCode(),
                    apiFailureMessage(e), "HTTP", humanIntervention);
        }
    }

    private SigninResult credentialFailure(String runId, RunTrigger trigger, Instant startedAt,
                                           CredentialStatus credential) {
        FailCode code = credential.getLevel() == CredentialStatus.Level.WAF_BLOCKED
                ? FailCode.WAF_BLOCKED
                : FailCode.SESSION_EXPIRED;
        return SigninResultFactory.failure(runId, trigger, startedAt, code,
                "签到未执行：" + credential.getMessage(), "NONE", credential.isHumanIntervention());
    }

    private void notifyResult(SigninResult result) {
        if (result.getStatus() == SigninStatus.FAILED) {
            notifier.failed(result);
        } else {
            notifier.dailyResult(result);
        }
    }

    /**
     * 只读查询签到任务状态；任务接口偶发失败时返回 null，不影响登录态判定。
     */
    private SignStatusResponse querySignStatusQuietly(GrowthApi api) {
        try {
            return api.getSignStatus();
        } catch (GrowthApiException e) {
            if (e.getCode() == FailCode.SESSION_EXPIRED) {
                throw e;
            }
            log.warn("查询签到任务状态失败：{}", e.getMessage());
            return null;
        }
    }
}