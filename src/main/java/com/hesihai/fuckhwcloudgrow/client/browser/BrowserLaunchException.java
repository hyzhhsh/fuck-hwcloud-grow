package com.hesihai.fuckhwcloudgrow.client.browser;

/**
 * 启动或连接浏览器失败。
 */
public class BrowserLaunchException extends RuntimeException {

    public BrowserLaunchException(String message, Throwable cause) {
        super(message, cause);
    }
}
