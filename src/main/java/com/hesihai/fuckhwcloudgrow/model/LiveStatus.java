package com.hesihai.fuckhwcloudgrow.model;

import lombok.Builder;
import lombok.Data;

/**
 * 只读查询的当前登录态与签到状态。
 */
@Data
@Builder
public class LiveStatus {

    /**
     * 本地登录态是否有效。
     */
    private boolean sessionValid;
    /**
     * 账号名，登录态无效时为空。
     */
    private String accountName;
    /**
     * 今日签到标记，1 表示已签到。
     */
    private Integer signFlag;
    /**
     * 今日是否已签到。
     */
    private boolean signedToday;
    /**
     * 连续签到天数。
     */
    private Integer continuityCount;
    /**
     * 上次签到时间（厂商格式字符串）。
     */
    private String lastSignTime;
    /**
     * 当前成长值。
     */
    private Integer beans;
    /**
     * 面向用户的状态描述。
     */
    private String message;
}
