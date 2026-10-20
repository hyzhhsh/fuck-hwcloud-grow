package com.hesihai.fuckhwcloudgrow.login;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.hesihai.fuckhwcloudgrow.client.HuaweiUrls;
import com.hesihai.fuckhwcloudgrow.client.browser.BrowserSession;
import com.hesihai.fuckhwcloudgrow.config.HwcloudGrowProperties;
import com.hesihai.fuckhwcloudgrow.util.SleepUtils;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * 登录状态探测：是否已登录、是否遇到风控、是否需要验证码/MFA、是否出现错误提示。
 *
 * <p>只做判断，不执行点击/填写；探测顺序保持“接口优先、页面文案兜底”，
 * 避免旧页面残留文案造成误判。</p>
 */
@Slf4j
final class LoginDetector {

    private final HwcloudGrowProperties properties;

    LoginDetector(HwcloudGrowProperties properties) {
        this.properties = properties;
    }

    boolean isLoggedIn(BrowserSession session) {
        for (Page page : session.context().pages()) {
            if (page.isClosed()) {
                continue;
            }
            String url = page.url();
            if (StrUtil.isBlank(url) || !url.contains(HuaweiUrls.DEVELOPER_DOMAIN)) {
                continue;
            }
            if (isLoggedIn(page)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 在超时时间内等待登录完成；出现明确的未登录文案时提前返回。
     */
    boolean waitForLoggedIn(BrowserSession session, Duration timeout) {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            if (isLoggedIn(session)) {
                return true;
            }
            Page page = session.activePage();
            if (ObjectUtil.isNotNull(page)
                    && LoginPageDriver.findVisibleText(page, LoginSelectors.LOGGED_OUT_TEXTS) != null) {
                return false;
            }
            SleepUtils.sleep(properties.getBrowser().getPollInterval(),
                    e -> new IllegalStateException("登录等待被中断", e));
        }
        return false;
    }

    boolean loginFormVisible(Page page) {
        return LoginPageDriver.findFirstVisible(page, LoginSelectors.PASSWORD_TAB_SELECTORS) != null
                || LoginPageDriver.findFirstVisible(page, LoginSelectors.PERSONAL_ACCOUNT_INPUTS) != null
                || LoginPageDriver.findFirstVisible(page, LoginSelectors.HUAWEI_CLOUD_ACCOUNT_SELECTORS) != null;
    }

    /**
     * 判断单个页面是否处于登录态：接口探测优先，页面文案兜底。
     *
     * @param page 页面，null 或已关闭返回 false
     * @return true 表示已登录
     */
    boolean isLoggedIn(Page page) {
        if (ObjectUtil.isNull(page) || page.isClosed()) {
            return false;
        }
        String url = page.url();
        if (StrUtil.isBlank(url) || url.contains(HuaweiUrls.AUTH_DOMAIN)) {
            return false;
        }
        // API 结果优先，避免旧页面文案造成误判/漏判
        Boolean apiResult = isLoggedInByApi(page);
        if (ObjectUtil.isNotNull(apiResult)) {
            return apiResult;
        }
        if (LoginPageDriver.findVisibleText(page, LoginSelectors.LOGGED_OUT_TEXTS) != null) {
            return false;
        }
        return LoginPageDriver.findVisibleText(page, LoginSelectors.LOGGED_IN_TEXTS) != null;
    }

    /**
     * 判断是否风控拦截页：URL 特征（errorcode=408 / casloginerror）与页面文案双通道判定。
     *
     * @param page 页面，null 或已关闭返回 false
     * @return true 表示遇到风控拦截
     */
    boolean isRiskControlPage(Page page) {
        if (ObjectUtil.isNull(page) || page.isClosed()) {
            return false;
        }
        String url = page.url();
        if (StrUtil.isNotBlank(url)) {
            String normalized = url.toLowerCase(Locale.ROOT);
            // errorcode=408 与 casloginerror 是华为云风控的 URL 特征，与页面文案双通道判定互补
            if (normalized.contains("errorcode=408") || normalized.contains("casloginerror")) {
                return true;
            }
        }
        return LoginPageDriver.findVisibleText(page,
                List.of("检测到您的设备不安全", "请更换设备登录")) != null;
    }

    /**
     * 查找账号密码错误、账号锁定等提示；找到后优先用于失败原因。
     */
    String findError(Page page) {
        String errorText = LoginPageDriver.findVisibleText(page, LoginSelectors.ERROR_TEXTS);
        if (ObjectUtil.isNotNull(errorText)) {
            return errorText;
        }
        for (String selector : LoginSelectors.ERROR_SELECTORS) {
            Locator locator = LoginPageDriver.findFirstVisible(page, List.of(selector));
            if (ObjectUtil.isNotNull(locator)) {
                try {
                    String content = locator.innerText().trim();
                    if (StrUtil.isNotEmpty(content)) {
                        return content;
                    }
                } catch (PlaywrightException ignored) {
                    // 元素可能已消失
                }
            }
        }
        return null;
    }

    /**
     * 识别验证码、两步验证、MFA 等需要人工介入的页面。
     */
    String detectChallenge(Page page) {
        if (LoginPageDriver.findFirstVisible(page, LoginSelectors.CHALLENGE_SELECTORS) != null) {
            return "检测到验证码或身份验证页面";
        }
        String challengeText = LoginPageDriver.findVisibleText(page, LoginSelectors.CHALLENGE_TEXTS);
        if (ObjectUtil.isNotNull(challengeText)) {
            return "检测到" + challengeText + "页面";
        }
        String url = page.url();
        if (StrUtil.isNotBlank(url)) {
            String normalized = url.toLowerCase(Locale.ROOT);
            if (normalized.contains("verify") || normalized.contains("mfa")
                    || normalized.contains("authentication")) {
                return "登录跳转到了身份验证页面";
            }
        }
        return null;
    }

    /**
     * 用个人资料接口判断登录态；status=0 表示探测失败，交由页面文案兜底。
     */
    private Boolean isLoggedInByApi(Page page) {
        LoginApiProbe.ProbeResult probe = LoginApiProbe.fetchPersonalInfo(page);
        if (probe.status() == 200) {
            return probe.body().contains("\"memAlias\":\"");
        }
        if (probe.status() == 401 || probe.status() == 403 || probe.status() == 404) {
            return false;
        }
        return null;
    }
}
