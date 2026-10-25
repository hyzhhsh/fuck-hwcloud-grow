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
import java.util.Map;

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
    /**
     * 移除 Blink 的 AutomationControlled 特征，使 navigator.webdriver 与真实 Chrome 一致为 false。
     */
    private static final String ARG_DISABLE_BLINK_AUTOMATION =
            "--disable-blink-features=AutomationControlled";
    /**
     * 初始化脚本兜底：个别渠道浏览器可能忽略上面的启动参数，再强制一次 webdriver=false。
     */
    private static final String HIDE_WEBDRIVER_SCRIPT =
            "Object.defineProperty(navigator, 'webdriver', { get: () => false });";

    private final Playwright playwright;
    private final BrowserContext context;

    private BrowserSession(Playwright playwright, BrowserContext context) {
        this.playwright = playwright;
        this.context = context;
        context.addInitScript(HIDE_WEBDRIVER_SCRIPT);
        ensurePage();
    }

    /**
     * 启动持久化浏览器会话。
     *
     * <p>优先使用配置的 channel（如系统安装的 Google Chrome，指纹可信度更高）；
     * channel 未配置或启动失败时回退 Playwright 捆绑 Chromium。</p>
     *
     * @param properties 配置，用于 profile 目录、channel、headless、no-sandbox 等
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

        String channel = StrUtil.trimToNull(properties.getBrowser().getChannel());
        // 指定了 channel 时跳过捆绑浏览器下载；回退路径要求捆绑 Chromium 已存在
        Playwright playwright = null;
        try {
            Playwright.CreateOptions createOptions = new Playwright.CreateOptions();
            if (ObjectUtil.isNotNull(channel)) {
                createOptions.setEnv(Map.of("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1"));
            }
            playwright = Playwright.create(createOptions);
            BrowserContext context = launchContext(playwright, properties, channel);
            log.info("浏览器已启动，channel={}，profile={}",
                    ObjectUtil.defaultIfNull(channel, "chromium"), profileDir);
            return new BrowserSession(playwright, context);
        } catch (PlaywrightException first) {
            if (ObjectUtil.isNotNull(channel)) {
                log.warn("启动系统 Chrome（channel={}）失败：{}，回退捆绑 Chromium",
                        channel, first.getMessage());
                try {
                    BrowserContext context = launchContext(playwright, properties, null);
                    return new BrowserSession(playwright, context);
                } catch (PlaywrightException second) {
                    closeQuietly(playwright);
                    throw new BrowserLaunchException(
                            "系统 Chrome 与捆绑 Chromium 均启动失败。若从未安装过捆绑浏览器，"
                                    + "请先执行 mvn -Pinstall-browser compile exec:java，"
                                    + "或在容器镜像中安装 google-chrome-stable。原因："
                                    + second.getMessage(),
                            second);
                }
            }
            closeQuietly(playwright);
            throw new BrowserLaunchException(
                    "启动有头浏览器失败。请确认 Chromium 已安装（mvn -Pinstall-browser compile exec:java），"
                            + "Linux 下确认 DISPLAY/Xvfb 正常。原因：" + first.getMessage(),
                    first);
        }
    }

    private static BrowserContext launchContext(Playwright playwright,
                                                HwcloudGrowProperties properties,
                                                String channel) {
        BrowserType.LaunchPersistentContextOptions options =
                new BrowserType.LaunchPersistentContextOptions()
                        .setHeadless(properties.getBrowser().isHeadless())
                        .setLocale(BROWSER_LOCALE)
                        .setTimezoneId(BROWSER_TIMEZONE)
                        .setViewportSize((ViewportSize) null)
                        .setArgs(launchArgs(properties));
        if (ObjectUtil.isNotNull(channel)) {
            options.setChannel(channel);
        }
        return playwright.chromium().launchPersistentContext(
                properties.effectiveProfileDir().toAbsolutePath(), options);
    }

    private static List<String> launchArgs(HwcloudGrowProperties properties) {
        List<String> args = new ArrayList<>();
        args.add(ARG_START_MAXIMIZED);
        args.add(ARG_WINDOW_SIZE);
        args.add(ARG_LANG);
        args.add(ARG_DISABLE_BLINK_AUTOMATION);
        if (properties.getBrowser().isNoSandbox()) {
            args.add(ARG_NO_SANDBOX);
            args.add(ARG_DISABLE_DEV_SHM);
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
