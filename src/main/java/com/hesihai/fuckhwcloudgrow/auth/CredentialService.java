package com.hesihai.fuckhwcloudgrow.auth;

import cn.hutool.core.util.StrUtil;
import com.hesihai.fuckhwcloudgrow.client.HuaweiUrls;
import com.hesihai.fuckhwcloudgrow.client.browser.BrowserLaunchException;
import com.hesihai.fuckhwcloudgrow.client.browser.BrowserSession;
import com.hesihai.fuckhwcloudgrow.client.harmony.HarmonyUrls;
import com.hesihai.fuckhwcloudgrow.login.BrowserLoginService;
import com.hesihai.fuckhwcloudgrow.login.LoginOutcome;
import com.hesihai.fuckhwcloudgrow.service.task.BrowserProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 凭据层：先用只读接口探测登录态，失效时才启动一次浏览器重新登录并预热各域。
 */
@Slf4j
@Service
public class CredentialService {

    private final HostProbe hostProbe;
    private final BrowserProvider browserProvider;
    private final BrowserLoginService browserLoginService;

    public CredentialService(HostProbe hostProbe, BrowserProvider browserProvider,
                             BrowserLoginService browserLoginService) {
        this.hostProbe = hostProbe;
        this.browserProvider = browserProvider;
        this.browserLoginService = browserLoginService;
    }

    /**
     * 只读探测，不启动浏览器。
     */
    public CredentialStatus probe(Collection<String> hosts) {
        return summarize(probeHosts(hosts), false, false);
    }

    /**
     * 探测登录态；存在失效域时启动一次浏览器登录、预热并保存，然后再探测一次。
     *
     * @param hosts 本次任务依赖的域
     * @param runId 批次 ID，用于诊断与通知
     */
    public CredentialStatus ensureValid(Collection<String> hosts, String runId) {
        Map<String, CredentialStatus.HostState> before = probeHosts(hosts);
        if (allValid(before)) {
            return CredentialStatus.valid(before);
        }

        boolean humanIntervention = false;
        log.info("检测到登录态失效，启动浏览器重新登录：{}", describe(before));
        try (BrowserSession session = browserProvider.open()) {
            LoginOutcome outcome = browserLoginService.ensureLoggedIn(session, runId);
            humanIntervention = outcome.humanIntervention();
            if (!outcome.success()) {
                return summarize(before, true, humanIntervention,
                        "登录失败：" + StrUtil.blankToDefault(outcome.message(), "未知原因"));
            }
            browserLoginService.saveStateAfterWarmup(session, runId, warmupUrls(hosts));
        } catch (BrowserLaunchException e) {
            log.warn("浏览器启动失败：{}", e.getMessage());
            return summarize(before, true, false, "浏览器启动失败：" + e.getMessage());
        } catch (Exception e) {
            log.warn("浏览器登录异常：{}", e.getMessage());
            return summarize(before, true, humanIntervention, "浏览器登录异常：" + e.getMessage());
        }

        Map<String, CredentialStatus.HostState> after = probeHosts(hosts);
        CredentialStatus status = summarize(after, true, humanIntervention, null);
        log.info("重新登录后凭据状态：{}，{}", status.getLevel(), describe(after));
        return status;
    }

    private Map<String, CredentialStatus.HostState> probeHosts(Collection<String> hosts) {
        Map<String, CredentialStatus.HostState> states = new LinkedHashMap<>();
        if (hosts == null) {
            return states;
        }
        for (String host : hosts) {
            HostProbe.Result result = hostProbe.probe(host);
            states.put(host, result.state());
            log.info("凭据探测 {} -> {}（{}）", host, result.state(), result.message());
        }
        return states;
    }

    private List<String> warmupUrls(Collection<String> hosts) {
        List<String> urls = new ArrayList<>();
        if (hosts == null) {
            return urls;
        }
        for (String host : hosts) {
            if (HuaweiUrls.GROW_HOST.equalsIgnoreCase(host)
                    || HuaweiUrls.API_HOST.equalsIgnoreCase(host)) {
                urls.add(HuaweiUrls.GROW_URL);
            } else if (HarmonyUrls.HARMONY_HOST.equalsIgnoreCase(host)) {
                urls.add(HarmonyUrls.HARMONY_ORIGIN + "/");
            }
        }
        return urls;
    }

    private CredentialStatus summarize(Map<String, CredentialStatus.HostState> states,
                                       boolean loginAttempted, boolean humanIntervention) {
        return summarize(states, loginAttempted, humanIntervention, null);
    }

    private CredentialStatus summarize(Map<String, CredentialStatus.HostState> states,
                                       boolean loginAttempted, boolean humanIntervention,
                                       String messageOverride) {
        CredentialStatus.Level level;
        if (states.isEmpty() || allValid(states)) {
            level = CredentialStatus.Level.VALID;
        } else if (states.containsValue(CredentialStatus.HostState.WAF_BLOCKED)) {
            level = CredentialStatus.Level.WAF_BLOCKED;
        } else {
            level = CredentialStatus.Level.INVALID;
        }
        String message = messageOverride;
        if (StrUtil.isBlank(message)) {
            message = switch (level) {
                case VALID -> "登录态有效";
                case INVALID -> "登录态失效：" + describe(states);
                case WAF_BLOCKED -> "接口被风控拦截：" + describe(states);
            };
        }
        return CredentialStatus.builder()
                .level(level)
                .hosts(states)
                .message(message)
                .loginAttempted(loginAttempted)
                .humanIntervention(humanIntervention)
                .build();
    }

    private boolean allValid(Map<String, CredentialStatus.HostState> states) {
        return states.values().stream()
                .allMatch(state -> state == CredentialStatus.HostState.VALID
                        || state == CredentialStatus.HostState.NOT_PROBED);
    }

    private String describe(Map<String, CredentialStatus.HostState> states) {
        List<String> parts = new ArrayList<>();
        states.forEach((host, state) -> parts.add(host + "=" + state));
        return String.join(", ", parts);
    }
}