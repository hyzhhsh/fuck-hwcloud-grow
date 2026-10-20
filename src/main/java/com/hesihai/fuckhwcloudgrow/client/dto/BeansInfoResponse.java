package com.hesihai.fuckhwcloudgrow.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * GET /growth/bonus-beans 的响应。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BeansInfoResponse {

    /**
     * 当前成长值（积分）余额。
     */
    private Integer beans;
}
