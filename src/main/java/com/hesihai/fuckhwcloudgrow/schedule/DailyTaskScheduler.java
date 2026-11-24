package com.hesihai.fuckhwcloudgrow.schedule;

import com.hesihai.fuckhwcloudgrow.model.RunTrigger;
import com.hesihai.fuckhwcloudgrow.model.task.TaskBatchResult;
import com.hesihai.fuckhwcloudgrow.service.task.TaskBusyException;
import com.hesihai.fuckhwcloudgrow.service.task.TaskOrchestrator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 每日定时执行全部启用的任务。
 */
@Slf4j
@Component
public class DailyTaskScheduler {

    private final TaskOrchestrator orchestrator;

    public DailyTaskScheduler(TaskOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    /**
     * cron 与 zone 沿用 {@code hwcloud.grow.cron}/{@code hwcloud.grow.zone}；
     * 已有批次在执行时跳过本次，不重复排队。
     */
    @Scheduled(cron = "${hwcloud.grow.cron:0 0 8 * * *}",
            zone = "${hwcloud.grow.zone:Asia/Shanghai}")
    public void runDaily() {
        log.info("定时任务批次开始");
        try {
            TaskBatchResult batch = orchestrator.runAll(RunTrigger.SCHEDULED);
            log.info("定时任务批次结束：{} 个任务", batch.getTasks() == null ? 0 : batch.getTasks().size());
        } catch (TaskBusyException e) {
            log.warn("已有任务批次在执行，跳过本次定时任务");
        } catch (Exception e) {
            log.error("定时任务批次异常：{}", e.getMessage(), e);
        }
    }
}