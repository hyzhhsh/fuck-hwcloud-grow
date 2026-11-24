package com.hesihai.fuckhwcloudgrow.model.task;

import com.hesihai.fuckhwcloudgrow.model.FailCode;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * 任务结果的统一构造入口，避免各 handler 重复计时与填充字段。
 */
public final class TaskResults {

    private TaskResults() {
    }

    public static TaskResult success(String key, String name, Instant startedAt, String message,
                                     Integer points, Integer pointsDelta, Map<String, Object> detail) {
        return build(key, name, TaskStatus.SUCCESS, true, message, null, "NONE", false,
                points, pointsDelta, detail, startedAt);
    }

    public static TaskResult alreadyDone(String key, String name, Instant startedAt, String message,
                                         Map<String, Object> detail) {
        return build(key, name, TaskStatus.ALREADY_DONE, true, message, null, "NONE", false,
                null, null, detail, startedAt);
    }

    public static TaskResult partial(String key, String name, Instant startedAt, String message,
                                     Integer points, Map<String, Object> detail) {
        return build(key, name, TaskStatus.PARTIAL, true, message, null, "NONE", false,
                points, null, detail, startedAt);
    }

    public static TaskResult skipped(String key, String name, String message) {
        Instant now = Instant.now();
        return build(key, name, TaskStatus.SKIPPED, false, message, null,
                "NONE", false, null, null, null, now);
    }

    public static TaskResult failed(String key, String name, Instant startedAt, FailCode code,
                                    String message, boolean humanIntervention,
                                    Map<String, Object> detail) {
        return build(key, name, TaskStatus.FAILED, false, message,
                (code == null ? FailCode.UNKNOWN : code).name(), "NONE", humanIntervention,
                null, null, detail, startedAt);
    }

    private static TaskResult build(String key, String name, TaskStatus status, boolean success,
                                    String message, String errorCode, String apiSource,
                                    boolean humanIntervention, Integer points, Integer pointsDelta,
                                    Map<String, Object> detail, Instant startedAt) {
        Instant finishedAt = Instant.now();
        return TaskResult.builder()
                .taskKey(key)
                .displayName(name)
                .status(status)
                .success(success)
                .message(message)
                .errorCode(errorCode)
                .apiSource(apiSource)
                .humanIntervention(humanIntervention)
                .points(points)
                .pointsDelta(pointsDelta)
                .detail(detail)
                .startedAt(startedAt)
                .finishedAt(finishedAt)
                .durationMs((int) Duration.between(startedAt, finishedAt).toMillis())
                .build();
    }
}