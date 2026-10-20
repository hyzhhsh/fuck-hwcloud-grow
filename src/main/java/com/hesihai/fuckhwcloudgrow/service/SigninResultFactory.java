package com.hesihai.fuckhwcloudgrow.service;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.hesihai.fuckhwcloudgrow.client.dto.BeansInfoResponse;
import com.hesihai.fuckhwcloudgrow.client.dto.SignStatusResponse;
import com.hesihai.fuckhwcloudgrow.model.FailCode;
import com.hesihai.fuckhwcloudgrow.model.RunTrigger;
import com.hesihai.fuckhwcloudgrow.model.SigninResult;
import com.hesihai.fuckhwcloudgrow.model.SigninStatus;

import java.time.Duration;
import java.time.Instant;

/**
 * 统一构造签到结果，避免成功/失败两条路径各写一遍 builder。
 *
 * <p>结果结构直接作为接口响应并持久化到 last-result.json，字段含义在这里集中维护。</p>
 */
public final class SigninResultFactory {

    private SigninResultFactory() {
    }

    /**
     * 构造成功结果（SUCCESS 或 ALREADY_SIGNED）。
     *
     * @param runId             任务唯一 ID
     * @param trigger           触发来源
     * @param status            最终状态，SUCCESS 或 ALREADY_SIGNED
     * @param alreadySigned     是否今日已签到
     * @param accountName       账号名
     * @param signStatus        签到状态查询结果，可为 null（部分字段留空）
     * @param before            签到前成长值，可为 null
     * @param after             签到后成长值，可为 null
     * @param message           面向用户的结果描述
     * @param apiSource         完成通道（HTTP 或 PLAYWRIGHT）
     * @param humanIntervention 是否经过人工验证
     * @param startedAt         任务开始时间
     * @return 完整签到结果
     */
    public static SigninResult success(String runId, RunTrigger trigger, SigninStatus status,
                                       boolean alreadySigned, String accountName,
                                       SignStatusResponse signStatus,
                                       BeansInfoResponse before, BeansInfoResponse after,
                                       String message, String apiSource,
                                       boolean humanIntervention, Instant startedAt) {
        Integer beforePoints = ObjectUtil.isNotNull(before) ? before.getBeans() : null;
        Integer afterPoints = ObjectUtil.isNotNull(after) ? after.getBeans() : null;
        // 积分差值只在前后两次都取到数据时才有意义
        Integer delta = ObjectUtil.isNotNull(beforePoints) && ObjectUtil.isNotNull(afterPoints)
                ? afterPoints - beforePoints
                : null;
        Instant finishedAt = Instant.now();
        return SigninResult.builder()
                .runId(runId)
                .trigger(trigger)
                .status(status)
                .success(true)
                .alreadySigned(alreadySigned)
                .accountName(accountName)
                .signFlag(ObjectUtil.isNotNull(signStatus) ? signStatus.getSignFlag() : null)
                .pointsBefore(beforePoints)
                .pointsAfter(afterPoints)
                .pointsDelta(delta)
                .continuityCount(ObjectUtil.isNotNull(signStatus)
                        ? signStatus.getSignContinuityCount() : null)
                .lastSignTime(ObjectUtil.isNotNull(signStatus) ? signStatus.getLastSignTime() : null)
                .apiSource(apiSource)
                .humanIntervention(humanIntervention)
                .message(message)
                .startedAt(startedAt)
                .finishedAt(finishedAt)
                .durationMs((int) Duration.between(startedAt, finishedAt).toMillis())
                .build();
    }

    /**
     * 构造失败结果。
     *
     * @param runId             任务唯一 ID
     * @param trigger           触发来源
     * @param startedAt         任务开始时间
     * @param code              失败分类，null 时记为 UNKNOWN
     * @param message           面向用户的结果描述，空白时兜底为“签到失败”
     * @param apiSource         失败通道（HTTP、PLAYWRIGHT 或 NONE）
     * @param humanIntervention 是否经过人工验证
     * @return 完整失败结果
     */
    public static SigninResult failure(String runId, RunTrigger trigger, Instant startedAt,
                                       FailCode code, String message, String apiSource,
                                       boolean humanIntervention) {
        Instant finishedAt = Instant.now();
        return SigninResult.builder()
                .runId(runId)
                .trigger(trigger)
                .status(SigninStatus.FAILED)
                .success(false)
                .alreadySigned(false)
                .apiSource(apiSource)
                .humanIntervention(humanIntervention)
                .errorCode(ObjectUtil.defaultIfNull(code, FailCode.UNKNOWN).name())
                .message(StrUtil.blankToDefault(message, "签到失败"))
                .startedAt(startedAt)
                .finishedAt(finishedAt)
                .durationMs((int) Duration.between(startedAt, finishedAt).toMillis())
                .build();
    }
}
