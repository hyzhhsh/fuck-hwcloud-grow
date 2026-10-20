package com.hesihai.fuckhwcloudgrow.model;

/**
 * 签到任务的最终状态。
 */
public enum SigninStatus {
    /**
     * 本次运行签到成功。
     */
    SUCCESS,
    /**
     * 今日已签到，本次运行未重复签到。
     */
    ALREADY_SIGNED,
    /**
     * 签到失败。
     */
    FAILED
}
