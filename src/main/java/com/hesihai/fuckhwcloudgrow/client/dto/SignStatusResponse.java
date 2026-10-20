package com.hesihai.fuckhwcloudgrow.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * GET /growth/sign 的响应。sign_flag == 1 表示今日已签到。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SignStatusResponse {

    /**
     * 华为云用户 ID。
     */
    private String userId;
    /**
     * 上次签到时间（厂商格式字符串）。
     */
    private String lastSignTime;
    /**
     * 连续签到天数。
     */
    private Integer signContinuityCount;
    /**
     * 累计签到次数。
     */
    private Integer signCount;
    /**
     * 今日签到标记，1 表示今日已签到。
     */
    private Integer signFlag;
    /**
     * 签到任务状态，目前未使用。
     */
    private Boolean signMissionStatus;
    /**
     * 签到奖励，目前未使用。
     */
    private Integer bonus;
}
