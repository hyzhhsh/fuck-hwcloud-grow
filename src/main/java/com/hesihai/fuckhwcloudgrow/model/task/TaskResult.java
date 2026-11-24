package com.hesihai.fuckhwcloudgrow.model.task;

import com.hesihai.fuckhwcloudgrow.model.FailCode;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Map;

/**
 * 单个自动化任务的执行结果。
 */
@Data
@Builder
public class TaskResult {

    private String taskKey;
    private String displayName;
    private TaskStatus status;
    private boolean success;
    private String message;
    private String errorCode;
    private String apiSource;
    private boolean humanIntervention;
    private Integer points;
    private Integer pointsDelta;
    private Map<String, Object> detail;
    private Instant startedAt;
    private Instant finishedAt;
    private Integer durationMs;

    /**
     * 是否由登录态失效导致，编排器据此中止批次剩余任务。
     */
    public boolean credentialExpired() {
        return FailCode.SESSION_EXPIRED.name().equals(errorCode);
    }
}