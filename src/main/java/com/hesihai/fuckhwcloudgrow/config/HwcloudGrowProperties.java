package com.hesihai.fuckhwcloudgrow.config;

import cn.hutool.core.util.ObjectUtil;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * 华为云成长中心每日任务与登录相关配置。
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
     * 每日任务批次 cron 表达式，默认每天 8 点。
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
     * 登录流程使用的浏览器配置。
     */
    private Browser browser = new Browser();
    /**
     * 息知通知配置。
     */
    private Notify notify = new Notify();
    /**
     * 多任务自动化配置。
     */
    private Tasks tasks = new Tasks();

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
     * 最近一次批量任务结果文件。
     *
     * @return 固定为 data/state/last-batch.json
     */
    public Path effectiveLastBatchFile() {
        return dataDir.resolve("state/last-batch.json");
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
        private String baseUrl = "https://grow-dataset.developer.huaweicloud.com";
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
    }

    /**
     * 登录流程与浏览器参数。
     */
    @Getter
    @Setter
    public static class Browser {

        /**
         * 是否无头运行，默认有头以便人工介入验证码。
         */
        private boolean headless = false;
        /**
         * 浏览器通道：chrome 表示使用系统安装的 Google Chrome（指纹更可信）；
         * 留空则直接使用 Playwright 捆绑 Chromium。系统 Chrome 启动失败会自动回退捆绑 Chromium。
         */
        private String channel = "chrome";
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
     * 多任务自动化配置。
     */
    @Getter
    @Setter
    public static class Tasks {

        /**
         * 整批任务的最长执行时间，超出后剩余任务记 SKIPPED。
         */
        private Duration totalTimeout = Duration.ofMinutes(20);
        /**
         * 凭据探测配置。
         */
        private Credential credential = new Credential();
        /**
         * 每日签到任务开关。
         */
        private Signin signin = new Signin();
        /**
         * 鸿蒙案例浏览任务配置。
         */
        private Harmony harmony = new Harmony();

        @Getter
        @Setter
        public static class Credential {

            /**
             * 单域登录态探测超时。
             */
            private Duration probeTimeout = Duration.ofSeconds(10);
        }

        @Getter
        @Setter
        public static class Signin {

            private boolean enabled = true;
        }

        @Getter
        @Setter
        public static class Harmony {

            private boolean enabled = true;
            /**
             * 鸿蒙实践中心站点源。
             */
            private String baseUrl = "https://harmonybp.developer.huaweicloud.com";
            /**
             * 心跳上报间隔，与页面行为保持一致。
             */
            private Duration heartbeatInterval = Duration.ofSeconds(5);
            /**
             * 在服务端要求时长之外额外等待的上限。
             */
            private Duration maxExtraWait = Duration.ofSeconds(30);
            /**
             * 选择案例时的列表页大小。
             */
            private int casePageSize = 20;
            /**
             * 指定案例 ID；为空则按日期在列表中轮转。
             */
            private String caseId = "";
            /**
             * 成长中心任务列表中该任务的 task_tag；未知时留空，按标题关键字匹配。
             */
            private String taskTag = "";
            /**
             * 成长中心任务列表中该任务的标题关键字。
             */
            private List<String> titleKeywords = List.of("鸿蒙");
        }
    }
}
