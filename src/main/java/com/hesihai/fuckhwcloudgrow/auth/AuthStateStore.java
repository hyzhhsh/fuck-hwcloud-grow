package com.hesihai.fuckhwcloudgrow.auth;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hesihai.fuckhwcloudgrow.config.HwcloudGrowProperties;
import com.hesihai.fuckhwcloudgrow.storage.AtomicJsonFile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * 登录态持久化：读取 Playwright storageState 中的 Cookie，保存 storageState 与账号元数据。
 *
 * <p>storageState 由浏览器导出为 JSON 字符串后原样落盘，避免丢掉 origins 等签到不需要、
 * 但后续复用浏览器会话时有用的字段；元数据单独保存，供 HTTP 快速路径复用 User-Agent。</p>
 */
@Slf4j
@Component
public class AuthStateStore {

    private final HwcloudGrowProperties properties;
    private final ObjectMapper businessMapper;
    private final ObjectMapper storageMapper;

    public AuthStateStore(HwcloudGrowProperties properties, ObjectMapper businessMapper) {
        this.properties = properties;
        this.businessMapper = businessMapper;
        // storageState 是 Playwright 的 camelCase 格式，不能套用业务侧的 snake_case 策略
        this.storageMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    private static void createDirectory(Path dir) {
        try {
            FileUtil.mkdir(dir.toFile());
            AtomicJsonFile.secureDirectory(dir);
        } catch (Exception e) {
            log.warn("创建数据目录失败：{}", e.getMessage());
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            FileUtil.del(path.toFile());
        } catch (Exception e) {
            log.warn("删除登录态文件失败：{}", e.getMessage());
        }
    }

    /**
     * 读取本地 Cookie；文件缺失或损坏时返回空列表，让调用方转入浏览器登录。
     */
    public List<StoredCookie> loadCookiesFromFile() {
        Path authFile = properties.effectiveAuthFile();
        if (!FileUtil.isFile(authFile.toFile())) {
            return List.of();
        }
        try {
            StorageState state = storageMapper.readValue(authFile.toFile(), StorageState.class);
            List<StoredCookie> cookies = state.getCookies();
            return CollUtil.isEmpty(cookies) ? List.of() : cookies;
        } catch (Exception e) {
            log.warn("读取登录态失败，将转入浏览器登录：{}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 读取登录元数据。
     *
     * @return 元数据，文件不存在或读取失败时为 null
     */
    public AuthMeta loadMetaFromFile() {
        Path metaFile = properties.effectiveAuthMetaFile();
        if (!FileUtil.isFile(metaFile.toFile())) {
            return null;
        }
        try {
            return businessMapper.readValue(metaFile.toFile(), AuthMeta.class);
        } catch (Exception e) {
            log.warn("读取登录元数据失败：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 保存浏览器导出的 storageState 与登录元数据。
     *
     * <p>保存失败只记日志不抛出：此时签到结果已经产生，登录态下次登录可重新获取，
     * 抛出会让一次成功的签到整体失败，得不偿失。</p>
     */
    public void save(String storageStateJson, AuthMeta meta) {
        ensureDirectories();
        try {
            AtomicJsonFile.writeText(properties.effectiveAuthFile(), storageStateJson);
            AtomicJsonFile.writeJson(properties.effectiveAuthMetaFile(), meta, businessMapper);
            log.info("登录态已保存");
        } catch (IOException e) {
            log.warn("保存登录态失败：{}", e.getMessage());
        }
    }

    /**
     * 删除本地登录态与元数据；在检测到登录态失效、转入浏览器重新登录前调用，
     * 避免下次仍复用已失效的状态。
     */
    public void invalidate() {
        deleteQuietly(properties.effectiveAuthFile());
        deleteQuietly(properties.effectiveAuthMetaFile());
    }

    /**
     * 诊断材料目录，不存在时自动创建。
     *
     * @return 目录路径，创建失败时仍返回路径（写入方自行兜底）
     */
    public Path diagnosticsDir() {
        Path dir = properties.effectiveDiagnosticsDir();
        try {
            FileUtil.mkdir(dir.toFile());
            AtomicJsonFile.secureDirectory(dir);
        } catch (Exception e) {
            log.warn("创建诊断目录失败：{}", e.getMessage());
        }
        return dir;
    }

    /**
     * 创建 state/profile/diagnostics 三个数据目录，创建失败只记日志。
     */
    public void ensureDirectories() {
        createDirectory(properties.effectiveStateDir());
        createDirectory(properties.effectiveProfileDir());
        createDirectory(properties.effectiveDiagnosticsDir());
    }
}
