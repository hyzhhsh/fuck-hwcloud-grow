package com.hesihai.fuckhwcloudgrow.service.task;

import com.hesihai.fuckhwcloudgrow.client.browser.BrowserSession;
import com.hesihai.fuckhwcloudgrow.config.HwcloudGrowProperties;
import org.springframework.stereotype.Component;

/**
 * 短生命周期浏览器会话工厂：每次打开一个会话，由调用方负责关闭。
 */
@Component
public class BrowserProvider {

    private final HwcloudGrowProperties properties;

    public BrowserProvider(HwcloudGrowProperties properties) {
        this.properties = properties;
    }

    public BrowserSession open() {
        return BrowserSession.launch(properties);
    }
}