package com.hesihai.fuckhwcloudgrow.model.task;

/**
 * 单个自动化任务的最终状态。
 */
public enum TaskStatus {
    /**
     * 本次实际完成了任务动作并已入账。
     */
    SUCCESS,
    /**
     * 本周期已由平台记录完成，本次未重复执行。
     */
    ALREADY_DONE,
    /**
     * 动作已执行，但平台完成状态暂未登记（通常为延迟）。
     */
    PARTIAL,
    /**
     * 需要人工在页面上完成。
     */
    NEED_HUMAN,
    /**
     * 因凭据失效或批次中止而未执行。
     */
    SKIPPED,
    /**
     * 执行失败。
     */
    FAILED;

    public boolean isSuccess() {
        return this == SUCCESS || this == ALREADY_DONE;
    }
}