package com.hesihai.fuckhwcloudgrow.client;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 华为云成长中心相关固定地址。集中维护，避免散落在各处。
 */
public final class HuaweiUrls {

    /**
     * 成长中心首页。
     */
    public static final String GROW_URL = "https://developer.huaweicloud.com/grow";
    /**
     * 成长中心站点源（Origin），用于请求头。
     */
    public static final String GROW_ORIGIN = "https://developer.huaweicloud.com";
    // URLUtil.encode 会保留 :// 等 URL 安全字符，这里作为 query 参数值需要完整编码
    /**
     * 登录页地址，service 参数指回成长中心。
     */
    public static final String LOGIN_URL = "https://auth.huaweicloud.com/authui/login?service="
            + URLEncoder.encode(GROW_URL, StandardCharsets.UTF_8)
            + "&locale=zh-cn";
    /**
     * 数据接口根地址。
     */
    public static final String API_BASE = "https://devdata.huaweicloud.com";
    /**
     * 成长中心主域（不含协议），用于页面 URL 归属判断。
     */
    public static final String DEVELOPER_DOMAIN = "developer.huaweicloud.com";
    /**
     * 华为云统一认证域（不含协议），用于识别登录/认证页面。
     */
    public static final String AUTH_DOMAIN = "auth.huaweicloud.com";
    /**
     * 成长中心数据接口域（不含协议），用于 Cookie 域名过滤。
     */
    public static final String DEVDATA_DOMAIN = "devdata.huaweicloud.com";
    /**
     * csrf token 获取接口。
     */
    public static final String CSRF_PATH = "/api/get-ainfo";
    /**
     * 签到状态查询 / 提交签到接口。
     */
    public static final String SIGN_PATH =
            "/rest/developer/fwdu/rest/developer/user/hdcommunitysoservice/v1/growth/sign";
    /**
     * 成长值查询接口。
     */
    public static final String BEANS_PATH =
            "/rest/developer/fwdu/rest/developer/user/hdcommunitysoservice/v1/growth/bonus-beans";
    /**
     * 个人资料查询接口。
     */
    public static final String PERSONAL_INFO_PATH =
            "/rest/developer/fwdu/rest/developer/user/hdcommunityservice/v1/member/get-personal-info";

    private HuaweiUrls() {
    }
}
