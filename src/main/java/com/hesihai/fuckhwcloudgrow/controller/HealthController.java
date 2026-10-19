package com.hesihai.fuckhwcloudgrow.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/health")
@Tag(name = "健康检查", description = "服务运行状态检查接口")
public class HealthController {

    @GetMapping
    @Operation(summary = "健康检查", description = "检查服务是否正常运行")
    public String healthCheck() {
        return "OK";
    }
}
