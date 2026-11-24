package com.hesihai.fuckhwcloudgrow.service.task;

import com.hesihai.fuckhwcloudgrow.client.HttpAccess;
import com.hesihai.fuckhwcloudgrow.model.RunTrigger;

import java.time.Instant;

/**
 * 一次任务执行所需的上下文。
 */
public record TaskContext(String batchId, String runId, RunTrigger trigger, Instant deadline,
                          HttpAccess http, boolean notifySelf) {

    public boolean expired() {
        return Instant.now().isAfter(deadline);
    }
}