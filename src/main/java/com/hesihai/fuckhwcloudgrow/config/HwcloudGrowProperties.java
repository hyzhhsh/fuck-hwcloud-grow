package com.hesihai.fuckhwcloudgrow.config;

import cn.hutool.core.util.ObjectUtil;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.time.Duration;

/**
 * 华为云成长中心签到相关配置。
 *
 * <p>账号密码等敏感项不提供 toString，避免被日志或异常信息带出。</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "hwcloud.grow")
public class HwcloudGrowProperties {

    /**
     * 华为云账号，从 HWCLOUD_USERNAME 环境变量注入。
     */
    private String username = "";
    /**
     * 华为云密码，从 HWCLOUD_PASSWORD 环境变量注入。
     */
    private String password = "";
    /**
     * 展示用账号名，从 HWCLOUD_ACCOUNT_NAME 环境变量注入，可为空。
     */
    private String accountName = "";
    /**
     * 每日签到 cron 表达式，默认每天 8 点。
     */
    private String cron = "0 0 8 * * *";
    /**
     * 定时任务时区。
     */
    private String zone = "Asia/Shanghai";
    /**
     * 数据根目录，登录态、结果与诊断材料都放在其下。
     */
    private Path dataDir = Path.of("data");
    /**
     * 接口调用配置。
     */
    private Api api = new Api();
    /**
     * 浏览器降级配置。
     */
    private Browser browser = new Browser();
    /**
     * 息知通知配置。
     */
    private Notify notify = new Notify();
    /**
     * 签到运行参数。
     */
    private Run run = new Run();

    /**
     * 登录态目录：显式配置 authFile 时取其父目录，否则用默认的 data/state。
     *
     * @return 登录态目录绝对路径
     */
    public Path effectiveStateDir() {
        Path authFile = browser.getAuthFile();
        return ObjectUtil.isNotNull(authFile)
                ? authFile.toAbsolutePath().getParent()
                : dataDir.resolve("state");
    }

    /**
     * 浏览器 profile 目录：显式配置优先，否则 data/browser-profile。
     *
     * @return profile 目录
     */
    public Path effectiveProfileDir() {
        return ObjectUtil.defaultIfNull(browser.getProfileDir(),
                dataDir.resolve("browser-profile"));
    }

    /**
     * 登录态文件：显式配置优先，否则 data/state/auth.json。
     *
     * @return 登录态文件路径
     */
    public Path effectiveAuthFile() {
        return ObjectUtil.defaultIfNull(browser.getAuthFile(),
                dataDir.resolve("state/auth.json"));
    }

    /**
     * 登录元数据文件：显式配置优先，否则 data/state/auth-meta.json。
     *
     * @return 元数据文件路径
     */
    public Path effectiveAuthMetaFile() {
        return ObjectUtil.defaultIfNull(browser.getAuthMetaFile(),
                dataDir.resolve("state/auth-meta.json"));
    }

    /**
     * 最近一次签到结果文件。
     *
     * @return 固定为 data/state/last-result.json
     */
    public Path effectiveLastResultFile() {
        return dataDir.resolve("state/last-result.json");
    }

    /**
     * 诊断材料目录。
     *
     * @return 固定为 data/diagnostics
     */
    public Path effectiveDiagnosticsDir() {
        return dataDir.resolve("diagnostics");
    }

    /**
     * 华为云接口调用参数。
     */
    @Getter
    @Setter
    public static class Api {

        /**
         * 接口根地址。
         */
        private String baseUrl = "https://devdata.huaweicloud.com";
        /**
         * 连接超时。
         */
        private Duration connectTimeout = Duration.ofSeconds(10);
        /**
         * 请求读取超时。
         */
        private Duration requestTimeout = Duration.ofSeconds(30);
        /**
         * 网络/5xx 最大尝试次数（含首次）。
         */
        private int maxAttempts = 3;
        /**
         * 网络/5xx 重试的基础退避间隔，逐次线性放大。
         */
        private Duration retryBackoff = Duration.ofSeconds(2);
        /**
         * 签到人数较多时的基础退避间隔，实际等待取 1~3 倍随机值。
         */
        private Duration signBusyBackoff = Duration.ofSeconds(5);
    }

    /**
     * 浏览器降级与登录流程参数。
     */
    @Getter
    @Setter
    public static class Browser {

        /**
         * 是否无头运行，默认有头以便人工介入验证码。
         */
        private boolean headless = false;
        /**
         * 浏览器持久化目录，默认 data/browser-profile。
         */
        private Path profileDir;
        /**
         * 登录态文件，默认 data/state/auth.json。
         */
        private Path authFile;
        /**
         * 登录元数据文件，默认 data/state/auth-meta.json。
         */
        private Path authMetaFile;
        /**
         * 登录流程整体超时。
         */
        private Duration loginTimeout = Duration.ofSeconds(90);
        /**
         * 等待人工验证的最长时间，超时判失败。
         */
        private Duration humanWait = Duration.ofMinutes(10);
        /**
         * 登录流程中各轮询的间隔。
         */
        private Duration pollInterval = Duration.ofSeconds(2);
        /**
         * 是否追加 --no-sandbox 等参数（容器内以 root 运行时需要）。
         */
        private boolean noSandbox = false;
    }

    /**
     * 息知（xizhi）微信推送配置。
     */
    @Getter
    @Setter
    public static class Notify {

        /**
         * 息知推送 key，为空则不推送。
         */
        private String xizhiKey = "";
        /**
         * 息知服务地址。
         */
        private String xizhiBaseUrl = "https://xizhi.qqoq.net";
        /**
         * noVNC 页面地址，用于人工介入通知中的跳转链接。
         */
        private String noVncUrl = "";
        /**
         * 推送请求超时。
         */
        private Duration timeout = Duration.ofSeconds(5);
    }

    /**
     * 签到运行参数。
     */
    @Getter
    @Setter
    public static class Run {

        /**
         * 提交后轮询签到状态的最大次数。
         */
        private int statusPollAttempts = 5;
        /**
         * 轮询签到状态的间隔。
         */
        private Duration statusPollInterval = Duration.ofSeconds(3);
    }
}
