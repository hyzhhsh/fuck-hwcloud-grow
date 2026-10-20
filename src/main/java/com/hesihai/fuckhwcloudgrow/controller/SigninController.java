package com.hesihai.fuckhwcloudgrow.controller;

import cn.hutool.core.util.ObjectUtil;
import com.hesihai.fuckhwcloudgrow.model.LiveStatus;
import com.hesihai.fuckhwcloudgrow.model.RunTrigger;
import com.hesihai.fuckhwcloudgrow.model.SigninResult;
import com.hesihai.fuckhwcloudgrow.service.SigninBusyException;
import com.hesihai.fuckhwcloudgrow.service.SigninService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 签到任务手动触发与状态查询接口。
 *
 * <p>接口未做鉴权，仅适合内网或本机暴露；接口语义以各方法的 {@link Operation} 描述为准。</p>
 */
@RestController
@RequestMapping("/api/signin")
@Tag(name = "成长中心签到", description = "华为云开发者成长中心每日签到接口")
public class SigninController {

    private final SigninService signinService;

    public SigninController(SigninService signinService) {
        this.signinService = signinService;
    }

    @PostMapping("/run")
    @Operation(summary = "执行签到",
            description = "同步执行签到任务。凭据失效时会启动有头浏览器；遇到验证码/两步验证时等待人工处理，"
                    + "等待时长由 hwcloud.grow.browser.human-wait 配置。"
                    + "该接口未做鉴权，请仅在内网或本机暴露。")
    public SigninResult run() {
        try {
            return signinService.run(RunTrigger.MANUAL);
        } catch (SigninBusyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    @GetMapping("/status")
    @Operation(summary = "查询当前签到状态",
            description = "使用本地 Cookie 只读查询登录态、今日签到状态与积分，不会启动浏览器。")
    public LiveStatus status() {
        return signinService.queryStatus();
    }

    @GetMapping("/last-result")
    @Operation(summary = "最近一次签到结果", description = "返回最近一次签到任务的最终结果。")
    public SigninResult lastResult() {
        SigninResult last = signinService.lastResult();
        if (ObjectUtil.isNull(last)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "暂无签到执行结果");
        }
        return last;
    }
}
