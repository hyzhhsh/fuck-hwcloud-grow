package com.hesihai.fuckhwcloudgrow.login;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.hesihai.fuckhwcloudgrow.client.HuaweiUrls;
import com.hesihai.fuckhwcloudgrow.client.browser.BrowserSession;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.BoundingBox;
import com.microsoft.playwright.options.WaitUntilState;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 登录页 DOM 操作助手：只负责“怎么找、怎么填、怎么点、怎么等”。
 *
 * <p>把易变的页面交互集中在这里，登录流程只表达业务步骤；
 * 页面改版时优先调整本类和 {@link LoginSelectors}。</p>
 */
@Slf4j
final class LoginPageDriver {

    /**
     * navigate 后留给页面渲染的缓冲时间。
     */
    private static final int NAVIGATE_RENDER_WAIT_MS = 2000;
    /**
     * 切换登录表单 tab/入口后等待新表单渲染。
     */
    private static final int FORM_SWITCH_WAIT_MS = 1000;
    /**
     * “更多账号登录”入口展开后的等待。
     */
    private static final int MORE_ACCOUNT_WAIT_MS = 500;
    /**
     * 点击华为云账号入口后等待表单出现。
     */
    private static final int ACCOUNT_FORM_WAIT_MS = 1500;
    /**
     * 登录按钮 disabled 状态轮询次数上限（约 4.5s，平衡表单校验耗时与登录总超时）。
     */
    private static final int LOGIN_BUTTON_POLL_ROUNDS = 15;
    /**
     * 登录按钮 disabled 状态轮询间隔。
     */
    private static final int LOGIN_BUTTON_POLL_INTERVAL_MS = 300;
    /**
     * 逐字符输入时每个按键的随机延迟下限/上限（ms），模拟人工输入节奏。
     */
    private static final int TYPE_DELAY_MIN_MS = 60;
    private static final int TYPE_DELAY_MAX_MS = 140;
    /**
     * 鼠标移动插值步数范围，以及点击前的停顿范围（ms）。
     */
    private static final int MOUSE_STEPS_MIN = 8;
    private static final int MOUSE_STEPS_MAX = 15;
    private static final int CLICK_PAUSE_MIN_MS = 80;
    private static final int CLICK_PAUSE_MAX_MS = 200;

    private LoginPageDriver() {
    }

    /**
     * 打开页面并留出渲染时间。
     */
    static void navigate(Page page, String url) {
        page.navigate(url, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
        page.waitForTimeout(NAVIGATE_RENDER_WAIT_MS);
    }

    /**
     * 切换到密码登录表单；兼容新旧版登录页和“更多账号登录”入口。
     */
    static boolean selectAccountTabIfNeeded(Page page) {
        if (findFirstVisible(page, LoginSelectors.PERSONAL_ACCOUNT_INPUTS) != null) {
            return true;
        }

        // 新版登录页：密码登录 tab
        Locator passwordTab = findFirstVisible(page, LoginSelectors.PASSWORD_TAB_SELECTORS);
        if (ObjectUtil.isNotNull(passwordTab) && passwordTab.isVisible()) {
            humanClick(page, passwordTab);
            page.waitForTimeout(FORM_SWITCH_WAIT_MS);
            if (findFirstVisible(page, LoginSelectors.PERSONAL_ACCOUNT_INPUTS) != null) {
                log.info("使用新版密码登录表单");
                return true;
            }
        }
        if (clickTextExact(page, "密码登录")) {
            page.waitForTimeout(FORM_SWITCH_WAIT_MS);
            if (findFirstVisible(page, LoginSelectors.PERSONAL_ACCOUNT_INPUTS) != null) {
                log.info("使用新版密码登录表单");
                return true;
            }
        }

        // 兜底：华为云账号入口（更多账号登录 -> 华为云账号）
        if (clickTextExact(page, "更多账号登录")) {
            page.waitForTimeout(MORE_ACCOUNT_WAIT_MS);
            Locator originalAccount = findFirstVisible(
                    page, LoginSelectors.HUAWEI_CLOUD_ACCOUNT_SELECTORS);
            if (ObjectUtil.isNotNull(originalAccount) && originalAccount.isVisible()) {
                humanClick(page, originalAccount);
                page.waitForTimeout(ACCOUNT_FORM_WAIT_MS);
                if (findFirstVisible(page, LoginSelectors.PERSONAL_ACCOUNT_INPUTS) != null) {
                    log.info("使用华为云账号登录表单");
                    return true;
                }
            }
        }

        // 旧版登录页兼容
        if (clickTextExact(page, "账号登录")) {
            page.waitForTimeout(FORM_SWITCH_WAIT_MS);
        }
        return findFirstVisible(page, LoginSelectors.PERSONAL_ACCOUNT_INPUTS) != null;
    }

    /**
     * 在候选选择器中找到第一个可见输入框并按人工节奏逐字符填入内容。
     *
     * @param page      页面
     * @param selectors 候选 CSS 选择器
     * @param value     要填入的值
     * @return true 表示找到并填充成功
     */
    static boolean fillFirst(Page page, List<String> selectors, String value) {
        Locator locator = findFirstVisible(page, selectors);
        if (locator == null) {
            return false;
        }
        locator.fill("");
        locator.pressSequentially(value, new Locator.PressSequentiallyOptions()
                .setDelay(randomBetween(TYPE_DELAY_MIN_MS, TYPE_DELAY_MAX_MS)));
        return true;
    }

    /**
     * 模拟人工点击：先把鼠标分几步移动到目标附近随机位置，短暂停顿后再点击；
     * 取不到元素坐标时退回直接点击。
     */
    static void humanClick(Page page, Locator locator) {
        BoundingBox box = null;
        try {
            box = locator.boundingBox();
        } catch (PlaywrightException ignored) {
            // 元素可能在取坐标时被移除，退回直接点击
        }
        if (ObjectUtil.isNotNull(box)) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            double x = box.x + box.width * (0.35 + random.nextDouble() * 0.3);
            double y = box.y + box.height * (0.35 + random.nextDouble() * 0.3);
            page.mouse().move(x, y, new Mouse.MoveOptions()
                    .setSteps(randomBetween(MOUSE_STEPS_MIN, MOUSE_STEPS_MAX)));
            page.waitForTimeout(randomBetween(CLICK_PAUSE_MIN_MS, CLICK_PAUSE_MAX_MS));
        }
        locator.click();
    }

    private static int randomBetween(int minInclusive, int maxInclusive) {
        return ThreadLocalRandom.current().nextInt(minInclusive, maxInclusive + 1);
    }

    /**
     * 点击登录按钮；按钮可能因表单校验短暂处于 disabled 状态。
     */
    static boolean clickLoginButton(Page page) {
        Locator button = findFirstVisible(page, LoginSelectors.LOGIN_BUTTONS);
        if (button != null) {
            // 新版按钮在表单校验通过前带 hwid-disabled，旧版带 loginBtnDisabled
            for (int i = 0; i < LOGIN_BUTTON_POLL_ROUNDS; i++) {
                String classes = button.getAttribute("class");
                if (classes == null || (!classes.contains("hwid-disabled")
                        && !classes.contains("loginBtnDisabled"))) {
                    break;
                }
                page.waitForTimeout(LOGIN_BUTTON_POLL_INTERVAL_MS);
            }
            log.debug("登录按钮 class={}", button.getAttribute("class"));
            humanClick(page, button);
            return true;
        }
        Locator passwordInput = findFirstVisible(page, LoginSelectors.PERSONAL_PASSWORD_INPUTS);
        if (passwordInput != null) {
            passwordInput.press("Enter");
            return true;
        }
        return false;
    }

    /**
     * 在所有 frame 中点击第一个可见的精确文本元素。
     *
     * @param page 页面
     * @param text 目标文本，用 text="..." 定位保证精确匹配
     * @return true 表示点击成功
     */
    static boolean clickTextExact(Page page, String text) {
        String selector = "text=\"" + text + "\"";
        for (Frame frame : page.frames()) {
            try {
                Locator locator = frame.locator(selector).first();
                if (locator.count() > 0 && locator.isVisible()) {
                    humanClick(frame.page(), locator);
                    return true;
                }
            } catch (PlaywrightException ignored) {
                // 跳过已分离的 frame
            }
        }
        return false;
    }

    /**
     * 在所有 frame 中按候选选择器找到第一个可见元素。
     *
     * @param page      页面
     * @param selectors 候选 CSS 选择器，按顺序优先
     * @return 命中的 Locator，未找到返回 null
     */
    static Locator findFirstVisible(Page page, List<String> selectors) {
        for (Frame frame : page.frames()) {
            for (String selector : selectors) {
                try {
                    Locator locator = frame.locator(selector).first();
                    if (locator.count() > 0 && locator.isVisible()) {
                        return locator;
                    }
                } catch (PlaywrightException ignored) {
                    // 跳过已分离的 frame
                }
            }
        }
        return null;
    }

    /**
     * 在所有 frame 中找到第一个命中的可见文案。
     *
     * @param page  页面
     * @param texts 候选文案，按顺序优先
     * @return 命中的文案，未找到返回 null
     */
    static String findVisibleText(Page page, List<String> texts) {
        for (Frame frame : page.frames()) {
            for (String text : texts) {
                try {
                    Locator locator = frame.getByText(text).first();
                    if (locator.count() > 0 && locator.isVisible()) {
                        return text;
                    }
                } catch (PlaywrightException ignored) {
                    // 跳过已分离的 frame
                }
            }
        }
        return null;
    }

    /**
     * 判断会话中的页面是否全部关闭（用户手动关掉了浏览器窗口）。
     *
     * @param session 浏览器会话
     * @return true 表示全部关闭
     */
    static boolean allPagesClosed(BrowserSession session) {
        return session.context().pages().stream().allMatch(Page::isClosed);
    }

    /**
     * 获取当前登录操作页面。
     *
     * @param session 浏览器会话
     * @return 优先返回认证页；没有认证页时退回当前活动页
     */
    static Page loginPage(BrowserSession session) {
        for (Page page : session.context().pages()) {
            if (!page.isClosed() && isAuthPage(page)) {
                return page;
            }
        }
        return session.activePage();
    }

    /**
     * 判断页面是否为华为云统一认证页。
     *
     * @param page 页面，可为 null
     * @return true 表示 URL 落在认证域
     */
    static boolean isAuthPage(Page page) {
        String url = page == null ? null : page.url();
        return StrUtil.isNotBlank(url) && url.contains(HuaweiUrls.AUTH_DOMAIN);
    }
}
