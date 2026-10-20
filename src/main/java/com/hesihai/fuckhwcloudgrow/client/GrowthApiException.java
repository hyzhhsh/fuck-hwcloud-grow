package com.hesihai.fuckhwcloudgrow.client;

import com.hesihai.fuckhwcloudgrow.model.FailCode;
import lombok.Getter;

/**
 * 调用华为云接口失败。携带分类信息，供签到编排决定重试、降级或失败。
 */
@Getter
public class GrowthApiException extends RuntimeException {

    private final FailCode code;
    private final String vendorCode;
    private final boolean retryable;
    private final String rawBody;

    /**
     * 业务异常，默认不可重试。
     *
     * @param code    失败分类
     * @param message 面向用户/日志的描述
     */
    public GrowthApiException(FailCode code, String message) {
        this(code, message, null, false, null, null);
    }

    /**
     * 带原始异常的业务异常，默认不可重试。
     *
     * @param code    失败分类
     * @param message 面向用户/日志的描述
     * @param cause   原始异常
     */
    public GrowthApiException(FailCode code, String message, Throwable cause) {
        this(code, message, null, false, null, cause);
    }

    /**
     * 带厂商错误码的业务异常。
     *
     * @param code       失败分类
     * @param message    面向用户/日志的描述
     * @param vendorCode 厂商返回的业务错误码
     * @param retryable  是否值得重试
     * @param rawBody    原始响应体，用于诊断
     */
    public GrowthApiException(FailCode code, String message, String vendorCode,
                              boolean retryable, String rawBody) {
        this(code, message, vendorCode, retryable, rawBody, null);
    }

    /**
     * 信息最完整的构造器，其余构造器最终都委托到这里。
     *
     * @param code       失败分类
     * @param message    面向用户/日志的描述
     * @param vendorCode 厂商返回的业务错误码
     * @param retryable  是否值得重试
     * @param rawBody    原始响应体，用于诊断
     * @param cause      原始异常
     */
    public GrowthApiException(FailCode code, String message, String vendorCode,
                              boolean retryable, String rawBody, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.vendorCode = vendorCode;
        this.retryable = retryable;
        this.rawBody = rawBody;
    }

    /**
     * 登录态失效异常。
     *
     * @param message 描述
     * @return 分类为 {@link FailCode#SESSION_EXPIRED} 的异常
     */
    public static GrowthApiException sessionExpired(String message) {
        return new GrowthApiException(FailCode.SESSION_EXPIRED, message);
    }

    /**
     * WAF 拦截异常。
     *
     * @param message 描述
     * @param rawBody 原始响应体，用于诊断
     * @return 分类为 {@link FailCode#WAF_BLOCKED} 的异常
     */
    public static GrowthApiException wafBlocked(String message, String rawBody) {
        return new GrowthApiException(FailCode.WAF_BLOCKED, message, null, false, rawBody);
    }

    /**
     * 网络异常。
     *
     * @param message 描述
     * @param cause   原始异常，可为 null
     * @return 分类为 {@link FailCode#NETWORK} 的可重试异常
     */
    public static GrowthApiException network(String message, Throwable cause) {
        return new GrowthApiException(FailCode.NETWORK, message, null, true, null, cause);
    }

    /**
     * 签到人数较多异常。
     *
     * @param message 描述
     * @return 分类为 {@link FailCode#SIGN_BUSY} 的可重试异常
     */
    public static GrowthApiException signBusy(String message) {
        return new GrowthApiException(FailCode.SIGN_BUSY, message, null, true, null);
    }

    /**
     * 账号异常异常。
     *
     * @param vendorCode 厂商错误码
     * @param message    描述
     * @return 分类为 {@link FailCode#ACCOUNT_ABNORMAL} 的异常
     */
    public static GrowthApiException accountAbnormal(String vendorCode, String message) {
        return new GrowthApiException(FailCode.ACCOUNT_ABNORMAL, message, vendorCode, false, null);
    }

    /**
     * 是否值得启动浏览器降级重试。
     *
     * <p>只有登录态失效、被 WAF 拦截、网络错误三类问题浏览器路径可能解决，
     * 其余（如账号异常）降级没有意义。</p>
     *
     * @return true 表示应转入浏览器路径
     */
    public boolean shouldFallbackToBrowser() {
        return code == FailCode.SESSION_EXPIRED
                || code == FailCode.WAF_BLOCKED
                || code == FailCode.NETWORK;
    }
}
