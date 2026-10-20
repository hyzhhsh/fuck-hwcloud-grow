package com.hesihai.fuckhwcloudgrow.client.http;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hesihai.fuckhwcloudgrow.client.AbstractGrowthApi;
import com.hesihai.fuckhwcloudgrow.client.GrowthApiException;
import com.hesihai.fuckhwcloudgrow.client.HuaweiUrls;
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

import java.time.Duration;
import java.util.Map;

/**
 * 使用持久化 Cookie 直接调用华为云接口（HTTP 快速路径）。
 *
 * <p>只负责 RestClient 传输、请求头以及网络/5xx 重试；csrf、业务错误分类和响应解析
 * 由 {@link AbstractGrowthApi} 统一处理。</p>
 */
@Slf4j
public class HttpGrowthApi extends AbstractGrowthApi {

    private static final String DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    private final RestClient restClient;
    private final CookieJar cookieJar;
    private final String userAgent;
    private final int maxAttempts;
    private final Duration retryBackoff;

    public HttpGrowthApi(HwcloudGrowProperties properties, CookieJar cookieJar,
                         String userAgent, ObjectMapper objectMapper) {
        super(objectMapper, "HTTP");
        // 构造期即校验 Cookie：没有可用登录态时直接抛出，避免上层无谓执行一轮
        if (ObjectUtil.isNull(cookieJar) || cookieJar.isEmpty()) {
            throw GrowthApiException.sessionExpired("未找到有效的华为云 Cookie");
        }
        this.cookieJar = cookieJar;
        this.userAgent = StrUtil.isBlank(userAgent) ? DEFAULT_USER_AGENT : userAgent;
        this.maxAttempts = Math.max(1, properties.getApi().getMaxAttempts());
        this.retryBackoff = properties.getApi().getRetryBackoff();

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) properties.getApi().getConnectTimeout().toMillis());
        factory.setReadTimeout((int) properties.getApi().getRequestTimeout().toMillis());
        this.restClient = RestClient.builder()
                .baseUrl(properties.getApi().getBaseUrl())
                .requestFactory(factory)
                .build();
    }

    private static ResponseData toResponseData(int status, HttpHeaders headers, String body) {
        Map<String, String> headerMap = ObjectUtil.isNull(headers)
                ? Map.of()
                : headers.toSingleValueMap();
        return new ResponseData(status, body, headerMap);
    }

    /**
     * HTTP 传输层：网络异常与 5xx/429 做有限次退避重试。
     */
    @Override
    protected ResponseData executeRaw(HttpMethod method, String path, Object body,
                                      boolean withCsrf) {
        GrowthApiException lastNetworkError = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                ResponseData data = doExecute(method, path, body, withCsrf);
                boolean retryableStatus = data.status() >= 500 || data.status() == 429;
                if (retryableStatus && attempt < maxAttempts) {
                    SleepUtils.sleep(retryBackoff.multipliedBy(attempt),
                            ie -> new GrowthApiException(FailCode.NETWORK, "任务被中断", ie));
                    continue;
                }
                return data;
            } catch (ResourceAccessException e) {
                // 网络异常值得重试；其余 RestClientException 多为请求构造错误，重试无意义，直接抛
                lastNetworkError = GrowthApiException.network("请求华为云接口失败", e);
                if (attempt < maxAttempts) {
                    SleepUtils.sleep(retryBackoff.multipliedBy(attempt),
                            ie -> new GrowthApiException(FailCode.NETWORK, "任务被中断", ie));
                    continue;
                }
            } catch (RestClientException e) {
                throw GrowthApiException.network("请求华为云接口失败", e);
            }
        }
        throw ObjectUtil.isNotNull(lastNetworkError)
                ? lastNetworkError
                : GrowthApiException.network("请求华为云接口失败", null);
    }

    private ResponseData doExecute(HttpMethod method, String path, Object body,
                                   boolean withCsrf) {
        try {
            RestClient.RequestBodySpec spec = restClient.method(method)
                    .uri(path)
                    .headers(headers -> applyHeaders(headers, withCsrf, method == HttpMethod.POST));
            if (ObjectUtil.isNotNull(body)) {
                spec.contentType(MediaType.APPLICATION_JSON).body(body);
            }
            ResponseEntity<String> response = spec.retrieve().toEntity(String.class);
            log.debug("HTTP {} {} -> {}", method, path, response.getStatusCode().value());
            return toResponseData(response.getStatusCode().value(),
                    response.getHeaders(), response.getBody());
        } catch (RestClientResponseException e) {
            log.debug("HTTP {} {} -> {}", method, path, e.getStatusCode().value());
            return toResponseData(e.getStatusCode().value(),
                    e.getResponseHeaders(), e.getResponseBodyAsString());
        }
    }

    private void applyHeaders(HttpHeaders headers, boolean withCsrf, boolean isPost) {
        headers.set(HttpHeaders.COOKIE, cookieJar.header());
        headers.set(HttpHeaders.USER_AGENT, userAgent);
        headers.set(HttpHeaders.REFERER, HuaweiUrls.GROW_URL);
        headers.set(HttpHeaders.ACCEPT, "application/json, text/plain, */*");
        headers.set(HttpHeaders.ACCEPT_LANGUAGE, "zh-CN,zh;q=0.9");
        if (isPost) {
            headers.set(HttpHeaders.ORIGIN, HuaweiUrls.GROW_ORIGIN);
        }
        if (withCsrf) {
            headers.set("csrf", csrfToken());
        }
    }
}
