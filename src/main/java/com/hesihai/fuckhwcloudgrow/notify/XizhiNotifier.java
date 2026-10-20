package com.hesihai.fuckhwcloudgrow.notify;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.URLUtil;
import com.hesihai.fuckhwcloudgrow.config.HwcloudGrowProperties;
import com.hesihai.fuckhwcloudgrow.model.SigninResult;
import com.hesihai.fuckhwcloudgrow.model.SigninStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 通过 Xizhi 推送签到事件。推送失败只记录日志，不影响签到主流程。
 */
@Slf4j
@Component
public class XizhiNotifier {

    private static final int MAX_CONTENT_LENGTH = 500;
    private static final int MAX_TITLE_LENGTH = 80;
    private static final int MAX_SENT_EVENTS = 1000;
    /**
     * 通知面向国内用户，时区固定东八区，与服务器所在时区解耦。
     */
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    /**
     * 通知中的时间展示格式，与 {@link #ZONE} 绑定。
     */
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZONE);

    private final HwcloudGrowProperties properties;
    private final RestClient restClient;
    // 按 runId+事件类型去重：同一任务的三种通知（人工/失败/结果）各只发送一次
    private final Set<String> sentEvents = ConcurrentHashMap.newKeySet();

    public XizhiNotifier(HwcloudGrowProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        int timeoutMillis = (int) properties.getNotify().getTimeout().toMillis();
        factory.setConnectTimeout(timeoutMillis);
        factory.setReadTimeout(timeoutMillis);
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    /**
     * 用 Hutool 拼装并编码 GET 请求，避免手写字符串拼接与 URLEncoder。
     */
    static String buildUrl(String baseUrl, String key, String title, String content) {
        String base = StrUtil.strip(
                StrUtil.blankToDefault(baseUrl, "https://xizhi.qqoq.net"), "/");
        Map<String, String> query = new LinkedHashMap<>();
        query.put("title", title);
        query.put("content", content);
        return StrUtil.format("{}/{}.send?{}", base, URLUtil.encode(key),
                URLUtil.buildQuery(query, StandardCharsets.UTF_8));
    }

    private static String truncate(String value, int maxLength) {
        return StrUtil.subPre(StrUtil.nullToEmpty(value), maxLength);
    }

    private static String nullToDash(Object value) {
        return String.valueOf(ObjectUtil.defaultIfNull(value, "-"));
    }

    /**
     * 推送“需要人工验证”通知。
     *
     * @param runId    任务 ID，用于去重
     * @param reason   需要人工介入的原因
     * @param noVncUrl noVNC 页面地址，为空时提示查看本机浏览器窗口
     * @param wait     人工等待时长
     */
    public void humanRequired(String runId, String reason, String noVncUrl, Duration wait) {
        if (ObjectUtil.isNull(runId) || !markSent(runId + ":human")) {
            return;
        }
        String target = StrUtil.isBlank(noVncUrl)
                ? "请查看运行机器上弹出的浏览器窗口。"
                : "请打开：" + noVncUrl;
        String content = "任务：" + runId
                + "\n原因：" + reason
                + "\n" + target
                + "\n请在 " + Math.max(1, wait.toMinutes()) + " 分钟内完成，超时将失败。";
        send("华为云签到需要人工验证", content);
    }

    /**
     * 推送签到失败通知。
     *
     * @param result 失败结果，为 null 或 runId 为空时跳过
     */
    public void failed(SigninResult result) {
        if (ObjectUtil.isNull(result) || ObjectUtil.isNull(result.getRunId())
                || !markSent(result.getRunId() + ":failed")) {
            return;
        }
        String content = "状态：失败"
                + "\n账号：" + nullToDash(result.getAccountName())
                + "\n错误码：" + nullToDash(result.getErrorCode())
                + "\n原因：" + nullToDash(result.getMessage())
                + "\n时间：" + TIME_FORMAT.format(ObjectUtil.defaultIfNull(
                result.getFinishedAt(), Instant.now()));
        send("华为云签到失败", content);
    }

    /**
     * 推送签到结果通知（成功或今日已签到）。
     *
     * @param result 结果，为 null 或 runId 为空时跳过
     */
    public void dailyResult(SigninResult result) {
        if (ObjectUtil.isNull(result) || ObjectUtil.isNull(result.getRunId())
                || !markSent(result.getRunId() + ":result")) {
            return;
        }
        String status = result.getStatus() == SigninStatus.ALREADY_SIGNED
                ? "今日已签到"
                : "签到成功";
        String points = nullToDash(result.getPointsBefore())
                + " -> " + nullToDash(result.getPointsAfter());
        if (ObjectUtil.isNotNull(result.getPointsDelta())) {
            points += "（" + (result.getPointsDelta() >= 0 ? "+" : "")
                    + result.getPointsDelta() + "）";
        }
        String content = "状态：" + status
                + "\n账号：" + nullToDash(result.getAccountName())
                + "\n积分：" + points
                + "\n连续签到：" + nullToDash(result.getContinuityCount()) + " 天"
                + "\n最近签到：" + nullToDash(result.getLastSignTime())
                + "\n来源：" + nullToDash(result.getApiSource())
                + "\n时间：" + TIME_FORMAT.format(ObjectUtil.defaultIfNull(
                result.getFinishedAt(), Instant.now()));
        send("华为云签到结果", content);
    }

    /**
     * 实际发送推送；未配置 key 时静默跳过，失败只记日志。
     *
     * @param title   通知标题，超长会被截断
     * @param content 通知内容，超长会被截断
     * @return true 表示已发送
     */
    boolean send(String title, String content) {
        String key = properties.getNotify().getXizhiKey();
        if (StrUtil.isBlank(key)) {
            log.debug("未配置 Xizhi key，跳过推送：{}", title);
            return false;
        }
        String url = buildUrl(properties.getNotify().getXizhiBaseUrl(), key,
                truncate(title, MAX_TITLE_LENGTH),
                truncate(content, MAX_CONTENT_LENGTH));
        try {
            restClient.get().uri(URI.create(url)).retrieve().toBodilessEntity();
            log.info("Xizhi 推送已发送：{}", title);
            return true;
        } catch (Exception e) {
            // 不记录异常 message，避免把带 key 的完整 URL 写入日志
            log.warn("Xizhi 推送失败：{}（{}）", title, e.getClass().getSimpleName());
            return false;
        }
    }

    /**
     * 限制去重集合大小，避免服务长期运行时内存无界增长。
     */
    private boolean markSent(String key) {
        if (sentEvents.size() >= MAX_SENT_EVENTS) {
            sentEvents.clear();
        }
        return sentEvents.add(key);
    }
}
