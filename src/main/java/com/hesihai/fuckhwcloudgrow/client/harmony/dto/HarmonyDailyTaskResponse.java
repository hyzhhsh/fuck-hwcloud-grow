package com.hesihai.fuckhwcloudgrow.client.harmony.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * GET /api/growth/tasks/case-view-daily 的响应，非成长中心的统一信封结构。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class HarmonyDailyTaskResponse {

    private TaskInfo task;
    private Boolean loggedIn;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TaskInfo {

        private String taskKey;
        private String name;
        private String description;
        private String evaluator;
        private String period;
        private Integer pointsHint;
        private Boolean configured;
        private String periodKey;
        private Boolean awardEnabled;
        private Boolean loggedIn;
        private String todayStatus;
        private String recordStatus;
        private Integer needSeconds;
        private String completedAt;
    }
}