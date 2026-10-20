package com.hesihai.fuckhwcloudgrow.model;

/**
 * 签到失败原因，用于结果记录、日志与通知分类。
 */
public enum FailCode {
    /**
     * 账号或密码未配置，无法发起登录。
     */
    CONFIG_MISSING,
    /**
     * 登录态已失效，可清除本地登录态并转入浏览器重新登录。
     */
    SESSION_EXPIRED,
    /**
     * 请求被 WAF 拦截（返回 HTML 页面），可转入浏览器绕行。
     */
    WAF_BLOCKED,
    /**
     * 网络或服务端错误，可退避重试或降级。
     */
    NETWORK,
    /**
     * 签到人数较多被限流，可退避重试。
     */
    SIGN_BUSY,
    /**
     * 账号密码错误或账号被锁定。
     */
    CREDENTIAL_INVALID,
    /**
     * 等待人工验证超时。
     */
    HUMAN_TIMEOUT,
    /**
     * 华为云判定账号异常，重试无意义。
     */
    ACCOUNT_ABNORMAL,
    /**
     * 浏览器登录流程失败（含浏览器启动失败）。
     */
    LOGIN_FAILED,
    /**
     * 无法归类的兜底失败原因。
     */
    UNKNOWN
}
