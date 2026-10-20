package com.hesihai.fuckhwcloudgrow.service;

/**
 * 已有签到任务在执行。
 */
public class SigninBusyException extends RuntimeException {

    public SigninBusyException(String message) {
        super(message);
    }
}
