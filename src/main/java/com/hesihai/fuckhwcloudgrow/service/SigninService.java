package com.hesihai.fuckhwcloudgrow.service;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hesihai.fuckhwcloudgrow.auth.AuthMeta;
import com.hesihai.fuckhwcloudgrow.auth.AuthStateStore;
import com.hesihai.fuckhwcloudgrow.auth.StoredCookie;
import com.hesihai.fuckhwcloudgrow.client.GrowthApiException;
import com.hesihai.fuckhwcloudgrow.client.browser.BrowserLaunchException;
import com.hesihai.fuckhwcloudgrow.client.browser.BrowserSession;
import com.hesihai.fuckhwcloudgrow.client.browser.PlaywrightGrowthApi;
import com.hesihai.fuckhwcloudgrow.client.dto.BeansInfoResponse;
import com.hesihai.fuckhwcloudgrow.client.dto.MemberInfoResponse;
import com.hesihai.fuckhwcloudgrow.client.dto.SignStatusResponse;
import com.hesihai.fuckhwcloudgrow.client.http.CookieJar;
import com.hesihai.fuckhwcloudgrow.client.http.HttpGrowthApi;
import com.hesihai.fuckhwcloudgrow.config.HwcloudGrowProperties;
import com.hesihai.fuckhwcloudgrow.login.BrowserLoginService;
import com.hesihai.fuckhwcloudgrow.login.LoginOutcome;
import com.hesihai.fuckhwcloudgrow.model.*;
import com.hesihai.fuckhwcloudgrow.notify.XizhiNotifier;
import com.hesihai.fuckhwcloudgrow.storage.SigninResultStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 签到任务入口：并发控制、HTTP 快速路径、浏览器降级、结果持久化与通知。
 */
@Slf4j
@Service
public class SigninService {

    private final HwcloudGrowProperties properties;
    private final AuthStateStore authStateStore;
    private final BrowserLoginService browserLoginService;
    private final SigninFlow signinFlow;
    private final SigninResultStore resultStore;
    private final XizhiNotifier notifier;
    private final ObjectMapper objectMapper;
    // 争用方只有定时任务与偶发手动请求，非公平锁吞吐更好且公平性无收益
    private final ReentrantLock lock = new ReentrantLock();

    public SigninService(HwcloudGrowProperties properties, AuthStateStore authStateStore,
                         BrowserLoginService browserLoginService, SigninFlow signinFlow,
                         SigninResultStore resultStore, XizhiNotifier notifier,
                         ObjectMapper objectMapper) {
        this.properties = properties;
        this.authStateStore = authStateStore;
        this.browserLoginService = browserLoginService;
        this.signinFlow = signinFlow;
        this.resultStore = resultStore;
        this.notifier = notifier;
        this.objectMapper = objectMapper;
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
     * 执行一次签到：HTTP 快速路径优先，失败时降级到浏览器。
     *
     * <p>同一时刻只允许一个任务运行；结果持久化到 last-result.json 并按状态推送通知。</p>
     *
     * @param trigger 触发来源
     * @return 签到结果
     * @throws SigninBusyException 已有签到任务正在执行
     */
    public SigninResult run(RunTrigger trigger) {
        if (!lock.tryLock()) {
            throw new SigninBusyException("签到任务正在执行中，请稍后重试");
        }
        try {
            String runId = IdUtil.randomUUID();
            Instant startedAt = Instant.now();
            SigninResult result;
            try {
                result = executeRun(runId, trigger, startedAt);
            } catch (Exception e) {
                log.error("签到任务异常：{}", e.getMessage(), e);
                result = SigninResultFactory.failure(runId, trigger, startedAt, FailCode.UNKNOWN,
                        "签到任务异常：" + e.getMessage(), "NONE", false);
            }
            resultStore.save(result);
            if (result.getStatus() == SigninStatus.FAILED) {
                notifier.failed(result);
            } else {
                notifier.dailyResult(result);
            }
            return result;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 读取最近一次签到结果，控制器不再直接依赖持久化实现。
     *
     * @return 最近一次结果，从未执行过时为 null
     */
    public SigninResult lastResult() {
        return resultStore.loadLast();
    }

    /**
     * 只读查询当前登录态与签到状态。
     *
     * <p>仅走 HTTP 快速路径，登录态无效时返回描述信息，不启动浏览器、不触发登录。</p>
     *
     * @return 实时状态
     */
    public LiveStatus queryStatus() {
        List<StoredCookie> cookies = authStateStore.loadCookiesFromFile();
        if (CollUtil.isEmpty(cookies)) {
            return LiveStatus.builder()
                    .sessionValid(false)
                    .message("未找到本地登录态，请先执行签到任务")
                    .build();
        }
        AuthMeta meta = authStateStore.loadMetaFromFile();
        try (HttpGrowthApi api = newHttpApi(cookies, meta)) {
            MemberInfoResponse member = api.getMemberInfo();
            SignStatusResponse status = api.getSignStatus();
            BeansInfoResponse beans = api.getBeans();
            return LiveStatus.builder()
                    .sessionValid(true)
                    .accountName(StrUtil.firstNonBlank(
                            ObjectUtil.isNotNull(member) ? member.getMemAlias() : null,
                            ObjectUtil.isNotNull(member) ? member.getMemName() : null))
                    .signFlag(ObjectUtil.isNotNull(status) ? status.getSignFlag() : null)
                    .signedToday(ObjectUtil.isNotNull(status)
                            && ObjectUtil.equal(status.getSignFlag(), 1))
                    .continuityCount(ObjectUtil.isNotNull(status)
                            ? status.getSignContinuityCount() : null)
                    .lastSignTime(ObjectUtil.isNotNull(status) ? status.getLastSignTime() : null)
                    .beans(ObjectUtil.isNotNull(beans) ? beans.getBeans() : null)
                    .message("查询成功")
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

    /**
     * HTTP 快速路径主流程：加载登录态、执行签到，异常时决定降级或记录失败。
     */
    private SigninResult executeRun(String runId, RunTrigger trigger, Instant startedAt) {
        List<StoredCookie> cookies = authStateStore.loadCookiesFromFile();
        if (CollUtil.isEmpty(cookies)) {
            return runWithBrowser(runId, trigger, startedAt, "未找到本地登录态");
        }

        AuthMeta meta = authStateStore.loadMetaFromFile();
        try (HttpGrowthApi api = newHttpApi(cookies, meta)) {
            return signinFlow.execute(api, runId, trigger, startedAt, false);
        } catch (GrowthApiException e) {
            // 只有登录态失效、WAF、网络错误才值得启动浏览器；业务失败直接记录
            if (e.shouldFallbackToBrowser()) {
                log.info("HTTP 快速路径失败（{}），转入浏览器：{}", e.getCode(), e.getMessage());
                // 先清除已失效的登录态，避免浏览器重新登录后又被旧状态覆盖、下次仍复用死状态
                if (e.getCode() == FailCode.SESSION_EXPIRED) {
                    authStateStore.invalidate();
                }
                return runWithBrowser(runId, trigger, startedAt, e.getMessage());
            }
            return SigninResultFactory.failure(runId, trigger, startedAt, e.getCode(),
                    apiFailureMessage(e), "HTTP", false);
        }
    }

    /**
     * 浏览器降级路径：启动浏览器、确保登录态、执行签到，所有异常就地转为失败结果。
     */
    private SigninResult runWithBrowser(String runId, RunTrigger trigger,
                                        Instant startedAt, String reason) {
        log.info("启动浏览器处理签到：{}", reason);
        try (BrowserSession session = BrowserSession.launch(properties)) {
            LoginOutcome login = browserLoginService.ensureLoggedIn(session, runId);
            if (!login.success()) {
                return SigninResultFactory.failure(runId, trigger, startedAt, login.failCode(),
                        login.message(), "PLAYWRIGHT", login.humanIntervention());
            }

            PlaywrightGrowthApi api = newPlaywrightApi(session);
            try {
                return signinFlow.execute(api, runId, trigger, startedAt,
                        login.humanIntervention());
            } catch (GrowthApiException e) {
                return SigninResultFactory.failure(runId, trigger, startedAt, e.getCode(),
                        apiFailureMessage(e), "PLAYWRIGHT", login.humanIntervention());
            }
        } catch (BrowserLaunchException e) {
            log.warn("浏览器启动失败：{}", e.getMessage());
            return SigninResultFactory.failure(runId, trigger, startedAt, FailCode.LOGIN_FAILED,
                    e.getMessage(), "PLAYWRIGHT", false);
        } catch (Exception e) {
            log.warn("浏览器签到失败：{}", e.getMessage());
            return SigninResultFactory.failure(runId, trigger, startedAt, FailCode.LOGIN_FAILED,
                    "浏览器签到失败：" + e.getMessage(), "PLAYWRIGHT", false);
        }
    }

    /**
     * HTTP 快速路径的实现构造集中在这里，避免在多处重复拼装 Cookie 与 User-Agent。
     */
    private HttpGrowthApi newHttpApi(List<StoredCookie> cookies, AuthMeta meta) {
        return new HttpGrowthApi(properties, new CookieJar(cookies),
                ObjectUtil.isNotNull(meta) ? meta.getUserAgent() : null, objectMapper);
    }

    private PlaywrightGrowthApi newPlaywrightApi(BrowserSession session) {
        return new PlaywrightGrowthApi(session, objectMapper);
    }
}
