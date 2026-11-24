package com.hesihai.fuckhwcloudgrow.service.task;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.ObjectUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hesihai.fuckhwcloudgrow.config.HwcloudGrowProperties;
import com.hesihai.fuckhwcloudgrow.model.task.TaskBatchResult;
import com.hesihai.fuckhwcloudgrow.storage.AtomicJsonFile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;

/**
 * 最近一次批量任务结果的持久化。
 */
@Slf4j
@Component
public class TaskResultStore {

    private final HwcloudGrowProperties properties;
    private final ObjectMapper objectMapper;

    public TaskResultStore(HwcloudGrowProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public void save(TaskBatchResult result) {
        if (ObjectUtil.isNull(result)) {
            return;
        }
        try {
            AtomicJsonFile.writeJson(properties.effectiveLastBatchFile(), result, objectMapper);
        } catch (IOException e) {
            log.warn("保存最近一次任务结果失败：{}", e.getMessage());
        }
    }

    public TaskBatchResult loadLast() {
        Path file = properties.effectiveLastBatchFile();
        if (!FileUtil.isFile(file.toFile())) {
            return null;
        }
        try {
            return objectMapper.readValue(file.toFile(), TaskBatchResult.class);
        } catch (IOException e) {
            log.warn("读取最近一次任务结果失败：{}", e.getMessage());
            return null;
        }
    }
}