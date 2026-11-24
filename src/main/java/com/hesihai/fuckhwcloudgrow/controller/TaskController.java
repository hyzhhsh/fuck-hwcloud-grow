package com.hesihai.fuckhwcloudgrow.controller;

import cn.hutool.core.util.ObjectUtil;
import com.hesihai.fuckhwcloudgrow.auth.CredentialService;
import com.hesihai.fuckhwcloudgrow.auth.CredentialStatus;
import com.hesihai.fuckhwcloudgrow.model.RunTrigger;
import com.hesihai.fuckhwcloudgrow.model.task.TaskBatchResult;
import com.hesihai.fuckhwcloudgrow.model.task.TaskOverview;
import com.hesihai.fuckhwcloudgrow.model.task.TaskResult;
import com.hesihai.fuckhwcloudgrow.model.task.TaskStatusView;
import com.hesihai.fuckhwcloudgrow.service.task.TaskBusyException;
import com.hesihai.fuckhwcloudgrow.service.task.TaskHandler;
import com.hesihai.fuckhwcloudgrow.service.task.TaskOrchestrator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 多任务自动化接口：整批/单个触发与结果查询。
 */
@RestController
@RequestMapping("/api/tasks")
@Tag(name = "成长中心每日任务", description = "签到与鸿蒙案例浏览的编排接口")
public class TaskController {

    private final TaskOrchestrator orchestrator;
    private final CredentialService credentials;

    public TaskController(TaskOrchestrator orchestrator, CredentialService credentials) {
        this.orchestrator = orchestrator;
        this.credentials = credentials;
    }

    @PostMapping("/run")
    @Operation(summary = "执行全部启用任务",
            description = "按 签到 → 鸿蒙案例 顺序执行；凭据失效时先登录一次。"
                    + "已有批次在执行时返回 409。")
    public TaskBatchResult run() {
        try {
            return orchestrator.runAll(RunTrigger.MANUAL);
        } catch (TaskBusyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    @PostMapping("/{key}/run")
    @Operation(summary = "执行单个任务", description = "key 取值：signin / harmony-case。")
    public TaskResult runOne(@PathVariable String key) {
        try {
            return orchestrator.runOne(key, RunTrigger.MANUAL)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "未知任务：" + key));
        } catch (TaskBusyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    @GetMapping("/last-batch")
    @Operation(summary = "最近一批任务结果", description = "返回最近一次批量执行的完整结果。")
    public TaskBatchResult lastBatch() {
        TaskBatchResult last = orchestrator.lastBatch();
        if (ObjectUtil.isNull(last)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "暂无任务执行结果");
        }
        return last;
    }

    @GetMapping("/status")
    @Operation(summary = "查询任务与凭据状态",
            description = "只读探测登录态，不会启动浏览器；同时返回各任务的开关与最近一次结果。")
    public TaskOverview status() {
        TaskBatchResult last = orchestrator.lastBatch();
        List<TaskStatusView> tasks = orchestrator.handlers().stream()
                .map(handler -> new TaskStatusView(handler.key(), handler.displayName(),
                        handler.enabled(), handler.requiresBrowser(), lastResult(last, handler)))
                .toList();
        Set<String> hosts = new LinkedHashSet<>();
        orchestrator.handlers().stream()
                .filter(TaskHandler::enabled)
                .forEach(handler -> hosts.addAll(handler.requiredHosts()));
        CredentialStatus credential = credentials.probe(hosts);
        return new TaskOverview(credential, tasks);
    }

    private TaskResult lastResult(TaskBatchResult last, TaskHandler handler) {
        if (ObjectUtil.isNull(last) || ObjectUtil.isNull(last.getTasks())) {
            return null;
        }
        return last.getTasks().stream()
                .filter(result -> handler.key().equalsIgnoreCase(result.getTaskKey()))
                .findFirst()
                .orElse(null);
    }
}