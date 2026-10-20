package com.hesihai.fuckhwcloudgrow.client.browser;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hesihai.fuckhwcloudgrow.client.AbstractGrowthApi;
import com.hesihai.fuckhwcloudgrow.client.GrowthApiException;
import com.hesihai.fuckhwcloudgrow.client.HuaweiUrls;
import com.microsoft.playwright.APIRequestContext;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.RequestOptions;
import com.microsoft.playwright.options.WaitUntilState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 在已登录的 Playwright 页面内执行 fetch，作为 HTTP 被 WAF 拦截时的降级实现。
 *
 * <p>与 {@link com.hesihai.fuckhwcloudgrow.client.http.HttpGrowthApi} 共享
 * {@link AbstractGrowthApi} 的业务逻辑，这里只负责页面内请求，
 * 并在 evaluate 失败时退回 context.request。</p>
 */
@Slf4j
public class PlaywrightGrowthApi extends AbstractGrowthApi {

    private final BrowserSession session;

    public PlaywrightGrowthApi(BrowserSession session, ObjectMapper objectMapper) {
        super(objectMapper, "PLAYWRIGHT");
        this.session = session;
    }

    /**
     * 把页面内 JS 返回的 Map 转换为统一的 {@link ResponseData} 结构。
     */
    private static ResponseData toResponse(Object result) {
        if (!(result instanceof Map<?, ?> map)) {
            throw GrowthApiException.wafBlocked("页面内 fetch 返回结构异常", String.valueOf(result));
        }
        int status = map.get("status") instanceof Number number ? number.intValue() : 0;
        String contentType = ObjectUtil.defaultIfNull(map.get("contentType"), "").toString();
        String csrfHeader = ObjectUtil.isNull(map.get("csrf")) ? null : map.get("csrf").toString();
        String body = ObjectUtil.defaultIfNull(map.get("body"), "").toString();
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("content-type", contentType);
        if (StrUtil.isNotBlank(csrfHeader)) {
            headers.put("csrf", csrfHeader);
        }
        return new ResponseData(status, body, headers);
    }

    /**
     * 页面内 fetch；Playwright 的 evaluate 失败时退回到 context.request。
     */
    @Override
    protected ResponseData executeRaw(HttpMethod method, String path, Object body,
                                      boolean withCsrf) {
        Page page = ensureGrowPage();
        try {
            Map<String, Object> args = new LinkedHashMap<>();
            args.put("url", HuaweiUrls.API_BASE + path);
            args.put("method", method.name());
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("Accept", "application/json, text/plain, */*");
            if (ObjectUtil.isNotNull(body)) {
                headers.put("Content-Type", "application/json");
            }
            if (withCsrf) {
                headers.put("csrf", csrfToken());
            }
            args.put("headers", headers);
            args.put("body", body);

            Object result = page.evaluate("""
                    async (args) => {
                      const resp = await fetch(args.url, {
                        method: args.method,
                        credentials: 'include',
                        headers: args.headers,
                        body: args.body == null ? undefined : JSON.stringify(args.body)
                      });
                      return {
                        status: resp.status,
                        contentType: resp.headers.get('content-type') || '',
                        csrf: resp.headers.get('csrf'),
                        body: await resp.text()
                      };
                    }
                    """, args);
            ResponseData data = toResponse(result);
            log.debug("页面内 fetch {} {} -> {}", method, path, data.status());
            return data;
        } catch (PlaywrightException e) {
            log.warn("页面内 fetch 失败，改用 context.request：{} {}，原因：{}",
                    method, path, e.getMessage());
            return executeWithApiRequest(page, method, path, body, withCsrf);
        }
    }

    /**
     * 页面内 fetch 没拿到 csrf 时，用 context.request 再取一次。
     */
    @Override
    protected String csrfTokenFrom(ResponseData data) {
        String token = super.csrfTokenFrom(data);
        if (StrUtil.isNotBlank(token)) {
            return token;
        }
        ResponseData fallback = executeWithApiRequest(ensureGrowPage(),
                HttpMethod.GET, HuaweiUrls.CSRF_PATH, null, false);
        return super.csrfTokenFrom(fallback);
    }

    /**
     * 页面不在成长中心时先导航过去；登录态失效则抛给上层触发重新登录。
     */
    private Page ensureGrowPage() {
        Page page = session.activePage();
        if (ObjectUtil.isNull(page) || page.isClosed()) {
            throw GrowthApiException.sessionExpired("浏览器页面已关闭");
        }
        String url = page.url();
        if (StrUtil.isBlank(url) || !url.contains(HuaweiUrls.DEVELOPER_DOMAIN)) {
            try {
                page.navigate(HuaweiUrls.GROW_URL,
                        new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
                try {
                    page.waitForLoadState(LoadState.NETWORKIDLE);
                } catch (PlaywrightException ignored) {
                    // 页面可能仍有长连接，不阻塞后续 fetch
                }
            } catch (PlaywrightException e) {
                throw GrowthApiException.network("打开成长中心页面失败", e);
            }
        }
        if (StrUtil.isNotBlank(page.url()) && page.url().contains(HuaweiUrls.AUTH_DOMAIN)) {
            throw GrowthApiException.sessionExpired("浏览器登录态已失效");
        }
        log.debug("Playwright 当前页面：{}", page.url());
        return page;
    }

    /**
     * 页面内 fetch 失败时的兜底通道：用 context.request 直接请求，同样携带登录态。
     */
    private ResponseData executeWithApiRequest(Page page, HttpMethod method, String path,
                                               Object body, boolean withCsrf) {
        try {
            RequestOptions options = RequestOptions.create().setMethod(method.name());
            options.setHeader("Accept", "application/json, text/plain, */*");
            if (ObjectUtil.isNotNull(body)) {
                options.setData(body);
            }
            if (withCsrf) {
                options.setHeader("csrf", csrfToken());
            }
            APIRequestContext request = session.context().request();
            APIResponse response = request.fetch(HuaweiUrls.API_BASE + path, options);
            log.debug("context.request {} {} -> {}", method, path, response.status());
            return new ResponseData(response.status(), response.text(), response.headers());
        } catch (PlaywrightException e) {
            throw GrowthApiException.network("浏览器请求华为云接口失败", e);
        }
    }
}
