# fuck-hwcloud-grow

华为云开发者成长中心每日任务自动化工具。基于 Spring Boot 3.5 + Playwright，每天定时自动完成[华为云开发者成长中心](https://grow.developer.huaweicloud.com/)的每日任务：

- 每日签到
- 每日浏览一个鸿蒙案例

## 功能特性

- **多任务编排**：同一 cron 顺序执行「凭据检查 → 签到 → 鸿蒙案例」，结束后推送一条汇总通知；每个任务可单独开关
- **凭据层独立**：先用只读接口探测登录态，失效时才启动浏览器登录一次，登录后预热各子域并关闭浏览器；纯 HTTP 任务不再启动浏览器
- **签到与鸿蒙案例纯 HTTP**：签到复用成长中心接口；鸿蒙案例走实践中心的计时会话 + 心跳协议，约 30 秒完成
- **失败语义清晰**：风控拦截只影响当前任务；任务执行中检测到登录态失效会清空本地状态并中止后续任务；瞬时错误有限重试
- **人工介入**：遇到验证码/两步验证时，通过息知（微信）推送通知等待人工处理，可借助内置 noVNC 网页远程查看和操作容器内的浏览器
- **轻量部署**：无数据库依赖；登录态与任务结果以 JSON 持久化，浏览器使用 `data/browser-profile/` 下的持久化 profile

## 环境要求

| 方式 | 要求 |
| --- | --- |
| 本地调试 | JDK 21、Maven 3.9+ |
| Docker 部署 | Docker + Docker Compose |

## 启动方式

### 方式一：本地调试

1. **准备配置文件**：复制 `.env.example` 为 `.env`，填写以下内容（`NOVNC_*`、`GROW_NO_SANDBOX` 等 Docker 专属项本地不需要）：

   ```ini
   HWCLOUD_USERNAME=你的华为云账号
   HWCLOUD_PASSWORD=你的华为云密码
   XIZHI_KEY=你的息知推送key（可选，不填则不推送）
   ```

2. **浏览器准备**：

   程序默认优先使用系统安装的 **Google Chrome**（`GROW_BROWSER_CHANNEL=chrome`），本机装了 Chrome 即可直接启动，无需额外安装。

   若本机没有 Chrome（或配置了 `GROW_BROWSER_CHANNEL=` 强制使用捆绑 Chromium），需要安装 Playwright 捆绑浏览器（首次或升级依赖后执行）：

   ```bash
   mvn -Pinstall-browser compile exec:java
   ```

3. **启动项目**：

   ```bash
   mvn spring-boot:run
   ```

   或在 IDE 中直接运行主类 `FuckHwcloudGrowApplication`。

4. 默认以**有头模式**运行，登录失效时会弹出浏览器窗口自动登录；遇到验证码时需在本机浏览器窗口中人工完成，然后程序会自动继续。

### 方式二：Docker Compose 部署

1. **准备配置文件**：复制 `.env.example` 为 `.env`，填写账号信息和推送配置：

   ```ini
   HWCLOUD_USERNAME=你的华为云账号
   HWCLOUD_PASSWORD=你的华为云密码
   XIZHI_KEY=你的息知推送key（可选，不填则不推送）
   NOVNC_PASSWORD=自定义noVNC访问密码
   ```

2. **修改宿主机 IP**：将 `.env` 中 `NOVNC_PUBLIC_URL` 里的 IP 改为宿主机实际 IP（该地址会出现在人工介入的微信通知里，用于远程操作浏览器）：

   ```ini
   NOVNC_PUBLIC_URL=http://192.168.1.10:6080/vnc.html?autoconnect=1&resize=scale
   #                             ^^^^^^^^^ 改成你的宿主机 IP
   ```

3. **构建镜像并启动**：

   ```bash
   docker compose up -d --build
   ```

   运行镜像基于 Playwright 官方 Java 镜像，并在构建时额外安装了 Google Chrome，浏览器无需手动安装。

4. **验证**：

   ```bash
   curl http://localhost:8080/fuck-hwcloud-grow/health
   ```

   正常返回后，可打开 `http://<宿主机IP>:6080/vnc.html` 并输入 `NOVNC_PASSWORD`，远程查看/操作容器内的浏览器（人工处理验证码）。

## 配置说明

完整配置见 `.env.example`，常用变量如下：

| 变量 | 必填 | 说明 |
| --- | --- | --- |
| `HWCLOUD_USERNAME` | 是 | 华为云账号 |
| `HWCLOUD_PASSWORD` | 是 | 华为云密码 |
| `XIZHI_KEY` | 否 | 息知微信推送 key，不填则不推送通知 |
| `NOVNC_PASSWORD` | Docker 是 | noVNC 远程桌面访问密码 |
| `NOVNC_PUBLIC_URL` | Docker 是 | noVNC 公网访问地址（改 IP 为宿主机地址） |
| `GROW_CRON` | 否 | 任务批次 cron 表达式，默认 `0 0 8 * * *`（每天 8 点） |
| `GROW_ZONE` | 否 | 时区，默认 `Asia/Shanghai` |
| `GROW_HUMAN_WAIT` | 否 | 人工介入最长等待时间，默认 `10m` |
| `GROW_HEADLESS` | 否 | 是否无头模式，默认 `false` |
| `GROW_BROWSER_CHANNEL` | 否 | 浏览器通道，默认 `chrome`（使用系统安装的 Google Chrome）；设为空则使用 Playwright 捆绑 Chromium，系统 Chrome 启动失败会自动回退 |
| `GROW_TASK_SIGNIN_ENABLED` | 否 | 是否执行签到任务，默认 `true` |
| `GROW_TASK_HARMONY_ENABLED` | 否 | 是否执行鸿蒙案例浏览任务，默认 `true` |
| `GROW_TASKS_TOTAL_TIMEOUT` | 否 | 整批任务时间预算，默认 `20m`；超时后剩余任务记"未执行" |
| `GROW_CREDENTIAL_PROBE_TIMEOUT` | 否 | 单域登录态探测超时，默认 `10s` |
| `GROW_HARMONY_CASE_ID` | 否 | 指定浏览的鸿蒙案例 ID；为空则按日期轮转 |
| `GROW_HARMONY_TASK_TAG` | 否 | 成长中心中鸿蒙任务的 `task_tag`，用于完成状态复核；未知可留空（按标题"鸿蒙"匹配） |

## 常用接口

应用端口 8080，统一前缀 `/fuck-hwcloud-grow`，接口文档（knife4j）：`http://localhost:8080/fuck-hwcloud-grow/doc.html`

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/health` | 健康检查 |
| POST | `/api/tasks/run` | 手动触发整批任务（凭据 → 签到 → 鸿蒙） |
| POST | `/api/tasks/{key}/run` | 单独触发任务，key 为 `signin` / `harmony-case` |
| GET | `/api/tasks/last-batch` | 查询最近一批任务结果 |
| GET | `/api/tasks/status` | 查询任务开关与只读登录态探测结果（不启动浏览器） |
| POST | `/api/signin/run` | 手动触发一次签到（路径与返回结构兼容旧版；登录改由凭据层完成，签到只走 HTTP） |
| GET | `/api/signin/status` | 查询当前登录态与今日签到状态（只读，不启动浏览器） |
| GET | `/api/signin/last-result` | 查询最近一次签到结果 |

## 注意事项

- **接口未做鉴权**，请仅在内网或本机部署暴露，不要直接暴露到公网
- `.env` 包含账号密码等敏感信息，已加入 `.gitignore`，请勿提交
- `data/` 目录存放登录态（`data/state/auth*.json`）、浏览器 profile（`data/browser-profile/`）、任务结果（`data/state/last-batch.json`、签到另有 `last-result.json`）与诊断材料（`data/diagnostics/`），删除登录态会触发重新登录
- **浏览器只用于登录**：签到、鸿蒙案例都是 Java 侧 HTTP 请求；登录态失效时凭据层会启动浏览器登录一次，并预热任务依赖的域（通常是 `grow` 与 `harmonybp`），随后关闭
- **鸿蒙案例任务**依赖 `harmonybp.developer.huaweicloud.com` 的登录态；若执行时检测到该域未登录，会记失败并清空本地登录态、中止本批后续任务，不会反复重登
- 为降低华为云登录风控（"设备不安全" 408 拦截）概率，程序默认使用系统 Chrome 并隐藏自动化特征；建议首次使用时在弹出的浏览器窗口中**手动登录一次并勾选"信任此设备"**，登录态会保留在 `data/browser-profile/` 供后续复用
- 息知推送服务地址：<https://xz.qqoq.net>，`XIZHI_KEY` 在该平台获取；每批任务只推送一条汇总（含各任务状态与结束时的积分），登录验证码需人工时另有即时提醒
