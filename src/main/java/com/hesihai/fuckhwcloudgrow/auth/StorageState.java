package com.hesihai.fuckhwcloudgrow.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Playwright storageState 中签到需要的部分。origins 等未知字段会被忽略。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class StorageState {

    /**
     * 登录态 Cookie 列表，默认空列表避免反序列化后为 null。
     */
    private List<StoredCookie> cookies = new ArrayList<>();
}
