package com.hesihai.fuckhwcloudgrow.storage;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.ObjectUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hesihai.fuckhwcloudgrow.config.HwcloudGrowProperties;
import com.hesihai.fuckhwcloudgrow.model.SigninResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;

/**
 * 最近一次签到结果的持久化。
 */
@Slf4j
@Component
public class SigninResultStore {

    private final HwcloudGrowProperties properties;
    private final ObjectMapper objectMapper;

    public SigninResultStore(HwcloudGrowProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * 保存结果；写失败只记日志，不能影响签到主流程。
     *
     * @param result 签到结果，为 null 时直接跳过
     */
    public void save(SigninResult result) {
        if (ObjectUtil.isNull(result)) {
            return;
        }
        try {
            AtomicJsonFile.writeJson(properties.effectiveLastResultFile(), result, objectMapper);
        } catch (IOException e) {
            log.warn("保存最近一次签到结果失败：{}", e.getMessage());
        }
    }

    /**
     * 读取最近一次签到结果。
     *
     * @return 结果对象，文件不存在或读取失败时为 null
     */
    public SigninResult loadLast() {
        Path file = properties.effectiveLastResultFile();
        if (!FileUtil.isFile(file.toFile())) {
            return null;
        }
        try {
            return objectMapper.readValue(file.toFile(), SigninResult.class);
        } catch (IOException e) {
            log.warn("读取最近一次签到结果失败：{}", e.getMessage());
            return null;
        }
    }
}
