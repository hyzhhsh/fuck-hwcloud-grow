package com.hesihai.fuckhwcloudgrow.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * POST /user/tasks 的 data 部分。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TaskListResponse {

    /**
     * 当前页任务列表。
     */
    private List<TaskRecord> records;
    /**
     * 任务总数。
     */
    private Integer total;
    /**
     * 总页数。
     */
    private Integer pages;

    /**
     * 单条任务记录。签到时只用到 taskTag/isFinish，其余字段用于其他每日任务的判定与校验。
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TaskRecord {

        private String id;
        private String title;
        private String description;
        private String taskTag;
        private Boolean isFinish;
        /**
         * 任务周期：DAILY / WEEKLY / MONTHLY / LIMIT / NO_LIMIT。
         */
        private String cycleType;
        private Integer completedTimes;
        private Integer taskLimit;
        private String taskGuide;
        /**
         * 任务跳转地址，成长中心前端"去完成"按钮直接打开它。
         */
        private String url;
        /**
         * 任务完成判定方式，例如 dwell。
         */
        private String evaluator;
        private Integer points;
    }
}