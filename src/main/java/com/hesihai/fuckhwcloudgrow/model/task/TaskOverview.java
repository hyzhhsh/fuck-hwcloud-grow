package com.hesihai.fuckhwcloudgrow.model.task;

import com.hesihai.fuckhwcloudgrow.auth.CredentialStatus;

import java.util.List;

/**
 * 任务总览：只读凭据探测结果 + 各任务开关与最近结果。
 */
public record TaskOverview(CredentialStatus credential, List<TaskStatusView> tasks) {
}