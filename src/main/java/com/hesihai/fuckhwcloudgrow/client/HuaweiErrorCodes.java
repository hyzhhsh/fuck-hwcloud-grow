package com.hesihai.fuckhwcloudgrow.client;

/**
 * 华为云接口中已确认的业务错误码。
 */
public final class HuaweiErrorCodes {

    /**
     * 登录态失效，需重新登录。
     */
    public static final String LOGIN_EXPIRED = "92212002";
    /**
     * 操作成功。
     */
    public static final String SUCCESS = "HD.00000000";
    /**
     * 操作成功（另一接口族使用的等价码）。
     */
    public static final String SUCCESS_ALT = "HD.67500000";
    /**
     * 今日已签到。
     */
    public static final String ALREADY_SIGNED = "HD.67520093";
    /**
     * 当前签到人数较多，稍后可重试。
     */
    public static final String SIGN_BUSY = "HD.67520109";
    /**
     * 账号异常，暂不支持此操作。
     */
    public static final String ACCOUNT_ABNORMAL = "HD.67520011";
    /**
     * 账号异常（另一接口族使用的等价码）。
     */
    public static final String ACCOUNT_ABNORMAL_ALT = "HD.35011005";

    private HuaweiErrorCodes() {
    }
}
