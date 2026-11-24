package com.hesihai.fuckhwcloudgrow.service.task;

/**
 * 已有任务批次正在执行。
 */
public class TaskBusyException extends RuntimeException {

    public TaskBusyException(String message) {
        super(message);
    }
}