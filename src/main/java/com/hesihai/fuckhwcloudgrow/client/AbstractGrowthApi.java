package com.hesihai.fuckhwcloudgrow.client;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hesihai.fuckhwcloudgrow.client.dto.BeansInfoResponse;
import com.hesihai.fuckhwcloudgrow.client.dto.MemberInfoResponse;
import com.hesihai.fuckhwcloudgrow.client.dto.SignStatusResponse;
import com.hesihai.fuckhwcloudgrow.client.dto.SignSubmitOutcome;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;

import java.util.Map;

/**
 * GrowthApi 的模板实现：统一业务方法、csrf 获取/失效重试、错误分类与 JSON 解析。
 *
 * <p>HTTP 与 Playwright 两个实现只提供底层传输能力（{@link #executeRaw}）以及 csrf 的兜底方式，
 * 这样签到业务逻辑只维护一份，避免两套实现行为漂移。</p>
 */
@Slf4j
public abstract class AbstractGrowthApi implements GrowthApi {

    private final ObjectMapper objectMapper;
    private final String source;
    private String csrf;

    protected AbstractGrowthApi(ObjectMapper objectMapper, String source) {
        this.objectMapper = objectMapper;
        this.source = source;
    }

    @Override
    public MemberInfoResponse getMemberInfo() {
        ResponseData data = executeWithCsrf(HttpMethod.GET, HuaweiUrls.PERSONAL_INFO_PATH, null);
        ResponseClassifier.throwIfError(data.status(), data.contentType(), data.body());
        return readJson(data.body(), MemberInfoResponse.class);
    }

    @Override
    public SignStatusResponse getSignStatus() {
        ResponseData data = executeWithCsrf(HttpMethod.GET, HuaweiUrls.SIGN_PATH, null);
        ResponseClassifier.throwIfError(data.status(), data.contentType(), data.body());
        return readJson(data.body(), SignStatusResponse.class);
    }

    @Override
    public BeansInfoResponse getBeans() {
        ResponseData data = executeWithCsrf(HttpMethod.GET, HuaweiUrls.BEANS_PATH, null);
        ResponseClassifier.throwIfError(data.status(), data.contentType(), data.body());
        return readJson(data.body(), BeansInfoResponse.class);
    }

    @Override
    public SignSubmitOutcome submitSign(String accountName) {
        Map<String, Object> body = Map.of("account_name", accountName);
        ResponseData data = executeWithCsrf(HttpMethod.POST, HuaweiUrls.SIGN_PATH, body);
        String code = ResponseClassifier.extractErrorCode(data.body());
        // 厂商把“今天已签到”作为业务错误码返回，这里转换成正常的已签到结果
        if (HuaweiErrorCodes.ALREADY_SIGNED.equals(code)) {
            return SignSubmitOutcome.builder()
                    .success(true)
                    .alreadySigned(true)
                    .vendorCode(code)
                    .rawBody(data.body())
                    .build();
        }
        ResponseClassifier.throwIfError(data.status(), data.contentType(), data.body());
        return SignSubmitOutcome.builder()
                .success(true)
                .alreadySigned(false)
                .vendorCode(code)
                .vendorMessage(ResponseClassifier.extractErrorMessage(data.body()))
                .rawBody(data.body())
                .build();
    }

    @Override
    public String source() {
        return source;
    }

    /**
     * 发送需要 csrf 的请求；csrf 失效时清空缓存并重试一次。
     */
    protected ResponseData executeWithCsrf(HttpMethod method, String path, Object body) {
        ResponseData data = executeRaw(method, path, body, true);
        if (isCsrfFailure(data)) {
            log.debug("csrf 校验失败，刷新后重试 {} {}", method, path);
            csrf = null;
            getCsrf();
            // 只重试一次：第二次仍失效说明不是 token 过期问题，交给错误分类处理，避免死循环
            data = executeRaw(method, path, body, true);
        }
        return data;
    }

    /**
     * 获取并缓存 csrf token；401/403 或未登录响应会转换成登录态失效。
     */
    protected String getCsrf() {
        if (StrUtil.isNotBlank(csrf)) {
            return csrf;
        }
        ResponseData data = executeRaw(HttpMethod.GET, HuaweiUrls.CSRF_PATH, null, false);
        // 401/403 或未登录文案说明登录态已失效，此时不必再解析 token，直接抛给上层降级
        if (data.status() == 401 || data.status() == 403
                || ResponseClassifier.isNotLoginBody(data.body())) {
            throw GrowthApiException.sessionExpired("获取 csrf 时发现华为云登录态已失效");
        }
        ResponseClassifier.throwIfError(data.status(), data.contentType(), data.body());
        String token = csrfTokenFrom(data);
        if (StrUtil.isBlank(token)) {
            throw GrowthApiException.sessionExpired("未获取到 csrf，华为云登录态可能已失效");
        }
        csrf = token;
        return csrf;
    }

    /**
     * 子类可覆盖：例如页面内 fetch 拿不到 csrf 时，改用 context.request 再试一次。
     */
    protected String csrfTokenFrom(ResponseData data) {
        if (StrUtil.isNotBlank(data.csrf())) {
            return data.csrf();
        }
        return data.header("csrf");
    }

    /**
     * 供子类在请求头中复用当前 csrf。
     */
    protected String csrfToken() {
        return getCsrf();
    }

    /**
     * csrf 失效的特征：响应体提到 csrf，但没有业务错误码，也不是未登录。
     */
    protected boolean isCsrfFailure(ResponseData data) {
        return ResponseClassifier.extractErrorCode(data.body()) == null
                && !ResponseClassifier.isNotLoginBody(data.body())
                && StrUtil.containsIgnoreCase(data.body(), "csrf");
    }

    /**
     * 统一空响应与非 JSON 响应的分类，两者都按 WAF 拦截处理。
     */
    protected <T> T readJson(String body, Class<T> type) {
        if (StrUtil.isBlank(body)) {
            throw GrowthApiException.wafBlocked("接口响应为空", body);
        }
        try {
            return objectMapper.readValue(body, type);
        } catch (JsonProcessingException e) {
            throw GrowthApiException.wafBlocked("接口响应不是合法 JSON，可能被 WAF 拦截", body);
        }
    }

    /**
     * 子类实现真正的传输并返回统一响应；不需要处理 csrf 重试。
     */
    protected abstract ResponseData executeRaw(HttpMethod method, String path,
                                               Object body, boolean withCsrf);

    /**
     * 统一 HTTP 与页面 fetch 的响应载体，屏蔽两套 header 结构。
     */
    protected static final class ResponseData {

        private final int status;
        private final String body;
        private final Map<String, String> headers;

        public ResponseData(int status, String body, Map<String, String> headers) {
            this.status = status;
            this.body = body;
            this.headers = ObjectUtil.defaultIfNull(headers, Map.of());
        }

        public int status() {
            return status;
        }

        public String body() {
            return body;
        }

        public String contentType() {
            return header("content-type");
        }

        public String csrf() {
            return header("csrf");
        }

        /**
         * 大小写不敏感地读取响应头。
         */
        public String header(String name) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(name)) {
                    return entry.getValue();
                }
            }
            return null;
        }
    }
}
