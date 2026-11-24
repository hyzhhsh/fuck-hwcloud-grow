package com.hesihai.fuckhwcloudgrow.model;

/**
 * 任务触发来源。
 */
public enum RunTrigger {
    /**
     * 由 {@code DailyTaskScheduler} 定时任务触发。
     */
    SCHEDULED,
    /**
     * 由 HTTP 接口手动触发。
     */
    MANUAL
}
