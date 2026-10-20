package com.hesihai.fuckhwcloudgrow.login;

import com.hesihai.fuckhwcloudgrow.model.FailCode;

/**
 * 登录结果。
 *
 * @param success           是否登录成功
 * @param failCode          失败原因，成功时为 null
 * @param message           面向用户/日志的描述
 * @param humanIntervention 登录过程中是否经过人工验证
 */
public record LoginOutcome(boolean success, FailCode failCode, String message,
                           boolean humanIntervention) {

    /**
     * @return 无需人工介入的登录成功结果
     */
    public static LoginOutcome ok() {
        return new LoginOutcome(true, null, "登录成功", false);
    }

    /**
     * @return 经过人工验证后登录成功的结果
     */
    public static LoginOutcome okAfterHuman() {
        return new LoginOutcome(true, null, "人工验证后登录成功", true);
    }

    /**
     * 登录失败结果。
     *
     * @param failCode 失败原因
     * @param message  描述
     * @return 未经过人工验证的失败结果
     */
    public static LoginOutcome failure(FailCode failCode, String message) {
        return new LoginOutcome(false, failCode, message, false);
    }

    /**
     * 人工验证后仍失败的登录结果。
     *
     * @param failCode 失败原因
     * @param message  描述
     * @return 经过人工验证的失败结果
     */
    public static LoginOutcome failureAfterHuman(FailCode failCode, String message) {
        return new LoginOutcome(false, failCode, message, true);
    }
}
