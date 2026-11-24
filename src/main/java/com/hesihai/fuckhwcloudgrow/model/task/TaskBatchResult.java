package com.hesihai.fuckhwcloudgrow.model.task;

import com.hesihai.fuckhwcloudgrow.auth.CredentialStatus;
import com.hesihai.fuckhwcloudgrow.model.RunTrigger;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

/**
 * 一次批量执行（凭据检查 + 多个任务）的汇总结果。
 */
@Data
@Builder
public class TaskBatchResult {

    private String batchId;
    private RunTrigger trigger;
    private CredentialStatus credential;
    private List<TaskResult> tasks;
    /**
     * 批次结束时查询到的成长值余额，查询失败时为 null。
     */
    private Integer totalPoints;
    private Instant startedAt;
    private Instant finishedAt;
    private Integer durationMs;
}