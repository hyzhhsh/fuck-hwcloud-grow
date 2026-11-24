package com.hesihai.fuckhwcloudgrow.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * POST /user/sign-in 的 data 部分。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SignInResponse {

    /**
     * true 表示调用前今日已签到；false 表示本次签到成功。
     */
    private Boolean signed;
    /**
     * 本次签到获得的积分。
     */
    private Integer points;
    /**
     * 签到后的积分余额。
     */
    private Integer totalPoints;
}