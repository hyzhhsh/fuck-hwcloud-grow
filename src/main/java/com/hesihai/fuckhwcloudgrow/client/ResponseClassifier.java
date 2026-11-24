package com.hesihai.fuckhwcloudgrow.client;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hesihai.fuckhwcloudgrow.model.FailCode;

import java.util.List;

/**
 * 统一识别华为云接口的 HTTP 状态与业务错误码。
 *
 * <p>错误分类会直接影响上层“重试 / 转浏览器 / 直接失败”的决策，
 * 因此集中在这里维护，HTTP 与 Playwright 两条路径共用。</p>
 */
public final class ResponseClassifier {

    /**
     * 按优先级遍历的厂商错误信息字段。
     */
    private static final List<String> ERROR_MESSAGE_FIELDS =
            List.of("message", "msg", "error_msg", "errorMsg");

    /**
     * 同实名多账号认定相关的错误码，统一给出“请到页面手动处理”的指引。
     */
    private static final List<String> NATURAL_PERSON_BINDING_CODES =
            List.of("20024", "20025", "20026", "20027", "20028", "20030");

    private ResponseClassifier() {
    }

    /**
     * 识别响应中的错误并按需抛出异常；判定顺序即优先级。
     *
     * <p>新接口统一返回 {@code {"code":0,"message":"...","data":...}}，
     * 业务错误码优先；之后依次判断 404 路由未匹配、401/403、429 限流、
     * 5xx 服务端错误、HTML 页面（WAF 拦截），最后才是其余 4xx。</p>
     *
     * @param status      HTTP 状态码
     * @param contentType 响应 Content-Type
     * @param body        响应体
     */
    public static void throwIfError(int status, String contentType, String body) {
        String code = extractErrorCode(body);
        String message = extractErrorMessage(body);

        if (StrUtil.isNotBlank(code)) {
            switch (code) {
                case HuaweiErrorCodes.LOGIN_EXPIRED -> throw GrowthApiException.sessionExpired("华为云登录态已失效");
                case HuaweiErrorCodes.USER_NOT_EXISTS -> throw GrowthApiException.accountAbnormal(code,
                        "当前账号不在成长中心用户体系中，请确认登录账号（" + code + "）");
                case HuaweiErrorCodes.REALNAME_REQUIRED -> throw GrowthApiException.accountAbnormal(code,
                        "账号未完成实名认证，请先在华为云账号中心完成实名认证后重试（" + code + "）");
                case HuaweiErrorCodes.NOT_FIRST_NATURAL_PERSON -> throw GrowthApiException.accountAbnormal(code,
                        "同一实名认证下已有其他参与账号，当前账号无法参与；请前往成长中心手动处理（"
                                + code + "）");
                case HuaweiErrorCodes.NATURAL_PERSON_SELECTION_REQUIRED ->
                        throw GrowthApiException.accountAbnormal(code,
                                "同实名认证下有多个账号，需先选择参与账号；请前往成长中心手动选择（"
                                        + code + "）");
                case HuaweiErrorCodes.SYSTEM_ERROR -> throw new GrowthApiException(FailCode.UNKNOWN,
                        "华为云服务繁忙，请稍后重试（" + code + "）", code, true, body);
                default -> {
                    if (NATURAL_PERSON_BINDING_CODES.contains(code)) {
                        throw GrowthApiException.accountAbnormal(code,
                                StrUtil.blankToDefault(message, "参与账号认定未完成")
                                        + "（" + code + "），请前往成长中心手动处理");
                    }
                    throw new GrowthApiException(FailCode.UNKNOWN,
                            StrUtil.isBlank(message)
                                    ? "华为云返回错误 " + code
                                    : "华为云返回错误 " + code + "：" + message,
                            code, false, body);
                }
            }
        }

        // 404 + "no route matched" 是华为云登录态失效的隐性特征，优先级高于 401/403 兜底
        if (status == 404 && StrUtil.containsIgnoreCase(body, "no route matched")) {
            throw GrowthApiException.sessionExpired("华为云接口路由未匹配，登录态可能已失效");
        }
        if (status == 401 || status == 403) {
            if (StrUtil.containsAnyIgnoreCase(body, "not login", "login expired", "未登录")) {
                throw GrowthApiException.sessionExpired("华为云登录态已失效");
            }
            if (isHtml(contentType, body)) {
                throw GrowthApiException.wafBlocked("接口返回 HTML 页面，疑似被 WAF 拦截", body);
            }
            throw GrowthApiException.sessionExpired("访问被拒绝，HTTP " + status);
        }
        if (status == 429) {
            throw new GrowthApiException(FailCode.NETWORK, "请求过于频繁，HTTP 429", null, true, body);
        }
        if (status >= 500) {
            throw new GrowthApiException(FailCode.NETWORK, "华为云服务异常，HTTP " + status, null, true, body);
        }
        if (isHtml(contentType, body)) {
            throw GrowthApiException.wafBlocked("接口返回 HTML 页面，疑似被 WAF 拦截", body);
        }
        if (status >= 400) {
            throw new GrowthApiException(FailCode.UNKNOWN, "华为云接口返回 HTTP " + status, null, false, body);
        }
    }

    /**
     * 解析新信封的 code 字段：0 视为成功返回 null，非 0 返回字符串。
     * 同时兼容旧的 error_code / errorCode 字段。
     */
    public static String extractErrorCode(String body) {
        JSONObject node = parse(body);
        if (ObjectUtil.isNull(node)) {
            return null;
        }
        Object rawCode = node.get("code");
        if (ObjectUtil.isNotNull(rawCode)) {
            String code = String.valueOf(rawCode).trim();
            if (!"null".equalsIgnoreCase(code) && !"0".equals(code)) {
                return code;
            }
            return null;
        }
        String code = node.getStr("error_code");
        return ObjectUtil.isNotNull(code) ? code : node.getStr("errorCode");
    }

    /**
     * 兼容 message / msg / error_msg / errorMsg 四种错误信息字段。
     */
    public static String extractErrorMessage(String body) {
        JSONObject node = parse(body);
        if (ObjectUtil.isNull(node)) {
            return null;
        }
        for (String field : ERROR_MESSAGE_FIELDS) {
            String value = node.getStr(field);
            if (ObjectUtil.isNotNull(value)) {
                return value;
            }
        }
        return null;
    }

    public static boolean isSuccessCode(String code) {
        return StrUtil.isBlank(code) || HuaweiErrorCodes.SUCCESS.equals(code);
    }

    /**
     * 同时检查 Content-Type 与正文首字符，覆盖 WAF 返回 HTML 的常见情况。
     */
    static boolean isHtml(String contentType, String body) {
        if (StrUtil.containsIgnoreCase(contentType, "text/html")) {
            return true;
        }
        return StrUtil.startWith(StrUtil.trim(body), "<");
    }

    public static boolean isNotLoginBody(String body) {
        return StrUtil.containsAnyIgnoreCase(body, "not login", "login expired", "未登录");
    }

    /**
     * 只解析 JSON 对象；HTML、空串和非法 JSON 都返回 null。
     */
    private static JSONObject parse(String body) {
        if (!JSONUtil.isTypeJSONObject(body)) {
            return null;
        }
        try {
            return JSONUtil.parseObj(body);
        } catch (Exception ignored) {
            return null;
        }
    }
}