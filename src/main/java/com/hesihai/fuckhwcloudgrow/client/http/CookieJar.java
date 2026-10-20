package com.hesihai.fuckhwcloudgrow.client.http;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.hesihai.fuckhwcloudgrow.auth.StoredCookie;
import com.hesihai.fuckhwcloudgrow.client.HuaweiUrls;

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

    static boolean isRelevantDomain(String domain) {
        if (StrUtil.isBlank(domain)) {
            return false;
        }
        String normalized = domain.toLowerCase(Locale.ROOT);
        return normalized.equals(HuaweiUrls.DEVDATA_DOMAIN)
                || normalized.equals("." + HuaweiUrls.DEVDATA_DOMAIN)
                || normalized.equals("huaweicloud.com")
                || normalized.equals(".huaweicloud.com");
    }

    /**
     * 是否没有任何“可用” Cookie。
     *
     * <p>语义是过滤后的可用 Cookie 为空，而非原始列表为空：仅含无关域名或已过期
     * Cookie 时同样返回 true。调用方据此判定登录态失效并转入浏览器。</p>
     *
     * @return true 表示拼不出任何 Cookie 头
     */
    public boolean isEmpty() {
        return StrUtil.isBlank(header());
    }

    /**
     * 过滤无效名称、无关域名与已过期 Cookie，再按保存顺序拼接。
     */
    public String header() {
        long nowSeconds = System.currentTimeMillis() / 1000;
        List<String> values = new ArrayList<>();
        for (StoredCookie cookie : cookies) {
            if (StrUtil.isBlank(cookie.getName()) || ObjectUtil.isNull(cookie.getValue())) {
                continue;
            }
            if (!isRelevantDomain(cookie.getDomain())) {
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
