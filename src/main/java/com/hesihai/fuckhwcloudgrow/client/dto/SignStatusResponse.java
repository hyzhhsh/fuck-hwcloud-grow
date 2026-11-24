package com.hesihai.fuckhwcloudgrow.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * 签到状态。新接口只提供每日签到任务的完成标记，signFlag == 1 表示今日已签到。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SignStatusResponse {

    /**
     * 今日签到标记，1 表示今日已签到。
     */
    private Integer signFlag;
}