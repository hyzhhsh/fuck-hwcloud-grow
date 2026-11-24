package com.hesihai.fuckhwcloudgrow.service.task;

import cn.hutool.core.util.IdUtil;
import com.hesihai.fuckhwcloudgrow.auth.AuthStateStore;
import com.hesihai.fuckhwcloudgrow.auth.CredentialService;
import com.hesihai.fuckhwcloudgrow.auth.CredentialStatus;
import com.hesihai.fuckhwcloudgrow.client.GrowthApi;
import com.hesihai.fuckhwcloudgrow.client.HttpAccess;
import com.hesihai.fuckhwcloudgrow.client.dto.UserInfoResponse;
import com.hesihai.fuckhwcloudgrow.config.HwcloudGrowProperties;
import com.hesihai.fuckhwcloudgrow.model.FailCode;
import com.hesihai.fuckhwcloudgrow.model.RunTrigger;
import com.hesihai.fuckhwcloudgrow.model.task.TaskBatchResult;
import com.hesihai.fuckhwcloudgrow.model.task.TaskResult;
import com.hesihai.fuckhwcloudgrow.model.task.TaskResults;
import com.hesihai.fuckhwcloudgrow.notify.XizhiNotifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 任务编排：凭据检查 → 按顺序执行各任务 → 汇总结果与推送。
 */
@Slf4j
@Service
public class TaskOrchestrator {

    private final HwcloudGrowProperties properties;
    private final AutomationLock automationLock;
    private final CredentialService credentials;
    private final AuthStateStore authStateStore;
    private final HttpAccess httpAccess;
    private final TaskResultStore resultStore;
    private final XizhiNotifier notifier;
    private final List<TaskHandler> handlers;

    public TaskOrchestrator(HwcloudGrowProperties properties, AutomationLock automationLock,
                            CredentialService credentials, AuthStateStore authStateStore,
                            HttpAccess httpAccess,
                            TaskResultStore resultStore, XizhiNotifier notifier,
                            List<TaskHandler> handlers) {
        this.properties = properties;
        this.automationLock = automationLock;
        this.credentials = credentials;
        this.authStateStore = authStateStore;
        this.httpAccess = httpAccess;
        this.resultStore = resultStore;
        this.notifier = notifier;
        this.handlers = handlers.stream()
                .sorted(Comparator.comparingInt(TaskHandler::order).thenComparing(TaskHandler::key))
                .toList();
    }

    public List<TaskHandler> handlers() {
        return handlers;
    }

    /**
     * 执行全部启用的任务。
     *
     * @throws TaskBusyException 已有批次正在执行
     */
    public TaskBatchResult runAll(RunTrigger trigger) {
        List<TaskHandler> enabled = handlers.stream().filter(TaskHandler::enabled).toList();
        return runBatch(trigger, enabled);
    }

    /**
     * 单独执行一个任务；任务不存在时返回空。
     */
    public Optional<TaskResult> runOne(String key, RunTrigger trigger) {
        TaskHandler handler = handlers.stream()
                .filter(item -> item.key().equalsIgnoreCase(key))
                .findFirst()
                .orElse(null);
        if (handler == null) {
            return Optional.empty();
        }
        if (!handler.enabled()) {
            return Optional.of(TaskResults.skipped(handler.key(), handler.displayName(), "任务已在配置中关闭"));
        }
        TaskBatchResult batch = runBatch(trigger, List.of(handler));
        return batch.getTasks().stream().findFirst();
    }

    public TaskBatchResult lastBatch() {
        return resultStore.loadLast();
    }

    private TaskBatchResult runBatch(RunTrigger trigger, List<TaskHandler> enabled) {
        if (!automationLock.tryAcquire()) {
            throw new TaskBusyException("任务批次正在执行中，请稍后重试");
        }
        try {
            Instant startedAt = Instant.now();
            String batchId = IdUtil.randomUUID();
            Instant deadline = startedAt.plus(properties.getTasks().getTotalTimeout());

            CredentialStatus credential = enabled.isEmpty()
                    ? CredentialStatus.valid(Map.of())
                    : credentials.ensureValid(requiredHosts(enabled), batchId);

            List<TaskResult> results = new ArrayList<>();
            boolean credentialUsable = credential.valid()
                    || credential.getLevel() == CredentialStatus.Level.WAF_BLOCKED;
            if (!credentialUsable) {
                for (TaskHandler handler : enabled) {
                    results.add(TaskResults.skipped(handler.key(), handler.displayName(),
                            "登录态失效，已中止：" + credential.getMessage()));
                }
            } else {
                executeAll(enabled, batchId, trigger, deadline, results);
            }

            TaskBatchResult batch = buildBatch(batchId, trigger, startedAt, credential, results,
                    resolveTotalPoints(results));
            resultStore.save(batch);
            notifier.dailySummary(batch);
            log.info("任务批次结束：{}", batchId);
            return batch;
        } finally {
            automationLock.release();
        }
    }

    private void executeAll(List<TaskHandler> enabled, String batchId, RunTrigger trigger,
                            Instant deadline, List<TaskResult> results) {
        boolean aborted = false;
        for (TaskHandler handler : enabled) {
            if (aborted) {
                results.add(TaskResults.skipped(handler.key(), handler.displayName(),
                        "登录态失效，已中止后续任务"));
                continue;
            }
            if (Instant.now().isAfter(deadline)) {
                results.add(TaskResults.skipped(handler.key(), handler.displayName(),
                        "批次执行超时，未执行"));
                continue;
            }
            TaskResult result = execute(handler, batchId, trigger, deadline);
            results.add(result);
            if (result.credentialExpired()) {
                authStateStore.invalidate();
                aborted = true;
            }
        }
    }

    private TaskResult execute(TaskHandler handler, String batchId, RunTrigger trigger, Instant deadline) {
        String runId = batchId + "-" + handler.key();
        TaskContext ctx = new TaskContext(batchId, runId, trigger, deadline, httpAccess, false);
        Instant startedAt = Instant.now();
        try {
            log.info("开始任务：{}（{}）", handler.displayName(), runId);
            TaskResult result = handler.run(ctx);
            if (result == null) {
                return TaskResults.failed(handler.key(), handler.displayName(), startedAt,
                        FailCode.UNKNOWN, "任务未返回结果", false, null);
            }
            log.info("任务结束：{} -> {}（{}）", handler.displayName(), result.getStatus(), result.getMessage());
            return result;
        } catch (Exception e) {
            log.error("任务执行异常：{}：{}", handler.displayName(), e.getMessage(), e);
            return TaskResults.failed(handler.key(), handler.displayName(), startedAt,
                    FailCode.UNKNOWN, "任务异常：" + e.getMessage(), false, null);
        }
    }

    private Set<String> requiredHosts(List<TaskHandler> enabled) {
        Set<String> hosts = new LinkedHashSet<>();
        for (TaskHandler handler : enabled) {
            hosts.addAll(handler.requiredHosts());
        }
        return hosts;
    }

    /**
     * 批次结束时的积分余额：优先实时查询成长中心，失败时回落到任务结果里记录的值。
     */
    private Integer resolveTotalPoints(List<TaskResult> results) {
        try (GrowthApi api = httpAccess.growthApi()) {
            UserInfoResponse user = api.getUserInfo();
            if (user != null && user.getTotalPoints() != null) {
                return user.getTotalPoints();
            }
        } catch (Exception e) {
            log.warn("查询批次结束积分失败，使用任务结果中的记录：{}", e.getMessage());
        }
        for (int i = results.size() - 1; i >= 0; i--) {
            Map<String, Object> detail = results.get(i).getDetail();
            if (detail == null) {
                continue;
            }
            Object value = detail.containsKey("totalPoints")
                    ? detail.get("totalPoints") : detail.get("pointsAfter");
            if (value instanceof Number number) {
                return number.intValue();
            }
        }
        return null;
    }

    private TaskBatchResult buildBatch(String batchId, RunTrigger trigger, Instant startedAt,
                                       CredentialStatus credential, List<TaskResult> results,
                                       Integer totalPoints) {
        Instant finishedAt = Instant.now();
        return TaskBatchResult.builder()
                .batchId(batchId)
                .trigger(trigger)
                .credential(credential)
                .tasks(results)
                .totalPoints(totalPoints)
                .startedAt(startedAt)
                .finishedAt(finishedAt)
                .durationMs((int) Duration.between(startedAt, finishedAt).toMillis())
                .build();
    }
}