package com.hesihai.fuckhwcloudgrow.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 登录态元数据，与 Playwright storageState 分开保存。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthMeta {

    /**
     * 登录态保存时间。
     */
    private Instant savedAt;
    /**
     * 导出登录态时浏览器的 User-Agent，供 HTTP 快速路径复用，避免被风控识别为环境突变。
     */
    private String userAgent;
    /**
     * 登录账号昵称。
     */
    private String memAlias;
    /**
     * 登录账号姓名。
     */
    private String memName;
}
