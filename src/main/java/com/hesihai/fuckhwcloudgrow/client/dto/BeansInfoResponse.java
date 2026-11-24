package com.hesihai.fuckhwcloudgrow.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 积分余额，由 user/info 的 total_points 构造。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BeansInfoResponse {

    /**
     * 当前成长值（积分）余额。
     */
    private Integer beans;
}
