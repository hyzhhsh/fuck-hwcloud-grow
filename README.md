# fuck-hwcloud-grow

华为云开发者成长中心每日自动签到工具。基于 Spring Boot 3.5 + Playwright，每天定时自动完成[华为云开发者成长中心](https://developer.huaweicloud.com/grow)的每日签到，赚取积分。

## 功能特性

- **每日自动签到**：默认每天 8:00（可配置 cron）自动执行，也可通过接口手动触发
- **双通道签到**：优先使用本地 Cookie 登录态直接走 HTTP 快速签到；登录态失效或被风控拦截时，自动降级为 Playwright 有头浏览器完成登录与签到
- **人工介入**：遇到验证码/两步验证时，通过息知（微信）推送通知等待人工处理，可借助内置 noVNC 网页远程查看和操作容器内的浏览器
- **结果推送**：每次签到结果（成功/失败/需人工）通过息知推送到微信
- **轻量部署**：无数据库依赖，登录态、浏览器 profile、签到结果均持久化在本地 `data/` 目录下的 JSON 文件中

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

2. **安装 Playwright 浏览器**（首次或升级依赖后执行）：

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

   运行镜像基于 Playwright 官方 Java 镜像，浏览器已内置，无需手动安装。

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
| `GROW_CRON` | 否 | 定时任务 cron 表达式，默认 `0 0 8 * * *`（每天 8 点） |
| `GROW_ZONE` | 否 | 时区，默认 `Asia/Shanghai` |
| `GROW_HUMAN_WAIT` | 否 | 人工介入最长等待时间，默认 `10m` |
| `GROW_HEADLESS` | 否 | 是否无头模式，默认 `false` |

## 常用接口

应用端口 8080，统一前缀 `/fuck-hwcloud-grow`，接口文档（knife4j）：`http://localhost:8080/fuck-hwcloud-grow/doc.html`

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/health` | 健康检查 |
| POST | `/api/signin/run` | 手动触发一次签到 |
| GET | `/api/signin/status` | 查询当前登录态与今日签到状态（只读，不启动浏览器） |
| GET | `/api/signin/last-result` | 查询最近一次签到结果 |

## 注意事项

- **接口未做鉴权**，请仅在内网或本机部署暴露，不要直接暴露到公网
- `.env` 包含账号密码等敏感信息，已加入 `.gitignore`，请勿提交
- `data/` 目录存放登录态（`data/state/`）、浏览器 profile（`data/browser-profile/`）与签到结果，删除后重新运行会触发重新登录
- 息知推送服务地址：<https://xz.qqoq.net>，`XIZHI_KEY` 在该平台获取
