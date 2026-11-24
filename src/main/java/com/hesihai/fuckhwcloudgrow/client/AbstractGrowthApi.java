package com.hesihai.fuckhwcloudgrow.client;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hesihai.fuckhwcloudgrow.client.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * GrowthApi 的模板实现：统一业务方法、错误分类与响应信封解析。
 *
 * <p>子类只提供底层传输能力（{@link #executeRaw}），业务方法在这里统一维护。</p>
 */
@Slf4j
public abstract class AbstractGrowthApi implements GrowthApi {

    /**
     * 任务列表每页条数，与前端保持一致。
     */
    private static final int TASK_PAGE_SIZE = 100;
    /**
     * 查找每日签到任务时最多翻页数，避免异常数据导致无限请求。
     */
    private static final int TASK_MAX_PAGES = 5;

    private final ObjectMapper objectMapper;
    private final String source;

    protected AbstractGrowthApi(ObjectMapper objectMapper, String source) {
        this.objectMapper = objectMapper;
        this.source = source;
    }

    /**
     * 构造任务列表查询请求体，与前端调用保持一致。
     */
    private static Map<String, Object> taskQueryBody(int pageNo) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("biz_type", "opc");
        body.put("show", true);
        body.put("page_no", pageNo);
        body.put("page_size", TASK_PAGE_SIZE);
        return body;
    }

    /**
     * 在任务列表中定位每日签到任务，is_finish 即为今日签到状态。
     */
    private static SignStatusResponse findDailySignStatus(List<TaskListResponse.TaskRecord> records) {
        for (TaskListResponse.TaskRecord record : records) {
            if (ObjectUtil.equals(HuaweiUrls.DAILY_SIGN_TASK_TAG, record.getTaskTag())) {
                SignStatusResponse status = new SignStatusResponse();
                status.setSignFlag(Boolean.TRUE.equals(record.getIsFinish()) ? 1 : 0);
                return status;
            }
        }
        return null;
    }

    @Override
    public UserInfoResponse getUserInfo() {
        ResponseData data = executeRaw(HttpMethod.GET, HuaweiUrls.USER_INFO_PATH, null);
        ResponseClassifier.throwIfError(data.status(), data.contentType(), data.body());
        return readData(data.body(), UserInfoResponse.class);
    }

    @Override
    public SignStatusResponse getSignStatus() {
        for (int pageNo = 1; pageNo <= TASK_MAX_PAGES; pageNo++) {
            ResponseData data = executeRaw(HttpMethod.POST, HuaweiUrls.USER_TASKS_PATH,
                    taskQueryBody(pageNo));
            ResponseClassifier.throwIfError(data.status(), data.contentType(), data.body());
            TaskListResponse tasks = readData(data.body(), TaskListResponse.class);
            if (ObjectUtil.isNull(tasks) || ObjectUtil.isEmpty(tasks.getRecords())) {
                return null;
            }
            SignStatusResponse status = findDailySignStatus(tasks.getRecords());
            if (ObjectUtil.isNotNull(status)) {
                return status;
            }
            Integer pages = tasks.getPages();
            if (ObjectUtil.isNull(pages) || pageNo >= pages) {
                return null;
            }
        }
        return null;
    }

    @Override
    public String source() {
        return source;
    }

    @Override
    public BeansInfoResponse getBeans() {
        UserInfoResponse user = getUserInfo();
        return new BeansInfoResponse(ObjectUtil.isNull(user) ? null : user.getTotalPoints());
    }

    @Override
    public List<TaskListResponse.TaskRecord> listTasks() {
        List<TaskListResponse.TaskRecord> all = new ArrayList<>();
        for (int pageNo = 1; pageNo <= TASK_MAX_PAGES; pageNo++) {
            ResponseData data = executeRaw(HttpMethod.POST, HuaweiUrls.USER_TASKS_PATH,
                    taskQueryBody(pageNo));
            ResponseClassifier.throwIfError(data.status(), data.contentType(), data.body());
            TaskListResponse tasks = readData(data.body(), TaskListResponse.class);
            if (ObjectUtil.isNull(tasks) || ObjectUtil.isEmpty(tasks.getRecords())) {
                break;
            }
            all.addAll(tasks.getRecords());
            Integer pages = tasks.getPages();
            if (ObjectUtil.isNull(pages) || pageNo >= pages) {
                break;
            }
        }
        return all;
    }

    @Override
    public SignSubmitOutcome submitSign() {
        ResponseData data = executeRaw(HttpMethod.POST, HuaweiUrls.SIGN_IN_PATH, null);
        String code = ResponseClassifier.extractErrorCode(data.body());
        String message = ResponseClassifier.extractErrorMessage(data.body());
        // 兼容厂商把“已签到”作为业务错误码返回的情况
        if (StrUtil.isNotBlank(code) && StrUtil.contains(message, "已签到")) {
            return SignSubmitOutcome.builder()
                    .success(true)
                    .signed(true)
                    .vendorCode(code)
                    .vendorMessage(message)
                    .rawBody(data.body())
                    .build();
        }
        ResponseClassifier.throwIfError(data.status(), data.contentType(), data.body());

        SignInResponse response = readData(data.body(), SignInResponse.class);
        return SignSubmitOutcome.builder()
                .success(true)
                .signed(ObjectUtil.isNotNull(response) && Boolean.TRUE.equals(response.getSigned()))
                .points(ObjectUtil.isNull(response) ? null : response.getPoints())
                .totalPoints(ObjectUtil.isNull(response) ? null : response.getTotalPoints())
                .vendorCode(code)
                .vendorMessage(message)
                .rawBody(data.body())
                .build();
    }

    /**
     * 解析统一信封 {@code {"code":0,"data":...}} 中的 data 部分。
     */
    protected <T> T readData(String body, Class<T> type) {
        if (StrUtil.isBlank(body)) {
            throw GrowthApiException.wafBlocked("接口响应为空", body);
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (JsonProcessingException e) {
            throw GrowthApiException.wafBlocked("接口响应不是合法 JSON，可能被 WAF 拦截", body);
        }
        JsonNode data = root.get("data");
        if (ObjectUtil.isNull(data) || data.isNull()) {
            return null;
        }
        try {
            return objectMapper.treeToValue(data, type);
        } catch (JsonProcessingException e) {
            throw GrowthApiException.wafBlocked("接口 data 结构无法解析", body);
        }
    }

    /**
     * 子类实现真正的传输并返回统一响应。
     */
    protected abstract ResponseData executeRaw(HttpMethod method, String path, Object body);

    /**
     * 统一 HTTP 与页面 fetch 的响应载体，屏蔽两套 header 结构。
     */
    protected static final class ResponseData {

        private final int status;
        private final String body;
        private final Map<String, String> headers;

        public ResponseData(int status, String body, Map<String, String> headers) {
            this.status = status;
            this.body = body;
            this.headers = ObjectUtil.defaultIfNull(headers, Map.of());
        }

        public int status() {
            return status;
        }

        public String body() {
            return body;
        }

        public String contentType() {
            return header("content-type");
        }

        /**
         * 大小写不敏感地读取响应头。
         */
        public String header(String name) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(name)) {
                    return entry.getValue();
                }
            }
            return null;
        }
    }
}