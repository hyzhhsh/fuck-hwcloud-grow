package com.hesihai.fuckhwcloudgrow.client;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 华为云成长中心相关固定地址。集中维护，避免散落在各处。
 */
public final class HuaweiUrls {

    /**
     * 成长中心站点源（Origin），用于请求头与登录 service 参数。
     */
    public static final String GROW_ORIGIN = "https://grow.developer.huaweicloud.com";
    /**
     * 成长中心首页。
     */
    public static final String GROW_URL = GROW_ORIGIN + "/";
    /**
     * 成长中心主机名（不含协议），用于判断页面是否处于成长中心站点。
     */
    public static final String GROW_HOST = "grow.developer.huaweicloud.com";
    /**
     * 登录页地址，service 参数指回成长中心。
     */
    public static final String LOGIN_URL = "https://auth.huaweicloud.com/authui/login.html?service="
            + URLEncoder.encode(GROW_ORIGIN, StandardCharsets.UTF_8)
            + "&locale=zh-cn";
    /**
     * 数据接口根地址。
     */
    public static final String API_BASE = "https://grow-dataset.developer.huaweicloud.com";
    /**
     * 数据接口主机名：接口请求必须打到该域，前端域对 /api/** 会返回 SPA HTML。
     */
    public static final String API_HOST = "grow-dataset.developer.huaweicloud.com";
    /**
     * 成长中心主域（不含协议），用于页面 URL 归属判断。
     */
    public static final String DEVELOPER_DOMAIN = "developer.huaweicloud.com";
    /**
     * 华为云统一认证域（不含协议），用于识别登录/认证页面。
     */
    public static final String AUTH_DOMAIN = "auth.huaweicloud.com";
    /**
     * 成长接口统一前缀。
     */
    public static final String API_PREFIX = "/api/v1/hdgrowservice";
    /**
     * 当前登录用户信息（登录探测复用）。
     */
    public static final String USER_INFO_PATH = API_PREFIX + "/user/info";
    /**
     * 每日签到接口。
     */
    public static final String SIGN_IN_PATH = API_PREFIX + "/user/sign-in";
    /**
     * 任务列表接口，用于读取各每日任务的完成状态。
     */
    public static final String USER_TASKS_PATH = API_PREFIX + "/user/tasks";
    /**
     * 每日签到任务的 task_tag。
     */
    public static final String DAILY_SIGN_TASK_TAG = "daily_login";

    private HuaweiUrls() {
    }
}