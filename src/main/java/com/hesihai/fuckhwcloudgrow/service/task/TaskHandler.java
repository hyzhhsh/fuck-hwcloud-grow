package com.hesihai.fuckhwcloudgrow.service.task;

import com.hesihai.fuckhwcloudgrow.model.task.TaskResult;

import java.util.Set;

/**
 * 单个自动化任务。
 *
 * <p>任务自身只负责业务动作；登录态由凭据层保证，页面交互由声明 {@link #requiresBrowser()} 的任务自行开浏览器。</p>
 */
public interface TaskHandler {

    String key();

    String displayName();

    boolean enabled();

    /**
     * 该任务依赖的站点，供凭据层预检。
     */
    Set<String> requiredHosts();

    /**
     * 是否需要浏览器才能完成。
     */
    boolean requiresBrowser();

    /**
     * 批次内的执行顺序，数值小的先执行。
     */
    default int order() {
        return 100;
    }

    TaskResult run(TaskContext ctx);
}