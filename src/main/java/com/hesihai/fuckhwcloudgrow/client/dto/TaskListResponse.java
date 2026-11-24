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
     * 单条任务记录，签到只需要 taskTag 与 isFinish。
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TaskRecord {

        private String id;
        private String title;
        private String taskTag;
        private Boolean isFinish;
    }
}