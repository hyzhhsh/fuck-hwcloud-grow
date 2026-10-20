package com.hesihai.fuckhwcloudgrow.client.browser;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.hesihai.fuckhwcloudgrow.client.HuaweiUrls;
import com.hesihai.fuckhwcloudgrow.config.HwcloudGrowProperties;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.ViewportSize;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 有头浏览器会话：持久化用户目录 + 登录态保存。
 */
@Slf4j
public class BrowserSession implements AutoCloseable {

    private static final int DEFAULT_TIMEOUT_MS = 30_000;
    private static final int NAVIGATION_TIMEOUT_MS = 60_000;
    /**
     * 浏览器 locale 固定中文，登录页按中文渲染，选择器文案依赖此设置。
     */
    private static final String BROWSER_LOCALE = "zh-CN";
    /**
     * 时区固定东八区，与签到任务的业务时区保持一致。
     */
    private static final String BROWSER_TIMEZONE = "Asia/Shanghai";
    private static final String ARG_START_MAXIMIZED = "--start-maximized";
    private static final String ARG_WINDOW_SIZE = "--window-size=1920,1080";
    private static final String ARG_LANG = "--lang=zh-CN";
    private static final String ARG_NO_SANDBOX = "--no-sandbox";
    private static final String ARG_DISABLE_DEV_SHM = "--disable-dev-shm-usage";
    private static final String ARG_DISABLE_GPU = "--disable-gpu";

    private final Playwright playwright;
    private final BrowserContext context;

    private BrowserSession(Playwright playwright, BrowserContext context) {
        this.playwright = playwright;
        this.context = context;
        ensurePage();
    }

    /**
     * 启动持久化浏览器会话。
     *
     * @param properties 配置，用于 profile 目录、headless、no-sandbox 等
     * @return 已启动的会话，调用方负责 {@link #close()}
     * @throws BrowserLaunchException 目录创建失败或浏览器启动失败
     */
    public static BrowserSession launch(HwcloudGrowProperties properties) {
        Path profileDir = properties.effectiveProfileDir().toAbsolutePath();
        try {
            FileUtil.mkdir(profileDir.toFile());
        } catch (Exception e) {
            throw new BrowserLaunchException("无法创建浏览器 profile 目录：" + profileDir, e);
        }

        Playwright playwright = null;
        try {
            playwright = Playwright.create();
            BrowserType.LaunchPersistentContextOptions options =
                    new BrowserType.LaunchPersistentContextOptions()
                            .setHeadless(properties.getBrowser().isHeadless())
                            .setLocale(BROWSER_LOCALE)
                            .setTimezoneId(BROWSER_TIMEZONE)
                            .setViewportSize((ViewportSize) null)
                            .setArgs(launchArgs(properties));
            BrowserContext context = playwright.chromium().launchPersistentContext(profileDir, options);
            log.info("浏览器已启动，profile={}", profileDir);
            return new BrowserSession(playwright, context);
        } catch (PlaywrightException e) {
            closeQuietly(playwright);
            throw new BrowserLaunchException(
                    "启动有头浏览器失败。请确认 Chromium 已安装（mvn -Pinstall-browser compile exec:java），"
                            + "Linux 下确认 DISPLAY/Xvfb 正常。原因：" + e.getMessage(),
                    e);
        }
    }

    private static List<String> launchArgs(HwcloudGrowProperties properties) {
        List<String> args = new ArrayList<>();
        args.add(ARG_START_MAXIMIZED);
        args.add(ARG_WINDOW_SIZE);
        args.add(ARG_LANG);
        if (properties.getBrowser().isNoSandbox()) {
            args.add(ARG_NO_SANDBOX);
            args.add(ARG_DISABLE_DEV_SHM);
            args.add(ARG_DISABLE_GPU);
        }
        return args;
    }

    private static void closeQuietly(Playwright playwright) {
        if (ObjectUtil.isNull(playwright)) {
            return;
        }
        try {
            playwright.close();
        } catch (Exception e) {
            log.debug("关闭 Playwright 失败：{}", e.getMessage());
        }
    }

    /**
     * 获取浏览器上下文。
     *
     * @return Playwright BrowserContext
     */
    public BrowserContext context() {
        return context;
    }

    /**
     * 获取一个可用页面，没有则新建。
     *
     * @return 页面，已设置默认超时
     */
    public Page page() {
        return ensurePage();
    }

    /**
     * 获取当前“活动”页面。
     *
     * <p>优先级：成长中心页 &gt; 认证页 &gt; 任意页面 &gt; 新建页面。
     * 登录流程优先复用已打开的成长中心页，其次才是认证页，
     * 避免在无关页面上执行签到操作。</p>
     *
     * @return 活动页面，永不返回 null
     */
    public Page activePage() {
        List<Page> pages = context.pages();
        for (Page page : pages) {
            String url = page.url();
            if (StrUtil.isNotBlank(url) && url.contains(HuaweiUrls.DEVELOPER_DOMAIN)) {
                return page;
            }
        }
        for (Page page : pages) {
            String url = page.url();
            if (StrUtil.isNotBlank(url) && url.contains(HuaweiUrls.AUTH_DOMAIN)) {
                return page;
            }
        }
        return CollUtil.isEmpty(pages) ? context.newPage() : pages.get(0);
    }

    /**
     * 读取当前浏览器的 User-Agent，供登录态保存后 HTTP 快速路径复用。
     *
     * @return UA 字符串，读取失败时为 null
     */
    public String userAgent() {
        try {
            Object result = activePage().evaluate("navigator.userAgent");
            return ObjectUtil.isNull(result) ? null : result.toString();
        } catch (PlaywrightException e) {
            return null;
        }
    }

    /**
     * 导出当前浏览器 storageState JSON，由 AuthStateStore 原子落盘。
     */
    public String storageState() {
        return context.storageState();
    }

    private Page ensurePage() {
        List<Page> pages = context.pages();
        Page page = CollUtil.isEmpty(pages) ? context.newPage() : pages.get(0);
        page.setDefaultTimeout(DEFAULT_TIMEOUT_MS);
        page.setDefaultNavigationTimeout(NAVIGATION_TIMEOUT_MS);
        return page;
    }

    @Override
    public void close() {
        try {
            context.close();
        } catch (Exception e) {
            log.debug("关闭浏览器 context 失败：{}", e.getMessage());
        }
        closeQuietly(playwright);
    }
}
