package com.hesihai.fuckhwcloudgrow.client.harmony;

/**
 * 鸿蒙开发实践中心（harmonybp）相关接口地址。
 */
public final class HarmonyUrls {

    /**
     * 鸿蒙实践中心站点源。
     */
    public static final String HARMONY_ORIGIN = "https://harmonybp.developer.huaweicloud.com";
    /**
     * 鸿蒙实践中心主机名，用于 Cookie 过滤与来源判断。
     */
    public static final String HARMONY_HOST = "harmonybp.developer.huaweicloud.com";
    /**
     * 每日浏览案例任务的 task_key。
     */
    public static final String DAILY_CASE_TASK_KEY = "case-view-daily";
    /**
     * 查询每日浏览案例任务配置与当日状态；匿名可访问，靠 logged_in 判断登录态。
     */
    public static final String DAILY_CASE_TASK_PATH = "/api/growth/tasks/" + DAILY_CASE_TASK_KEY;
    /**
     * 案例列表。
     */
    public static final String CASES_PATH = "/api/cases";
    /**
     * 开启一次浏览计时会话。
     */
    public static final String DAILY_CASE_SESSIONS_PATH =
            "/api/growth/tasks/" + DAILY_CASE_TASK_KEY + "/sessions";
    /**
     * 心跳上报路径模板，按线程安全的方式拼接 sessionId。
     */
    public static final String HEARTBEAT_PATH_TEMPLATE = "/api/growth/sessions/%s/heartbeat";

    private HarmonyUrls() {
    }
}