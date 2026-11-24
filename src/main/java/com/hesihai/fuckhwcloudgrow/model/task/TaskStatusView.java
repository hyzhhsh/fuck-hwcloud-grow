package com.hesihai.fuckhwcloudgrow.model.task;

/**
 * 任务清单中的单项状态（只读接口返回）。
 */
public record TaskStatusView(String key, String displayName, boolean enabled,
                             boolean requiresBrowser, TaskResult lastResult) {
}