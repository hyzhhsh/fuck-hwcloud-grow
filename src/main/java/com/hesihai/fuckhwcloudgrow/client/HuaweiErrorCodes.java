package com.hesihai.fuckhwcloudgrow.client;

/**
 * 华为云成长中心接口中已确认的业务错误码。
 */
public final class HuaweiErrorCodes {

    /**
     * 操作成功。
     */
    public static final String SUCCESS = "0";
    /**
     * 未登录或登录已过期，需重新登录。
     */
    public static final String LOGIN_EXPIRED = "10001";
    /**
     * 当前用户不在成长中心用户体系中。
     */
    public static final String USER_NOT_EXISTS = "10003";
    /**
     * 账号未完成实名认证。
     */
    public static final String REALNAME_REQUIRED = "20020";
    /**
     * 同一实名认证下已有其他参与账号。
     */
    public static final String NOT_FIRST_NATURAL_PERSON = "20021";
    /**
     * 参与账号认定相关的其余错误码。
     */
    public static final String NATURAL_PERSON_SELECTION_REQUIRED = "20023";
    /**
     * 系统错误，可稍后重试。
     */
    public static final String SYSTEM_ERROR = "40004";

    private HuaweiErrorCodes() {
    }
}