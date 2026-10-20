package com.hesihai.fuckhwcloudgrow.login;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hesihai.fuckhwcloudgrow.auth.AuthMeta;
import com.hesihai.fuckhwcloudgrow.auth.AuthStateStore;
import com.hesihai.fuckhwcloudgrow.client.HuaweiUrls;
import com.hesihai.fuckhwcloudgrow.client.browser.BrowserSession;
import com.hesihai.fuckhwcloudgrow.client.dto.MemberInfoResponse;
import com.microsoft.playwright.Page;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.time.Instant;

/**
 * 登录成功后的持久化：导出 storageState、保存账号元数据，并在失败时落盘诊断材料。
 *
 * <p>storageState 保存失败不应影响签到主流程，因此这里只记录日志，不向上抛异常。</p>
 */
@Slf4j
final class LoginStatePersister {

    private final AuthStateStore authStateStore;
    private final ObjectMapper objectMapper;

    LoginStatePersister(AuthStateStore authStateStore, ObjectMapper objectMapper) {
        this.authStateStore = authStateStore;
        this.objectMapper = objectMapper;
    }

    /**
     * 登录成功后保存 storageState 与 memAlias/memName，供后续 HTTP 快速路径复用。
     */
    void save(BrowserSession session, String runId) {
        try {
            Page page = session.activePage();
            if (ObjectUtil.isNull(page) || page.isClosed()) {
                return;
            }
            String url = page.url();
            // 不在成长中心页时先导航回去，保证探针接口的 Origin/Referer 正确
            if (StrUtil.isBlank(url) || !url.contains(HuaweiUrls.DEVELOPER_DOMAIN)) {
                LoginPageDriver.navigate(page, HuaweiUrls.GROW_URL);
            }
            page.waitForTimeout(1000);

            LoginApiProbe.ProbeResult probe = LoginApiProbe.fetchPersonalInfo(page);
            String memAlias = null;
            String memName = null;
            if (StrUtil.isNotBlank(probe.body())) {
                try {
                    MemberInfoResponse member = objectMapper.readValue(
                            probe.body(), MemberInfoResponse.class);
                    memAlias = member.getMemAlias();
                    memName = member.getMemName();
                } catch (Exception ignored) {
                    // 解析失败时 storageState 仍然有价值
                }
            }
            log.info("保存登录态：memAlias={}, personalInfoLength={}",
                    memAlias, probe.body().length());
            AuthMeta meta = AuthMeta.builder()
                    .savedAt(Instant.now())
                    .userAgent(session.userAgent())
                    .memAlias(memAlias)
                    .memName(memName)
                    .build();
            authStateStore.save(session.storageState(), meta);
        } catch (Exception e) {
            log.warn("保存登录态失败：{}", e.getMessage());
        }
    }

    /**
     * 保存截图与 HTML，便于排查登录页改版、风控或人工等待超时。
     */
    void saveDiagnostics(Page page, String runId, String suffix) {
        if (ObjectUtil.isNull(page) || page.isClosed()) {
            return;
        }
        try {
            Path dir = authStateStore.diagnosticsDir();
            page.screenshot(new Page.ScreenshotOptions()
                    .setPath(dir.resolve(runId + "-" + suffix + ".png")));
            FileUtil.writeUtf8String(page.content(),
                    dir.resolve(runId + "-" + suffix + ".html").toFile());
        } catch (Exception e) {
            log.debug("保存登录诊断信息失败：{}", e.getMessage());
        }
    }
}
