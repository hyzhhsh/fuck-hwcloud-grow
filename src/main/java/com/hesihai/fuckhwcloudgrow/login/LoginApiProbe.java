package com.hesihai.fuckhwcloudgrow.login;

import cn.hutool.core.util.ObjectUtil;
import com.hesihai.fuckhwcloudgrow.client.HuaweiUrls;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;

import java.util.Map;

/**
 * 登录页内的接口探测器。
 *
 * <p>直接复用页面的登录态调用华为云接口，比读取页面文案更可靠；
 * 探测失败返回 status=0，让调用方决定是否退回文案判断。</p>
 */
final class LoginApiProbe {

    private LoginApiProbe() {
    }

    /**
     * 页面内 fetch 个人资料；JS 异常或结构异常时返回 status=0。
     */
    static ProbeResult fetchPersonalInfo(Page page) {
        try {
            Object result = page.evaluate("""
                    async (args) => {
                      try {
                        const a = await fetch(args.base + args.csrfPath, {credentials: 'include'});
                        const csrf = a.headers.get('csrf');
                        const r = await fetch(args.base + args.personalInfoPath,
                          {credentials: 'include', headers: csrf ? {csrf} : {}});
                        return {status: r.status, body: await r.text()};
                      } catch (e) {
                        return {status: 0, body: ''};
                      }
                    }
                    """, Map.of(
                    "base", HuaweiUrls.API_BASE,
                    "csrfPath", HuaweiUrls.CSRF_PATH,
                    "personalInfoPath", HuaweiUrls.PERSONAL_INFO_PATH));
            if (result instanceof Map<?, ?> map) {
                int status = map.get("status") instanceof Number number ? number.intValue() : 0;
                String body = ObjectUtil.defaultIfNull(map.get("body"), "").toString();
                return new ProbeResult(status, body);
            }
        } catch (PlaywrightException ignored) {
            // 页面可能正在跳转
        }
        return new ProbeResult(0, "");
    }

    /**
     * 探测结果。
     *
     * @param status HTTP 状态码，0 表示探测失败（JS 异常或结构异常），调用方据此决定是否退回文案判断
     * @param body   响应体
     */
    record ProbeResult(int status, String body) {
    }
}
