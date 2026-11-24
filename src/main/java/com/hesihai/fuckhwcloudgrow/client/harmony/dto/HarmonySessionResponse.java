package com.hesihai.fuckhwcloudgrow.client.harmony.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * POST /api/growth/tasks/case-view-daily/sessions 的响应。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class HarmonySessionResponse {

    private String sessionId;
    private Integer needSeconds;
    private Long needMs;
}