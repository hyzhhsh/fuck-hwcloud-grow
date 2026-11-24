package com.hesihai.fuckhwcloudgrow.service.task.handler;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.hesihai.fuckhwcloudgrow.client.GrowthApi;
import com.hesihai.fuckhwcloudgrow.client.GrowthApiException;
import com.hesihai.fuckhwcloudgrow.client.dto.TaskListResponse;
import com.hesihai.fuckhwcloudgrow.client.harmony.HarmonyClient;
import com.hesihai.fuckhwcloudgrow.client.harmony.HarmonyUrls;
import com.hesihai.fuckhwcloudgrow.client.harmony.dto.HarmonyCaseListResponse;
import com.hesihai.fuckhwcloudgrow.client.harmony.dto.HarmonyDailyTaskResponse;
import com.hesihai.fuckhwcloudgrow.client.harmony.dto.HarmonyHeartbeatResponse;
import com.hesihai.fuckhwcloudgrow.client.harmony.dto.HarmonySessionResponse;
import com.hesihai.fuckhwcloudgrow.config.HwcloudGrowProperties;
import com.hesihai.fuckhwcloudgrow.model.FailCode;
import com.hesihai.fuckhwcloudgrow.model.task.TaskResult;
import com.hesihai.fuckhwcloudgrow.model.task.TaskResults;
import com.hesihai.fuckhwcloudgrow.service.task.TaskContext;
import com.hesihai.fuckhwcloudgrow.service.task.TaskHandler;
import com.hesihai.fuckhwcloudgrow.util.SleepUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 每日浏览一个鸿蒙案例：纯 HTTP 调用计时会话与心跳接口，不需要浏览器。
 */
@Slf4j
@Component
public class HarmonyCaseTaskHandler implements TaskHandler {

    public static final String KEY = "harmony-case";
    private static final int DEFAULT_NEED_SECONDS = 30;

    private final HwcloudGrowProperties properties;

    public HarmonyCaseTaskHandler(HwcloudGrowProperties properties) {
        this.properties = properties;
    }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public String displayName() {
        return "每日浏览一个鸿蒙案例";
    }

    @Override
    public boolean enabled() {
        return properties.getTasks().getHarmony().isEnabled();
    }

    @Override
    public Set<String> requiredHosts() {
        return Set.of(HarmonyUrls.HARMONY_HOST);
    }

    @Override
    public boolean requiresBrowser() {
        return false;
    }

    @Override
    public int order() {
        return 20;
    }

    @Override
    public TaskResult run(TaskContext ctx) {
        TaskResult result = runInternal(ctx);
        if (ObjectUtil.isNotNull(result)
                && (result.getApiSource() == null || "NONE".equals(result.getApiSource()))) {
            result.setApiSource("HTTP");
        }
        return result;
    }

    private TaskResult runInternal(TaskContext ctx) {
        Instant startedAt = Instant.now();
        Map<String, Object> detail = new LinkedHashMap<>();
        try {
            HarmonyClient client = ctx.http().harmonyClient();
            HarmonyDailyTaskResponse daily = client.getDailyTask();
            if (ObjectUtil.isNull(daily) || ObjectUtil.isNull(daily.getTask())) {
                return TaskResults.failed(KEY, displayName(), startedAt, FailCode.UNKNOWN,
                        "鸿蒙案例任务信息为空", false, detail);
            }
            boolean loggedIn = !Boolean.FALSE.equals(daily.getLoggedIn())
                    && !Boolean.FALSE.equals(daily.getTask().getLoggedIn());
            if (!loggedIn) {
                return TaskResults.failed(KEY, displayName(), startedAt, FailCode.SESSION_EXPIRED,
                        "鸿蒙实践中心登录态已失效", false, detail);
            }
            detail.put("periodKey", daily.getTask().getPeriodKey());

            TaskResult settled = settledResult(client, daily, startedAt);
            if (ObjectUtil.isNotNull(settled)) {
                return settled;
            }

            String caseId = resolveCaseId(client);
            detail.put("caseId", caseId);
            Optional<HarmonySessionResponse> session = client.startSession(caseId);
            if (session.isEmpty()) {
                return TaskResults.alreadyDone(KEY, displayName(), startedAt,
                        "今日已浏览过鸿蒙案例", detail);
            }
            String sessionId = session.get().getSessionId();
            detail.put("sessionId", sessionId);

            HarmonyHeartbeatResponse last = heartbeatUntilDone(client, session, ctx, detail);
            if (ObjectUtil.isNull(last) || !Boolean.TRUE.equals(last.getCompleted())) {
                return TaskResults.failed(KEY, displayName(), startedAt, FailCode.UNKNOWN,
                        "浏览计时未在预期时间内完成", false, detail);
            }

            Integer pointsHint = daily.getTask().getPointsHint();
            // 心跳返回的 points 是积分余额，本次增量按任务标注的积分计算
            Integer delta = Boolean.TRUE.equals(last.getAlreadyAwardedToday())
                    ? 0 : pointsHint;
            detail.put("totalPoints", last.getPoints());
            Boolean finished = growthTaskFinished(ctx);
            detail.put("growthTaskFinished", finished);
            if (Boolean.TRUE.equals(finished)) {
                return TaskResults.success(KEY, displayName(), startedAt,
                        "已完成鸿蒙案例浏览", pointsHint, delta, detail);
            }
            return TaskResults.partial(KEY, displayName(), startedAt,
                    "案例浏览已完成，成长中心任务状态待登记", pointsHint, detail);
        } catch (GrowthApiException e) {
            return TaskResults.failed(KEY, displayName(), startedAt, e.getCode(),
                    apiFailureMessage(e), false, detail);
        }
    }

    /**
     * 已进入本周期完成态时直接返回，避免重复开会话；发放中时短暂等待收敛。
     */
    private TaskResult settledResult(HarmonyClient client, HarmonyDailyTaskResponse daily,
                                     Instant startedAt) {
        HarmonyDailyTaskResponse.TaskInfo task = daily.getTask();
        String status = StrUtil.blankToDefault(task.getTodayStatus(), task.getRecordStatus());
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("periodKey", task.getPeriodKey());
        if ("done".equalsIgnoreCase(status)) {
            return TaskResults.alreadyDone(KEY, displayName(), startedAt, "今日已浏览过鸿蒙案例", detail);
        }
        if ("awarding".equalsIgnoreCase(status)) {
            status = awaitAwarding(client, detail);
            if ("done".equalsIgnoreCase(status)) {
                return TaskResults.alreadyDone(KEY, displayName(), startedAt,
                        "今日已浏览过鸿蒙案例", detail);
            }
        }
        if ("awarding".equalsIgnoreCase(status)
                || "award_failed".equalsIgnoreCase(status)
                || "failed_terminal".equalsIgnoreCase(status)) {
            return TaskResults.partial(KEY, displayName(), startedAt,
                    "案例浏览已完成，积分发放状态：" + status, task.getPointsHint(), detail);
        }
        return null;
    }

    private String awaitAwarding(HarmonyClient client, Map<String, Object> detail) {
        String status = "awarding";
        for (int attempt = 1; attempt <= 3; attempt++) {
            SleepUtils.sleep(Duration.ofSeconds(5),
                    e -> new GrowthApiException(FailCode.NETWORK, "等待被中断", e));
            HarmonyDailyTaskResponse latest = client.getDailyTask();
            if (ObjectUtil.isNull(latest) || ObjectUtil.isNull(latest.getTask())) {
                continue;
            }
            status = StrUtil.blankToDefault(latest.getTask().getTodayStatus(),
                    latest.getTask().getRecordStatus());
            detail.put("awardStatus", status);
            if (!"awarding".equalsIgnoreCase(status)) {
                return status;
            }
        }
        return status;
    }

    private String resolveCaseId(HarmonyClient client) {
        String configured = StrUtil.trimToNull(properties.getTasks().getHarmony().getCaseId());
        if (ObjectUtil.isNotNull(configured)) {
            return configured;
        }
        HarmonyCaseListResponse cases = client.listCases(
                Math.max(1, properties.getTasks().getHarmony().getCasePageSize()));
        List<HarmonyCaseListResponse.CaseItem> items = cases == null ? null : cases.getCases();
        if (items == null || items.isEmpty()) {
            throw new GrowthApiException(FailCode.UNKNOWN, "鸿蒙案例列表为空，无法选择浏览目标");
        }
        int index = Math.floorMod(LocalDate.now().getDayOfYear(), items.size());
        return items.get(index).getId();
    }

    private HarmonyHeartbeatResponse heartbeatUntilDone(HarmonyClient client,
                                                        Optional<HarmonySessionResponse> session,
                                                        TaskContext ctx,
                                                        Map<String, Object> detail) {
        Duration interval = properties.getTasks().getHarmony().getHeartbeatInterval();
        Duration extraWait = properties.getTasks().getHarmony().getMaxExtraWait();
        int needSeconds = ObjectUtil.defaultIfNull(session.get().getNeedSeconds(), DEFAULT_NEED_SECONDS);
        String sessionId = session.get().getSessionId();
        Instant deadline = Instant.now().plusSeconds(needSeconds).plus(extraWait);
        if (deadline.isAfter(ctx.deadline())) {
            deadline = ctx.deadline();
        }

        HarmonyHeartbeatResponse last = null;
        while (Instant.now().isBefore(deadline)) {
            Optional<HarmonyHeartbeatResponse> beat = client.heartbeat(sessionId);
            if (beat.isEmpty()) {
                Optional<HarmonyHeartbeatResponse> completed = completedFromTask(client);
                if (completed.isPresent()) {
                    return completed.get();
                }
                throw new GrowthApiException(FailCode.UNKNOWN, "浏览计时会话已被服务端结束");
            }
            last = beat.get();
            if (StrUtil.isNotBlank(last.getSessionId())) {
                sessionId = last.getSessionId();
            }
            detail.put("accumulatedMs", last.getAccumulatedMs());
            detail.put("awardStatus", last.getAwardStatus());
            if (Boolean.TRUE.equals(last.getCompleted())) {
                return last;
            }
            long remaining = Duration.between(Instant.now(), deadline).toMillis();
            if (remaining <= 0) {
                break;
            }
            SleepUtils.sleep(Duration.ofMillis(Math.min(interval.toMillis(), remaining)),
                    e -> new GrowthApiException(FailCode.NETWORK, "等待被中断", e));
        }
        return last;
    }

    /**
     * 心跳会话被服务端结束（404/409）时，回查任务状态判断是否已经完成。
     */
    private Optional<HarmonyHeartbeatResponse> completedFromTask(HarmonyClient client) {
        HarmonyDailyTaskResponse latest = client.getDailyTask();
        if (ObjectUtil.isNull(latest) || ObjectUtil.isNull(latest.getTask())) {
            return Optional.empty();
        }
        if (!"done".equalsIgnoreCase(latest.getTask().getTodayStatus())) {
            return Optional.empty();
        }
        HarmonyHeartbeatResponse synthetic = new HarmonyHeartbeatResponse();
        synthetic.setCompleted(true);
        synthetic.setPoints(latest.getTask().getPointsHint());
        return Optional.of(synthetic);
    }

    /**
     * 用成长中心任务列表复核卡片是否已完成；查询失败返回 null，不影响本地结论。
     */
    private Boolean growthTaskFinished(TaskContext ctx) {
        try (GrowthApi api = ctx.http().growthApi()) {
            return matchFinished(api.listTasks());
        } catch (Exception e) {
            log.warn("查询成长中心任务列表失败，跳过完成状态复核：{}", e.getMessage());
            return null;
        }
    }

    private Boolean matchFinished(List<TaskListResponse.TaskRecord> records) {
        if (records == null) {
            return null;
        }
        String taskTag = properties.getTasks().getHarmony().getTaskTag();
        List<String> keywords = properties.getTasks().getHarmony().getTitleKeywords();
        for (TaskListResponse.TaskRecord record : records) {
            boolean tagMatch = StrUtil.isNotBlank(taskTag)
                    && taskTag.equalsIgnoreCase(record.getTaskTag());
            boolean keywordMatch = StrUtil.isNotBlank(record.getTitle())
                    && keywords.stream().anyMatch(record.getTitle()::contains);
            if (tagMatch || keywordMatch) {
                return Boolean.TRUE.equals(record.getIsFinish());
            }
        }
        return null;
    }

    private String apiFailureMessage(GrowthApiException e) {
        String message = StrUtil.blankToDefault(e.getMessage(), "鸿蒙案例任务失败");
        return StrUtil.isBlank(e.getVendorCode()) ? message : message + "（" + e.getVendorCode() + "）";
    }
}