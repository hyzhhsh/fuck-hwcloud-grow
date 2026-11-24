package com.hesihai.fuckhwcloudgrow.service.task.handler;

import com.hesihai.fuckhwcloudgrow.client.HuaweiUrls;
import com.hesihai.fuckhwcloudgrow.config.HwcloudGrowProperties;
import com.hesihai.fuckhwcloudgrow.model.FailCode;
import com.hesihai.fuckhwcloudgrow.model.SigninResult;
import com.hesihai.fuckhwcloudgrow.model.SigninStatus;
import com.hesihai.fuckhwcloudgrow.model.task.TaskResult;
import com.hesihai.fuckhwcloudgrow.model.task.TaskResults;
import com.hesihai.fuckhwcloudgrow.service.SigninService;
import com.hesihai.fuckhwcloudgrow.service.task.TaskContext;
import com.hesihai.fuckhwcloudgrow.service.task.TaskHandler;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 每日签到任务：复用既有 {@link SigninService} 的 HTTP 签到流程。
 */
@Component
public class SigninTaskHandler implements TaskHandler {

    public static final String KEY = "signin";

    private final HwcloudGrowProperties properties;
    private final SigninService signinService;

    public SigninTaskHandler(HwcloudGrowProperties properties, SigninService signinService) {
        this.properties = properties;
        this.signinService = signinService;
    }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public String displayName() {
        return "每日签到";
    }

    @Override
    public boolean enabled() {
        return properties.getTasks().getSignin().isEnabled();
    }

    @Override
    public Set<String> requiredHosts() {
        return Set.of(HuaweiUrls.API_HOST);
    }

    @Override
    public boolean requiresBrowser() {
        return false;
    }

    @Override
    public int order() {
        return 10;
    }

    @Override
    public TaskResult run(TaskContext ctx) {
        Instant startedAt = Instant.now();
        SigninResult result = signinService.run(ctx.trigger(), ctx);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("accountName", result.getAccountName());
        detail.put("signFlag", result.getSignFlag());
        detail.put("pointsBefore", result.getPointsBefore());
        detail.put("pointsAfter", result.getPointsAfter());
        detail.put("apiSource", result.getApiSource());

        TaskResult taskResult = switch (result.getStatus()) {
            case SUCCESS -> TaskResults.success(KEY, displayName(), startedAt, result.getMessage(),
                    result.getPointsAfter(), result.getPointsDelta(), detail);
            case ALREADY_SIGNED -> TaskResults.alreadyDone(KEY, displayName(), startedAt,
                    result.getMessage(), detail);
            default -> TaskResults.failed(KEY, displayName(), startedAt,
                    parseCode(result.getErrorCode()), result.getMessage(),
                    result.isHumanIntervention(), detail);
        };
        taskResult.setApiSource(result.getApiSource() == null ? "NONE" : result.getApiSource());
        taskResult.setHumanIntervention(result.isHumanIntervention());
        return taskResult;
    }

    private FailCode parseCode(String code) {
        if (code == null) {
            return FailCode.UNKNOWN;
        }
        try {
            return FailCode.valueOf(code);
        } catch (IllegalArgumentException e) {
            return FailCode.UNKNOWN;
        }
    }
}