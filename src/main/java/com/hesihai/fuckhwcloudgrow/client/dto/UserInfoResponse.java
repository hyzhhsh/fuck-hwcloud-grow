package com.hesihai.fuckhwcloudgrow.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * GET /user/info 的 data 部分。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserInfoResponse {

    /**
     * 华为云 IAM 用户 ID。
     */
    private String iamId;
    /**
     * 华为云账号 ID。
     */
    private String domainId;
    /**
     * 华为云账号名。
     */
    private String domainName;
    /**
     * 当前成长中心积分余额。
     */
    private Integer totalPoints;
}