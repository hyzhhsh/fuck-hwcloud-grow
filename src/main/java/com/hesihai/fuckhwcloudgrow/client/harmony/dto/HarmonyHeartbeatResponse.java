package com.hesihai.fuckhwcloudgrow.client.harmony.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * POST /api/growth/sessions/{sessionId}/heartbeat 的响应。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class HarmonyHeartbeatResponse {

    private Long accumulatedMs;
    private Long needMs;
    private Boolean completed;
    private String sessionId;
    private Boolean alreadyAwardedToday;
    private Integer points;
    private String awardStatus;
}