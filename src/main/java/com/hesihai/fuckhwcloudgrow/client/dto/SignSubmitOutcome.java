package com.hesihai.fuckhwcloudgrow.client.dto;

import lombok.Builder;
import lombok.Data;

/**
 * POST /growth/sign 的结果。
 */
@Data
@Builder
public class SignSubmitOutcome {

    /**
     * 签到请求是否成功（业务码为成功）。
     */
    private boolean success;
    /**
     * 接口返回的今日签到状态：true 表示调用后今日已签到（可能是本次，也可能是之前）。
     */
    private boolean signed;
    /**
     * 厂商返回的业务错误码。
     */
    private String vendorCode;
    /**
     * 厂商返回的错误信息。
     */
    private String vendorMessage;
    /**
     * 本次签到获得的积分，已签到时可能为 null。
     */
    private Integer points;
    /**
     * 签到后的积分余额。
     */
    private Integer totalPoints;
    /**
     * 原始响应体，保留用于诊断。
     */
    private String rawBody;
}
