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
     * 是否返回“今日已签到”。
     */
    private boolean alreadySigned;
    /**
     * 厂商返回的业务错误码。
     */
    private String vendorCode;
    /**
     * 厂商返回的错误信息。
     */
    private String vendorMessage;
    /**
     * 原始响应体，保留用于诊断。
     */
    private String rawBody;
}
