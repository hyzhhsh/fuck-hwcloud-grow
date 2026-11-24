package com.hesihai.fuckhwcloudgrow.client.harmony.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * GET /api/cases 的响应。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class HarmonyCaseListResponse {

    private Integer total;
    private List<CaseItem> cases;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CaseItem {

        private String id;
        private String title;
        private String description;
        private String status;
    }
}