package com.hesihai.fuckhwcloudgrow.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 成长中心个人资料中签到需要的字段。接口返回的是 camelCase，需要显式覆盖全局 snake_case 策略。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MemberInfoResponse {

    /**
     * 用户昵称，签到结果中的账号名优先取此值。
     */
    @JsonProperty("memAlias")
    private String memAlias;

    /**
     * 用户姓名，昵称为空时的备选账号名。
     */
    @JsonProperty("memName")
    private String memName;

    /**
     * 华为云用户 ID。
     */
    @JsonProperty("userId")
    private String userId;
}
