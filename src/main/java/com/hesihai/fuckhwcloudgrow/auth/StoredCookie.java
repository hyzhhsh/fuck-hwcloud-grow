package com.hesihai.fuckhwcloudgrow.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * Playwright storageState 中的 Cookie 结构（camelCase）。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class StoredCookie {

    /**
     * Cookie 名称。
     */
    private String name;
    /**
     * Cookie 值。
     */
    private String value;
    /**
     * Cookie 所属域名（可带前导点，如 .huaweicloud.com）。
     */
    private String domain;
    /**
     * Cookie 路径。
     */
    private String path;
    /**
     * 过期时间（秒级 Unix 时间戳），-1 表示 Playwright 会话 Cookie（浏览器关闭即失效）。
     */
    private Double expires;
    /**
     * 是否仅 HTTP 传输。
     */
    private Boolean httpOnly;
    /**
     * 是否仅 HTTPS 传输。
     */
    private Boolean secure;
    /**
     * SameSite 属性。
     */
    private String sameSite;
}
