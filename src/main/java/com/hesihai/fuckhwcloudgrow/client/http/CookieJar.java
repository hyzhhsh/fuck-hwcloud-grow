package com.hesihai.fuckhwcloudgrow.client.http;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.hesihai.fuckhwcloudgrow.auth.StoredCookie;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 将登录态 Cookie 转换为 Java HTTP 请求使用的 Cookie 头。
 */
public class CookieJar {

    private final List<StoredCookie> cookies;

    public CookieJar(List<StoredCookie> cookies) {
        this.cookies = List.copyOf(ObjectUtil.defaultIfNull(cookies, List.of()));
    }

    /**
     * 按 RFC6265 的 domain-match 规则判断 Cookie 是否可以发送给目标主机。
     *
     * <p>存储域带前导点时表示其子域也共享（例如 {@code .huaweicloud.com}），
     * 不带前导点时是 host-only Cookie，只能发给完全一致的主机。</p>
     */
    static boolean domainMatches(String host, String domain) {
        if (StrUtil.isBlank(host) || StrUtil.isBlank(domain)) {
            return false;
        }
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        String normalizedDomain = domain.toLowerCase(Locale.ROOT);
        boolean includeSubdomains = normalizedDomain.startsWith(".");
        String bareDomain = includeSubdomains ? normalizedDomain.substring(1) : normalizedDomain;
        if (StrUtil.isBlank(bareDomain)) {
            return false;
        }
        return normalizedHost.equals(bareDomain)
                || (includeSubdomains && normalizedHost.endsWith("." + bareDomain));
    }

    /**
     * 是否没有任何可以发送给目标主机的 Cookie。
     */
    public boolean isEmpty(String host) {
        return StrUtil.isBlank(header(host));
    }

    /**
     * 过滤无效名称、不匹配目标主机与已过期的 Cookie，再按保存顺序拼接。
     */
    public String header(String host) {
        long nowSeconds = System.currentTimeMillis() / 1000;
        List<String> values = new ArrayList<>();
        for (StoredCookie cookie : cookies) {
            if (StrUtil.isBlank(cookie.getName()) || ObjectUtil.isNull(cookie.getValue())) {
                continue;
            }
            if (!domainMatches(host, cookie.getDomain())) {
                continue;
            }
            Double expires = cookie.getExpires();
            // expires=-1 是 Playwright 会话 Cookie 的标记（浏览器关闭即失效），永不过滤；
            // 只有 0 <= expires <= now 的持久 Cookie 才算已过期
            if (ObjectUtil.isNotNull(expires) && expires >= 0 && expires <= nowSeconds) {
                continue;
            }
            values.add(cookie.getName() + "=" + cookie.getValue());
        }
        return CollUtil.join(values, "; ");
    }
}