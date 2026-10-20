package com.hesihai.fuckhwcloudgrow.model;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * 一次签到任务的最终结果，直接作为手动接口响应并持久化到 last-result.json。
 */
@Data
@Builder
public class SigninResult {

    /**
     * 本次任务唯一 ID，用于日志串联与通知去重。
     */
    private String runId;
    /**
     * 触发来源：定时任务或手动接口。
     */
    private RunTrigger trigger;
    /**
     * 最终状态。
     */
    private SigninStatus status;
    /**
     * 是否成功（SUCCESS 或 ALREADY_SIGNED）。
     */
    private boolean success;
    /**
     * 是否今日已签到。
     */
    private boolean alreadySigned;
    /**
     * 展示用账号名。
     */
    private String accountName;
    /**
     * 查询到的今日签到标记，1 表示已签到。
     */
    private Integer signFlag;
    /**
     * 签到前成长值。
     */
    private Integer pointsBefore;
    /**
     * 签到后成长值。
     */
    private Integer pointsAfter;
    /**
     * 成长值变化量，前后均取到值时才非空。
     */
    private Integer pointsDelta;
    /**
     * 连续签到天数。
     */
    private Integer continuityCount;
    /**
     * 上次签到时间（厂商格式字符串）。
     */
    private String lastSignTime;
    /**
     * 实际完成签到的通道：HTTP 或 PLAYWRIGHT。
     */
    private String apiSource;
    /**
     * 本次是否经过人工验证/人工介入。
     */
    private boolean humanIntervention;
    /**
     * 面向用户的结果描述。
     */
    private String message;
    /**
     * 失败时的错误码。
     */
    private String errorCode;
    /**
     * 任务开始时间。
     */
    private Instant startedAt;
    /**
     * 任务结束时间。
     */
    private Instant finishedAt;
    /**
     * 任务耗时（毫秒）。
     */
    private Integer durationMs;
}
