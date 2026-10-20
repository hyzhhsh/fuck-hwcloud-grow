package com.hesihai.fuckhwcloudgrow.login;

import java.util.List;

/**
 * 登录页选择器与文案。页面改版时只需要调整这里。
 */
public final class LoginSelectors {

    public static final List<String> PERSONAL_ACCOUNT_INPUTS = List.of(
            "input[name='userAccount']",
            "input.hwid-input.userAccount",
            "#personalAccountInputId input",
            "#personalAccountInputId");
    public static final List<String> PERSONAL_PASSWORD_INPUTS = List.of(
            "input.hwid-input-pwd",
            "#personalPasswordInputId input",
            "#personalPasswordInputId");
    public static final List<String> LOGIN_BUTTONS = List.of(
            ".hwid-pwdlogin-root .hwid-btn.hwid-btn-primary",
            ".hwid-pwdlogin-root .hwid-login-btn-wrap .hwid-btn",
            "#loginBtn",
            "#btn_submit");
    public static final List<String> PASSWORD_TAB_SELECTORS = List.of(
            "#pwdLogin");
    public static final List<String> HUAWEI_CLOUD_ACCOUNT_SELECTORS = List.of(
            "#originalLoginDiv");
    public static final List<String> ERROR_SELECTORS = List.of(
            "#loginServerErrorMsg",
            ".errorInfo",
            ".error-info",
            ".hwid-pwdlogin-root .hwid-errorMsg-root",
            ".pwdlogin-error");
    public static final List<String> LOGGED_IN_TEXTS = List.of(
            "我的积分",
            "积分明细");
    public static final List<String> LOGGED_OUT_TEXTS = List.of(
            "登录/注册查看您的积分");
    public static final List<String> ERROR_TEXTS = List.of(
            "账号或密码错误",
            "用户名或密码错误",
            "帐号或密码错误",
            "账户名或密码错误",
            "用户名或密码不正确",
            "密码错误",
            "账号已被锁定",
            "账号异常",
            "当前用户不存在",
            "操作过于频繁");
    public static final List<String> CHALLENGE_TEXTS = List.of(
            "身份验证",
            "登录保护",
            "两步验证",
            "验证方式",
            "安全验证",
            "手机验证",
            "邮箱验证",
            "身份验证应用程序",
            "安全密钥",
            "MFA");
    public static final List<String> CHALLENGE_SELECTORS = List.of(
            "#checkCodeId",
            "#validresultImg",
            "#inputVerifyCodeImg",
            "#MFAQRCodeCanvas",
            "input[autocomplete='one-time-code']",
            "iframe[src*='captcha']",
            ".cbchwcap");

    private LoginSelectors() {
    }
}
