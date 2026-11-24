package com.hesihai.fuckhwcloudgrow.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hesihai.fuckhwcloudgrow.auth.AuthMeta;
import com.hesihai.fuckhwcloudgrow.auth.AuthStateStore;
import com.hesihai.fuckhwcloudgrow.auth.StoredCookie;
import com.hesihai.fuckhwcloudgrow.client.harmony.HarmonyClient;
import com.hesihai.fuckhwcloudgrow.client.http.CookieJar;
import com.hesihai.fuckhwcloudgrow.client.http.HttpGrowthApi;
import com.hesihai.fuckhwcloudgrow.config.HwcloudGrowProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 任务层统一的 HTTP 访问入口：集中加载登录态并构造各站点的客户端。
 */
@Slf4j
@Component
public class HttpAccess {

    private final HwcloudGrowProperties properties;
    private final AuthStateStore authStateStore;
    private final ObjectMapper objectMapper;

    public HttpAccess(HwcloudGrowProperties properties, AuthStateStore authStateStore,
                      ObjectMapper objectMapper) {
        this.properties = properties;
        this.authStateStore = authStateStore;
        this.objectMapper = objectMapper;
    }

    public List<StoredCookie> cookies() {
        return authStateStore.loadCookiesFromFile();
    }

    public String userAgent() {
        AuthMeta meta = authStateStore.loadMetaFromFile();
        return meta == null ? null : meta.getUserAgent();
    }

    public CookieJar cookieJar() {
        return new CookieJar(cookies());
    }

    public HttpGrowthApi growthApi() {
        return new HttpGrowthApi(properties, cookieJar(), userAgent(), objectMapper);
    }

    public HarmonyClient harmonyClient() {
        return new HarmonyClient(properties, cookieJar(), userAgent(), objectMapper);
    }
}