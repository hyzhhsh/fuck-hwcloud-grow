package com.hesihai.fuckhwcloudgrow.client.harmony;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hesihai.fuckhwcloudgrow.client.GrowthApiException;
import com.hesihai.fuckhwcloudgrow.client.harmony.dto.HarmonyCaseListResponse;
import com.hesihai.fuckhwcloudgrow.client.harmony.dto.HarmonyDailyTaskResponse;
import com.hesihai.fuckhwcloudgrow.client.harmony.dto.HarmonyHeartbeatResponse;
import com.hesihai.fuckhwcloudgrow.client.harmony.dto.HarmonySessionResponse;
import com.hesihai.fuckhwcloudgrow.client.http.CookieJar;
import com.hesihai.fuckhwcloudgrow.config.HwcloudGrowProperties;
import com.hesihai.fuckhwcloudgrow.model.FailCode;
import com.hesihai.fuckhwcloudgrow.util.SleepUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 鸿蒙开发实践中心的 HTTP 客户端。
 *
 * <p>该站点的响应不是成长中心的 {@code {code,data}} 信封，请求来源域也与成长中心不同，
 * 因此单独实现，不复用 {@code AbstractGrowthApi}。</p>
 */
@Slf4j
public class HarmonyClient {

    private final RestClient restClient;
    private final CookieJar cookieJar;
    private final String host;
    private final String origin;
    private final String userAgent;
    private final ObjectMapper objectMapper;
    private final int maxAttempts;
    private final Duration retryBackoff;

    public HarmonyClient(HwcloudGrowProperties properties, CookieJar cookieJar,
                         String userAgent, ObjectMapper objectMapper) {
        String baseUrl = StrUtil.strip(properties.getTasks().getHarmony().getBaseUrl(), "/");
        this.origin = StrUtil.blankToDefault(baseUrl, HarmonyUrls.HARMONY_ORIGIN);
        this.host = URI.create(this.origin).getHost();
        if (ObjectUtil.isNull(cookieJar) || cookieJar.isEmpty(host)) {
            throw GrowthApiException.sessionExpired("未找到可用于鸿蒙实践中心的华为云 Cookie");
        }
        this.cookieJar = cookieJar;
        this.userAgent = userAgent;
        this.objectMapper = objectMapper;
        this.maxAttempts = Math.max(1, properties.getApi().getMaxAttempts());
        this.retryBackoff = properties.getApi().getRetryBackoff();

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) properties.getApi().getConnectTimeout().toMillis());
        factory.setReadTimeout((int) properties.getApi().getRequestTimeout().toMillis());
        this.restClient = RestClient.builder()
                .baseUrl(this.origin)
                .requestFactory(factory)
                .build();
    }

    /**
     * 查询每日浏览案例任务配置与当日状态。
     */
    public HarmonyDailyTaskResponse getDailyTask() {
        Response response = executeWithRetry(HttpMethod.GET, HarmonyUrls.DAILY_CASE_TASK_PATH, null);
        throwIfError(response, "查询鸿蒙案例任务失败");
        return parse(response.body(), HarmonyDailyTaskResponse.class);
    }

    /**
     * 查询案例列表。
     */
    public HarmonyCaseListResponse listCases(int pageSize) {
        String path = HarmonyUrls.CASES_PATH + "?page=1&page_size=" + Math.max(1, pageSize);
        Response response = executeWithRetry(HttpMethod.GET, path, null);
        throwIfError(response, "查询鸿蒙案例列表失败");
        return parse(response.body(), HarmonyCaseListResponse.class);
    }

    /**
     * 开启一次浏览计时会话。
     *
     * @return 会话信息；409 表示本周期已完成，返回空
     */
    public Optional<HarmonySessionResponse> startSession(String caseId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("case_id", caseId);
        Response response = execute(HttpMethod.POST, HarmonyUrls.DAILY_CASE_SESSIONS_PATH, body);
        if (response.status() == 409) {
            return Optional.empty();
        }
        throwIfError(response, "开启鸿蒙案例浏览计时失败");
        return Optional.ofNullable(parse(response.body(), HarmonySessionResponse.class));
    }

    /**
     * 上报一次浏览心跳。
     *
     * @return 最新进度；404/409 表示会话已结束，返回空
     */
    public Optional<HarmonyHeartbeatResponse> heartbeat(String sessionId) {
        String path = String.format(HarmonyUrls.HEARTBEAT_PATH_TEMPLATE, sessionId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("session_id", sessionId);
        Response response = execute(HttpMethod.POST, path, body);
        if (response.status() == 404 || response.status() == 409) {
            return Optional.empty();
        }
        throwIfError(response, "上报鸿蒙案例浏览心跳失败");
        return Optional.ofNullable(parse(response.body(), HarmonyHeartbeatResponse.class));
    }

    private Response executeWithRetry(HttpMethod method, String path, Object body) {
        GrowthApiException lastNetworkError = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                Response response = execute(method, path, body);
                boolean retryable = response.status() >= 500 || response.status() == 429;
                if (retryable && attempt < maxAttempts) {
                    SleepUtils.sleep(retryBackoff.multipliedBy(attempt),
                            ie -> new GrowthApiException(FailCode.NETWORK, "任务被中断", ie));
                    continue;
                }
                return response;
            } catch (ResourceAccessException e) {
                lastNetworkError = GrowthApiException.network("请求鸿蒙实践中心接口失败", e);
                if (attempt < maxAttempts) {
                    SleepUtils.sleep(retryBackoff.multipliedBy(attempt),
                            ie -> new GrowthApiException(FailCode.NETWORK, "任务被中断", ie));
                    continue;
                }
            } catch (RestClientException e) {
                throw GrowthApiException.network("请求鸿蒙实践中心接口失败", e);
            }
        }
        throw ObjectUtil.isNotNull(lastNetworkError)
                ? lastNetworkError
                : GrowthApiException.network("请求鸿蒙实践中心接口失败", null);
    }

    private Response execute(HttpMethod method, String path, Object body) {
        try {
            RestClient.RequestBodySpec spec = restClient.method(method)
                    .uri(path)
                    .headers(headers -> applyHeaders(headers, method == HttpMethod.POST));
            if (ObjectUtil.isNotNull(body)) {
                spec.contentType(MediaType.APPLICATION_JSON).body(body);
            }
            ResponseEntity<String> response = spec.retrieve().toEntity(String.class);
            log.debug("鸿蒙 HTTP {} {} -> {}", method, path, response.getStatusCode().value());
            return new Response(response.getStatusCode().value(), response.getBody(),
                    response.getHeaders().getContentType() == null
                            ? null : response.getHeaders().getContentType().toString());
        } catch (RestClientResponseException e) {
            log.debug("鸿蒙 HTTP {} {} -> {}", method, path, e.getStatusCode().value());
            return new Response(e.getStatusCode().value(), e.getResponseBodyAsString(),
                    e.getResponseHeaders() == null || e.getResponseHeaders().getContentType() == null
                            ? null : e.getResponseHeaders().getContentType().toString());
        }
    }

    private void applyHeaders(HttpHeaders headers, boolean isPost) {
        headers.set(HttpHeaders.COOKIE, cookieJar.header(host));
        headers.set(HttpHeaders.USER_AGENT, userAgent);
        headers.set(HttpHeaders.REFERER, origin + "/");
        headers.set(HttpHeaders.ACCEPT, "application/json, text/plain, */*");
        headers.set(HttpHeaders.ACCEPT_LANGUAGE, "zh-CN,zh;q=0.9");
        if (isPost) {
            headers.set(HttpHeaders.ORIGIN, origin);
        }
    }

    /**
     * 鸿蒙站点未使用统一信封：登录态与风控需要按状态码与响应形态单独判定。
     */
    private void throwIfError(Response response, String action) {
        int status = response.status();
        String body = response.body();
        if (status >= 200 && status < 300) {
            if (isHtml(response.contentType(), body)) {
                throw GrowthApiException.wafBlocked(action + "：接口返回 HTML 页面", body);
            }
            return;
        }
        if (status == 401) {
            throw GrowthApiException.sessionExpired("鸿蒙实践中心登录态已失效");
        }
        if (status == 403 || status == 429) {
            throw GrowthApiException.wafBlocked(action + "：请求被拒绝（HTTP " + status + "）", body);
        }
        if (status >= 500) {
            throw new GrowthApiException(FailCode.NETWORK, action + "：服务端异常（HTTP " + status + "）",
                    null, true, body);
        }
        if (isHtml(response.contentType(), body)) {
            throw GrowthApiException.wafBlocked(action + "：接口返回 HTML 页面", body);
        }
        throw new GrowthApiException(FailCode.UNKNOWN,
                action + "：HTTP " + status, null, false, body);
    }

    private boolean isHtml(String contentType, String body) {
        return StrUtil.containsIgnoreCase(contentType, "text/html")
                || StrUtil.startWith(StrUtil.trim(body), "<");
    }

    private <T> T parse(String body, Class<T> type) {
        if (StrUtil.isBlank(body)) {
            throw GrowthApiException.wafBlocked("鸿蒙接口响应为空", body);
        }
        try {
            return objectMapper.readValue(body, type);
        } catch (Exception e) {
            throw GrowthApiException.wafBlocked("鸿蒙接口响应无法解析", body);
        }
    }

    private record Response(int status, String body, String contentType) {
    }
}