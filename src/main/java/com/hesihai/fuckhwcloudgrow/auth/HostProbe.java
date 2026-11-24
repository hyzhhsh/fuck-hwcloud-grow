package com.hesihai.fuckhwcloudgrow.auth;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hesihai.fuckhwcloudgrow.client.HuaweiUrls;
import com.hesihai.fuckhwcloudgrow.client.HttpAccess;
import com.hesihai.fuckhwcloudgrow.client.harmony.HarmonyUrls;
import com.hesihai.fuckhwcloudgrow.config.HwcloudGrowProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * 单域登录态探测：只发只读 GET，不启动浏览器。
 */
@Slf4j
@Component
public class HostProbe {

    private final HwcloudGrowProperties properties;
    private final HttpAccess httpAccess;
    private final ObjectMapper objectMapper;

    public HostProbe(HwcloudGrowProperties properties, HttpAccess httpAccess, ObjectMapper objectMapper) {
        this.properties = properties;
        this.httpAccess = httpAccess;
        this.objectMapper = objectMapper;
    }

    public Result probe(String host) {
        try {
            Target target = resolveTarget(host);
            if (target == null) {
                return new Result(CredentialStatus.HostState.NOT_PROBED, "该域不需要探测");
            }
            String cookie = httpAccess.cookieJar().header(host);
            if (StrUtil.isBlank(cookie)) {
                return new Result(CredentialStatus.HostState.INVALID, "本地没有该域可用的 Cookie");
            }
            Response response = execute(target, cookie);
            return classify(host, response);
        } catch (Exception e) {
            log.warn("探测 {} 登录态失败：{}", host, e.getMessage());
            return new Result(CredentialStatus.HostState.NETWORK_ERROR, "探测失败：" + e.getMessage());
        }
    }

    /**
     * 成长中心接口在独立子域，探测必须打到 {@link HuaweiUrls#API_BASE}；
     * Cookie 仍按请求域名过滤。
     */
    private Target resolveTarget(String host) {
        if (HuaweiUrls.API_HOST.equalsIgnoreCase(host)) {
            return new Target(HuaweiUrls.API_BASE, HuaweiUrls.USER_INFO_PATH, HuaweiUrls.GROW_ORIGIN);
        }
        if (HuaweiUrls.GROW_HOST.equalsIgnoreCase(host)) {
            return new Target(HuaweiUrls.API_BASE, HuaweiUrls.USER_INFO_PATH, HuaweiUrls.GROW_ORIGIN);
        }
        if (HarmonyUrls.HARMONY_HOST.equalsIgnoreCase(host)) {
            return new Target(HarmonyUrls.HARMONY_ORIGIN, HarmonyUrls.DAILY_CASE_TASK_PATH,
                    HarmonyUrls.HARMONY_ORIGIN);
        }
        return null;
    }

    private Response execute(Target target, String cookie) {
        int timeout = (int) properties.getTasks().getCredential().getProbeTimeout().toMillis();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        RestClient client = RestClient.builder()
                .baseUrl(target.origin())
                .requestFactory(factory)
                .build();
        try {
            ResponseEntity<String> response = client.get().uri(target.path())
                    .headers(headers -> {
                        headers.set(HttpHeaders.COOKIE, cookie);
                        headers.set(HttpHeaders.USER_AGENT, StrUtil.blankToDefault(
                                httpAccess.userAgent(), "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"));
                        headers.set(HttpHeaders.REFERER, target.referer() + "/");
                        headers.set(HttpHeaders.ACCEPT, "application/json, text/plain, */*");
                    })
                    .retrieve().toEntity(String.class);
            return new Response(response.getStatusCode().value(), response.getBody());
        } catch (RestClientResponseException e) {
            return new Response(e.getStatusCode().value(), e.getResponseBodyAsString());
        }
    }

    private Result classify(String host, Response response) {
        int status = response.status();
        String body = response.body();
        if (status == 401) {
            return new Result(CredentialStatus.HostState.INVALID, "登录态已失效（HTTP 401）");
        }
        if (status == 403 || status == 429) {
            return new Result(CredentialStatus.HostState.WAF_BLOCKED, "请求被拒绝（HTTP " + status + "）");
        }
        if (status >= 500) {
            return new Result(CredentialStatus.HostState.NETWORK_ERROR, "服务端异常（HTTP " + status + "）");
        }
        if (StrUtil.isBlank(body) || StrUtil.startWith(StrUtil.trim(body), "<")) {
            return new Result(CredentialStatus.HostState.WAF_BLOCKED, "接口返回 HTML 页面");
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (Exception e) {
            return new Result(CredentialStatus.HostState.WAF_BLOCKED, "接口响应不是合法 JSON");
        }
        if (status >= 400) {
            return new Result(CredentialStatus.HostState.INVALID, "接口返回 HTTP " + status);
        }
        if (HuaweiUrls.GROW_HOST.equalsIgnoreCase(host)
                || HuaweiUrls.API_HOST.equalsIgnoreCase(host)) {
            int code = root.path("code").asInt(-1);
            JsonNode data = root.get("data");
            if (code != 0 || ObjectUtil.isNull(data) || data.isNull()) {
                return new Result(CredentialStatus.HostState.INVALID, "成长中心未返回用户信息（code=" + code + "）");
            }
            return new Result(CredentialStatus.HostState.VALID, "成长中心登录态有效");
        }
        boolean loggedIn = root.path("logged_in").asBoolean(false);
        if (!loggedIn) {
            return new Result(CredentialStatus.HostState.INVALID, "鸿蒙实践中心未登录");
        }
        return new Result(CredentialStatus.HostState.VALID, "鸿蒙实践中心登录态有效");
    }

    public record Result(CredentialStatus.HostState state, String message) {
    }

    private record Target(String origin, String path, String referer) {
    }

    private record Response(int status, String body) {
    }
}