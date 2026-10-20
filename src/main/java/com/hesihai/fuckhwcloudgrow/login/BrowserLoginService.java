package com.hesihai.fuckhwcloudgrow.login;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hesihai.fuckhwcloudgrow.auth.AuthStateStore;
import com.hesihai.fuckhwcloudgrow.client.HuaweiUrls;
import com.hesihai.fuckhwcloudgrow.client.browser.BrowserSession;
import com.hesihai.fuckhwcloudgrow.config.HwcloudGrowProperties;
import com.hesihai.fuckhwcloudgrow.model.FailCode;
import com.hesihai.fuckhwcloudgrow.notify.XizhiNotifier;
import com.hesihai.fuckhwcloudgrow.util.SleepUtils;
import com.microsoft.playwright.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * 华为云个人账号登录流程：自动填充、验证码/MFA 检测、人工等待与登录态保存。
 *
 * <p>页面操作、状态判断、持久化分别由 {@link LoginPageDriver}、{@link LoginDetector}、
 * {@link LoginStatePersister} 负责，本类只编排登录步骤与重试。</p>
 */
@Slf4j
@Component
public class BrowserLoginService {

    private static final int LOGIN_ROUNDS = 3;
    private static final Duration FORM_WAIT = Duration.ofSeconds(30);
    private static final Duration CHALLENGE_GRACE = Duration.ofSeconds(20);
    private static final Duration POST_SUBMIT_WAIT = Duration.ofSeconds(2);
    private static final long RISK_RETRY_WAIT_MS = 2000;
    /**
     * 风控拦截的对外统一描述，用于结果消息。
     */
    private static final String RISK_BLOCKED_MESSAGE = "风控拦截（408）";

    private final HwcloudGrowProperties properties;
    private final XizhiNotifier notifier;
    private final LoginDetector detector;
    private final LoginStatePersister persister;

    public BrowserLoginService(HwcloudGrowProperties properties, AuthStateStore authStateStore,
                               XizhiNotifier notifier, ObjectMapper objectMapper) {
        this.properties = properties;
        this.notifier = notifier;
        this.detector = new LoginDetector(properties);
        this.persister = new LoginStatePersister(authStateStore, objectMapper);
    }

    /**
     * 确保浏览器处于已登录状态：复用已有登录态，否则走账号密码登录流程。
     *
     * @param session 浏览器会话
     * @param runId   任务 ID，用于诊断材料命名
     * @return 登录结果，所有异常就地转为失败结果，不向上抛
     */
    public LoginOutcome ensureLoggedIn(BrowserSession session, String runId) {
        Page page = session.page();
        try {
            LoginPageDriver.navigate(page, HuaweiUrls.GROW_URL);
            if (detector.waitForLoggedIn(session, FORM_WAIT)) {
                log.info("复用已有浏览器登录态");
                persister.save(session, runId);
                return LoginOutcome.ok();
            }

            String username = properties.getUsername();
            String password = properties.getPassword();
            if (StrUtil.isBlank(username) || StrUtil.isEmpty(password)) {
                return LoginOutcome.failure(FailCode.CONFIG_MISSING,
                        "未配置 HWCLOUD_USERNAME / HWCLOUD_PASSWORD，且浏览器 profile 未登录");
            }
            log.info("浏览器 profile 未登录，开始使用账号密码登录");

            for (int round = 1; round <= LOGIN_ROUNDS; round++) {
                LoginRoundResult result = performLoginRound(session, page, runId, round);
                if (result.success()) {
                    persister.save(session, runId);
                    return result.humanIntervention()
                            ? LoginOutcome.okAfterHuman()
                            : LoginOutcome.ok();
                }
                if (result.riskControl()) {
                    if (round < LOGIN_ROUNDS) {
                        log.warn("华为云登录被风控拦截（408），重新进入登录页重试（{}/{}）",
                                round, LOGIN_ROUNDS);
                        page.waitForTimeout(RISK_RETRY_WAIT_MS);
                        continue;
                    }
                    return LoginOutcome.failure(FailCode.LOGIN_FAILED,
                            "多次登录仍被华为云" + RISK_BLOCKED_MESSAGE + "，请稍后再试");
                }
                return result.humanIntervention()
                        ? LoginOutcome.failureAfterHuman(result.failCode(), result.message())
                        : LoginOutcome.failure(result.failCode(), result.message());
            }
            return LoginOutcome.failure(FailCode.LOGIN_FAILED, "华为云登录失败");
        } catch (Exception e) {
            log.warn("浏览器登录失败：{}", e.getMessage());
            persister.saveDiagnostics(page, runId, "login-exception");
            return LoginOutcome.failure(FailCode.LOGIN_FAILED, "浏览器登录异常：" + e.getMessage());
        }
    }

    /**
     * 单轮登录：进入登录页、填充表单、提交，并处理风控/验证码/错误三种分支。
     */
    private LoginRoundResult performLoginRound(BrowserSession session, Page page,
                                               String runId, int round) {
        LoginPageDriver.navigate(page, HuaweiUrls.LOGIN_URL);
        log.info("第 {} 次进入华为云登录页，等待登录表单，当前页面：{}", round, page.url());

        boolean formReady = false;
        Instant formDeadline = Instant.now().plus(FORM_WAIT);
        while (Instant.now().isBefore(formDeadline)) {
            if (detector.isLoggedIn(session)) {
                log.info("登录页跳转后已检测到登录态");
                return LoginRoundResult.success(false);
            }
            Page current = LoginPageDriver.loginPage(session);
            if (ObjectUtil.isNotNull(current) && detector.isRiskControlPage(current)) {
                log.warn("登录页被风控拦截（408）");
                return LoginRoundResult.riskControlResult();
            }
            if (ObjectUtil.isNotNull(current) && detector.loginFormVisible(current)) {
                formReady = true;
                break;
            }
            SleepUtils.sleep(properties.getBrowser().getPollInterval(),
                    e -> new IllegalStateException("登录等待被中断", e));
        }
        if (!formReady) {
            Page current = LoginPageDriver.loginPage(session);
            if (ObjectUtil.isNotNull(current) && detector.isRiskControlPage(current)) {
                log.warn("登录页被风控拦截（408）");
                return LoginRoundResult.riskControlResult();
            }
            log.warn("登录页未加载完成，当前页面：{}", ObjectUtil.isNull(current) ? "null" : current.url());
            persister.saveDiagnostics(current, runId, "login-page-timeout");
            return LoginRoundResult.failure(FailCode.LOGIN_FAILED,
                    "登录页未加载完成，当前页面：" + (ObjectUtil.isNull(current) ? "null" : current.url()),
                    false);
        }

        Page current = LoginPageDriver.loginPage(session);
        if (ObjectUtil.isNull(current) || current.isClosed()) {
            return LoginRoundResult.failure(FailCode.LOGIN_FAILED, "登录页面不存在", false);
        }
        if (!LoginPageDriver.selectAccountTabIfNeeded(current)) {
            persister.saveDiagnostics(current, runId, "login-form-not-found");
            return LoginRoundResult.failure(FailCode.LOGIN_FAILED, "未找到华为云账号登录表单", false);
        }
        if (!LoginPageDriver.fillFirst(current, LoginSelectors.PERSONAL_ACCOUNT_INPUTS,
                properties.getUsername().trim())
                || !LoginPageDriver.fillFirst(current, LoginSelectors.PERSONAL_PASSWORD_INPUTS,
                properties.getPassword())) {
            persister.saveDiagnostics(current, runId, "fill-credentials-failed");
            return LoginRoundResult.failure(FailCode.LOGIN_FAILED, "自动填充账号密码失败", false);
        }
        log.info("已填充账号密码");

        if (detector.isRiskControlPage(current)) {
            log.warn("提交前检测到风控页（408）");
            return LoginRoundResult.riskControlResult();
        }
        String preSubmitChallenge = detector.detectChallenge(current);
        if (ObjectUtil.isNotNull(preSubmitChallenge)) {
            return waitForHuman(session, runId, preSubmitChallenge);
        }
        if (!LoginPageDriver.clickLoginButton(current)) {
            persister.saveDiagnostics(current, runId, "login-button-not-found");
            return LoginRoundResult.failure(FailCode.LOGIN_FAILED, "未找到登录按钮", false);
        }
        log.info("已提交登录，等待登录结果");

        // AttemptResult 三态分派：成功直接返回；风控交给外层重试；
        // 有明确失败原因记失败；都没有说明需要人工验证
        AttemptResult attempt = waitForLoginAttempt(session);
        if (attempt.success()) {
            return LoginRoundResult.success(false);
        }
        if (attempt.riskControl()) {
            return LoginRoundResult.riskControlResult();
        }
        if (ObjectUtil.isNotNull(attempt.failCode())) {
            persister.saveDiagnostics(session.activePage(), runId, "login-failed");
            return LoginRoundResult.failure(attempt.failCode(), attempt.message(), false);
        }
        return waitForHuman(session, runId, attempt.manualReason());
    }

    /**
     * 提交后轮询登录结果，直到成功、失败、需要人工或超时。
     */
    private AttemptResult waitForLoginAttempt(BrowserSession session) {
        Page page = session.activePage();
        page.waitForTimeout(POST_SUBMIT_WAIT.toMillis());
        Instant start = Instant.now();
        Instant deadline = start.plus(properties.getBrowser().getLoginTimeout());

        while (Instant.now().isBefore(deadline)) {
            if (detector.isLoggedIn(session)) {
                return AttemptResult.ok();
            }
            if (LoginPageDriver.allPagesClosed(session)) {
                return AttemptResult.failure(FailCode.LOGIN_FAILED, "登录过程中浏览器被关闭");
            }
            Page current = LoginPageDriver.loginPage(session);
            if (ObjectUtil.isNull(current) || current.isClosed()) {
                return AttemptResult.failure(FailCode.LOGIN_FAILED, "浏览器页面不存在");
            }
            if (detector.isRiskControlPage(current)) {
                return AttemptResult.riskControlResult();
            }

            String error = detector.findError(current);
            if (ObjectUtil.isNotNull(error)) {
                return AttemptResult.failure(FailCode.CREDENTIAL_INVALID, error);
            }
            String challenge = detector.detectChallenge(current);
            if (ObjectUtil.isNotNull(challenge)) {
                return AttemptResult.manual(challenge);
            }
            // 提交后仍停留在认证页超过宽限期，通常还需要短信/扫码等人工验证
            if (Duration.between(start, Instant.now()).compareTo(CHALLENGE_GRACE) >= 0
                    && LoginPageDriver.isAuthPage(current)) {
                return AttemptResult.manual("登录后仍停留在华为云验证页面");
            }
            SleepUtils.sleep(Duration.ofSeconds(1),
                    e -> new IllegalStateException("登录等待被中断", e));
        }

        Page current = LoginPageDriver.loginPage(session);
        if (current != null && detector.isRiskControlPage(current)) {
            return AttemptResult.riskControlResult();
        }
        if (current != null && LoginPageDriver.isAuthPage(current)) {
            return AttemptResult.manual("登录等待超时，可能仍需人工验证");
        }
        return AttemptResult.failure(FailCode.LOGIN_FAILED, "登录超时");
    }

    /**
     * 通知并等待人工完成验证，期间持续探测登录态与风控。
     */
    private LoginRoundResult waitForHuman(BrowserSession session, String runId, String reason) {
        Page current = LoginPageDriver.loginPage(session);
        if (current != null && detector.isRiskControlPage(current)) {
            log.warn("人工介入前检测到 408 风控页，自动重新进入登录页");
            return LoginRoundResult.riskControlResult();
        }
        log.info("检测到需要人工介入：{}", reason);
        notifier.humanRequired(runId, reason,
                properties.getNotify().getNoVncUrl(), properties.getBrowser().getHumanWait());
        persister.saveDiagnostics(current, runId, "manual-wait");

        Instant deadline = Instant.now().plus(properties.getBrowser().getHumanWait());
        while (Instant.now().isBefore(deadline)) {
            if (detector.isLoggedIn(session)) {
                return LoginRoundResult.success(true);
            }
            if (LoginPageDriver.allPagesClosed(session)) {
                return LoginRoundResult.failure(
                        FailCode.LOGIN_FAILED, "等待人工验证期间浏览器被关闭", true);
            }
            Page active = LoginPageDriver.loginPage(session);
            if (ObjectUtil.isNotNull(active) && detector.isRiskControlPage(active)) {
                log.warn("人工等待期间检测到 408 风控页，自动重新进入登录页");
                return LoginRoundResult.riskControlResult();
            }
            SleepUtils.sleep(properties.getBrowser().getPollInterval(),
                    e -> new IllegalStateException("登录等待被中断", e));
        }

        persister.saveDiagnostics(LoginPageDriver.loginPage(session), runId, "manual-timeout");
        return LoginRoundResult.failure(FailCode.HUMAN_TIMEOUT,
                "等待人工验证超时（" + properties.getBrowser().getHumanWait().toMinutes() + " 分钟）",
                true);
    }

    /**
     * 单次提交后的登录结果（三态：成功 / 风控 / 失败或需人工）。
     *
     * <p>与 {@link LoginRoundResult} 的分工：本 record 是单轮内提交动作的结果，
     * {@code LoginRoundResult} 是完整一轮（含表单准备与人工等待）的轮级结果。</p>
     *
     * @param success      是否登录成功
     * @param riskControl  是否触发风控
     * @param failCode     失败原因，成功或需人工时为 null
     * @param message      描述
     * @param manualReason 需要人工介入的原因，仅三态中的“需人工”分支非空
     */
    private record AttemptResult(boolean success, boolean riskControl, FailCode failCode,
                                 String message, String manualReason) {

        static AttemptResult ok() {
            return new AttemptResult(true, false, null, "登录成功", null);
        }

        static AttemptResult failure(FailCode code, String message) {
            return new AttemptResult(false, false, code, message, null);
        }

        static AttemptResult manual(String reason) {
            return new AttemptResult(false, false, null, null, reason);
        }

        static AttemptResult riskControlResult() {
            return new AttemptResult(false, true, null, RISK_BLOCKED_MESSAGE, null);
        }
    }

    /**
     * 一轮登录的轮级结果。
     *
     * @param success           是否登录成功
     * @param riskControl       是否触发风控（外层据此决定是否重试下一轮）
     * @param humanIntervention 本轮是否经过人工验证
     * @param failCode          失败原因，成功时为 null
     * @param message           描述
     */
    private record LoginRoundResult(boolean success, boolean riskControl, boolean humanIntervention,
                                    FailCode failCode, String message) {

        static LoginRoundResult success(boolean humanIntervention) {
            return new LoginRoundResult(true, false, humanIntervention, null, "登录成功");
        }

        static LoginRoundResult riskControlResult() {
            return new LoginRoundResult(false, true, false, null, RISK_BLOCKED_MESSAGE);
        }

        static LoginRoundResult failure(FailCode code, String message,
                                        boolean humanIntervention) {
            return new LoginRoundResult(false, false, humanIntervention, code, message);
        }
    }
}
