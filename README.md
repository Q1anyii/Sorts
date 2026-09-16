# 梭子 SORTS

> 智能日程管理平台 · 主题「**光阴似箭，日月如梭**」

把时间当作织机：日程是经线，专注是纬线，一次「开梭—穿梭—落梭」就是一段被记录下来的专注。
梭子（SORTS）围绕这条主线提供日程编排、计时追踪、日历聚合、统计分析、AI 织师规划与商城装扮。

---

## 一、功能一览

| 模块                      | 端口   | 能做什么                                                    |
| ----------------------- | ---- | ------------------------------------------------------- |
| **sorts-gateway**       | 8080 | 统一入口：路由转发、JWT 鉴权、Redis 令牌桶限流、剥离伪造的内部凭证                  |
| **sorts-user**          | 8081 | 注册登录、双令牌（access/refresh）、资料维护、光阴砂积分与流水                   |
| **sorts-schedule**      | 8082 | 日程 CRUD、计时状态机（开梭/暂停/续梭/落梭/取消）、日历聚合、统计汇总、落梭发积分           |
| **sorts-ai**            | 8083 | 自然语言对话（SSE 流式）、日程规划与一键采纳、日/月/年总结报告、工具调用（Tool Calling）   |
| **sorts-notification**  | 8084 | 通知列表与已读、提醒设置（提前量/免打扰）、定时提醒扫描与幂等去重                       |
| **sorts-mall**          | 8085 | 锦市商品、购买（加锁 + 条件更新防超卖）、装扮仓库与启用切换                         |
| **frontend**            | 8088 | Vue 3 + Vite 单页应用：今日经纬、织历、日程清单、穿梭计时、纹谱统计、AI 织师、锦市、衣橱、飞鸽传书、设置 |

---

## 二、技术栈

| 分类    | 选型                                                                          |
| ----- | --------------------------------------------------------------------------- |
| 后端    | Spring Boot 3.3.4 / Spring Cloud 2023.0.3 / Spring Cloud Alibaba 2023.0.3.2   |
| 持久化   | MyBatis-Plus 3.5.7 + MySQL 8（每服务独立库，禁止跨库直连）                                   |
| 注册与配置 | Nacos 2.4.3（Docker，单机模式）                                                     |
| 缓存    | Redis（redis-stack：Redis 本体 + RediSearch 等模块）                                |
| 消息    | RabbitMQ 3.13（当前提醒走 `@Scheduled`；MQ 为事务性消息/outbox 技术债预留）                      |
| 服务间调用 | OpenFeign（内部接口统一 `/internal/**` + `X-Internal-Token`）                      |
| AI 模型 | DeepSeek（OpenAI 兼容协议，自实现客户端，藏在 `ChatModelClient` 后面）                        |
| 前端    | Vue 3.5 + TypeScript + Vite 6 + Pinia + Vue Router 4 + axios                  |
| 构建与运行 | JDK 17 编译目标、Maven Wrapper、Docker Compose、GitHub Actions                      |

---

## 三、目录结构

```
.
├── README.md                 # 本文档
├── api-spec.json             # OpenAPI 契约（改接口前先对齐）
├── docker/                   # 容器编排（唯一入口）
│   ├── compose.yml           # 中间件 + 6 个服务 + 前端（profile 区分）
│   ├── Dockerfile.backend    # 服务通用镜像（多阶段：Maven → JRE）
│   ├── Dockerfile.frontend   # 前端镜像（Node 构建 → nginx）
│   ├── nginx.conf            # SPA 兜底 + /api 反代（含 SSE 关缓冲）
│   └── .env.example          # 端口/口令模板（cp 为 .env 使用）
├── backend/                  # Maven 多模块工程（8 个模块）
│   ├── mvnw / mvnw.cmd       # Maven Wrapper（IDE/CI 无需预装 Maven）
│   ├── sorts-common/         # Result/异常/JWT/PageData/内部凭证拦截器
│   ├── sorts-gateway/        # 网关
│   ├── sorts-user/           # 用户服务
│   ├── sorts-schedule/       # 日程服务
│   ├── sorts-ai/             # AI 服务（llm / tool / service / controller）
│   ├── sorts-notification/   # 通知服务
│   └── sorts-mall/           # 商城服务
├── frontend/                 # Vue3 + Vite 工程
│   ├── src/{api,components,layouts,router,stores,styles,types,utils,views}
│   ├── tests/                # vitest 单测
│   └── legacy-demo/          # 早期单文件演示页（留作参考）
├── docs/
│   ├── PROGRESS.md           # 进度、决策记录、技术债、路线图
│   ├── dev-setup.md          # 开发手册（端口、命令、约定）
│   ├── ide-setup.md          # IDEA 运行手册（Maven 导入、JDK 17、报错速查）
│   └── theme-design.md       # 主题规范「织锦流光」
├── scripts/
│   ├── docker.sh             # 容器统一入口（up/app/web/status/logs/sql/clean…）
│   ├── mvn.sh                # 沙箱内构建封装（见「常见问题」）
│   ├── wsl-middleware.sh     # 兼容层，映射到 docker.sh
│   └── sql/                  # 建库建表 + 种子数据（首次启动自动执行）
├── .run/                     # IDEA 共享运行配置（6 服务 + Compound）
└── .github/workflows/ci.yml  # CI：矩阵构建 + 可选推镜像/部署
```

---

## 四、快速开始

### 0. 前置

| 依赖      | 说明                                                          |
| ------- | ----------------------------------------------------------- |
| Docker  | Docker Desktop（WSL 2 后端）或 WSL 内自建 dockerd                     |
| JDK 17  | 本地跑后端需要（IDEA 里用注册好的 JDK 17，勿用 23+：Lombok 1.18.34 会编译失败）    |
| Node 22 | 本地跑前端需要                                                     |

### 1. 起中间件（一条命令）

```bash
# WSL 或 Git Bash 内执行（首次会自动生成 docker/.env）
bash scripts/docker.sh up
```

等价于 `docker compose -f docker/compose.yml up -d`：拉起 MySQL 3307、Redis 6379、Nacos 8848、RabbitMQ 5672，并在 MySQL 首次初始化数据卷时自动执行 `scripts/sql/*.sql` 建库建表。

```bash
bash scripts/docker.sh status        # 端口 + 容器 + 数据库一览
bash scripts/docker.sh sql           # 新增建表脚本后补执行
bash scripts/docker.sh down          # 停止（数据保留在命名卷）
```

### 2. 跑后端（本地进程，推荐日常开发）

```bash
cd backend && ./mvnw clean install   # 终端 / IDEA / WSL / CI 都可用
cd backend && ./mvnw test            # 只跑单测
```

随后用 `.run/` 里的共享配置逐个启动服务（详见 `docs/ide-setup.md`），或直接：

```bash
cd backend && ./mvnw -pl sorts-gateway spring-boot:run
```

### 3. 跑前端

```bash
cd frontend
npm install
npm run dev      # http://localhost:5173，/api 已代理到 localhost:8080
npm test         # vitest
npm run build    # 产物 dist/
```

### 4. 全容器化运行（宿主不装 JDK/Node 也能跑）

```bash
bash scripts/docker.sh up        # 中间件
bash scripts/docker.sh app       # 构建并启动 6 个服务（网关 8080）
bash scripts/docker.sh web       # 可选：前端站点 8088（nginx 反代 /api）
bash scripts/docker.sh app-down  # 停服务
```

服务镜像为多阶段构建（`docker/Dockerfile.backend`，按 `MODULE` 参数复用同一份 Dockerfile），容器内通过服务名互访（`mysql` / `redis` / `nacos`），不依赖宿主机端口映射。

---

## 五、端口与账号（本地开发默认值）

| 资源            | 地址                                        | 账号                                                     |
| ------------- | ----------------------------------------- | ------------------------------------------------------ |
| MySQL         | localhost:**3307**                        | root / `sorts_dev`                                     |
| Redis         | localhost:**6379**                        | 密码 `sorts_dev`                                          |
| Nacos         | http://localhost:8848/nacos               | 单机免鉴权                                                  |
| RabbitMQ      | localhost:5672 / http://localhost:15672   | sorts / `sorts_dev`                                    |
| 网关            | http://localhost:8080                     | 需 `Authorization: Bearer <accessToken>`                 |
| 前端（dev / 容器）  | http://localhost:5173 / **8088**          | —                                                      |
| 数据库           | sorts_user、sorts_schedule、sorts_ai、sorts_notification、sorts_mall | —                                    |

> 口令都通过 `docker/.env` 覆盖（模板见 `docker/.env.example`，`.env` 不入库）。
> 服务读取的环境变量：`MYSQL_HOST/PORT/USER/PASSWORD`、`REDIS_HOST/PORT/PASSWORD`、`NACOS_ADDR`、`INTERNAL_TOKEN`、`DEEPSEEK_API_KEY`。

---

## 六、接口与数据约定（前后端都要守）

1. **统一响应体** `Result<T>`：`{ code, message, data, timestamp }`，**`code = 0` 表示成功**。
   （`api-spec.json` 示例为 `200`、分页字段为 `records`，实际实现以本文为准，缘由见 `docs/PROGRESS.md` 决策记录）
2. **分页字段是 `list`**：`PageData<T> = { list, total, page, pageSize }`。
3. **时长口径**：日程与统计对外一律「秒」（`actualDuration`、`totalFocusTime`…），唯一例外是 `plannedDuration`（分钟）。
4. **鉴权**：网关校验 JWT 后注入 `X-User-Id`，业务服务只读该头；前端持有 `accessToken` / `refreshToken`，401 时单飞续期后重放。
5. **内部接口**：一律 `/internal/**`（网关不路由该前缀）并标 `@InternalApi`；出站凭证由 common 的 Feign 拦截器按路径白名单自动附加，新增路径要同步 `sorts.internal.paths`。
6. **AI 流式**：传输方式由 **query 参数**决定——`/ai/chat` 默认 SSE（`?stream=false` 走 JSON），`/ai/plan` 反之；事件为 `delta` / `done` / `error`。
7. **AI 写操作是双钥匙**：服务端 `sorts.ai.tool.allow-write` **且** 请求体 `allowWrite=true`（前端需用户确认后置位）。

---

## 七、测试

| 层        | 命令                           | 覆盖                                            |
| -------- | ---------------------------- | --------------------------------------------- |
| 后端       | `cd backend && ./mvnw test`  | 各模块 service / controller / 工具类单测               |
| 前端       | `cd frontend && npm test`    | 时长与时辰节气工具、状态机映射、SSE 帧切分、错误语义                  |
| 端到端（待补）  | —                            | Testcontainers 集成测试（技术债，见 `docs/PROGRESS.md`） |

---

## 八、常见问题

**Q：容器起不来，提示 `Cannot connect to the Docker daemon`？**
A：WSL 内自建 dockerd 时先看 `/etc/docker/daemon.json` 是否合法——`proxies` 只接受 `http-proxy` / `https-proxy` / `no-proxy` 这类扁平键，写成 `proxies.default` 会让 dockerd 直接启动失败（本仓库已修，原文件备份为 `daemon.json.bak.20260916`）。修好后 `systemctl start docker`。

**Q：Git Bash 里 `mvn` 报 `ClassNotFoundException: classworlds.launcher.Launcher`？**
A：不是 Maven 坏了：环境注入了 `MSYS_NO_PATHCONV=1`，POSIX 路径没被转成 Windows 路径就交给了 `java.exe`。**沙箱内用 `bash scripts/mvn.sh`**，其余场景用 `cd backend && ./mvnw`。

**Q：IDEA 打开仓库根目录后没有 Maven 面板？**
A：右键 `backend/pom.xml` → **Add as Maven Project**；项目 SDK 选 JDK 17（本机注册名 `ms-17`）。

**Q：Redis 连不上 / 提示 NOAUTH？**
A：Redis 现由 compose 提供（6379，密码见 `docker/.env`）。历史上 WSL 里另有一个「原生 redis-server」监听 6380 且带密码（配置在 `/etc/redis/redis.conf`），最容易在这里踩坑——该实例已随容器化停用，`bash scripts/docker.sh clean-legacy` 会帮你确认并清理。

**Q：前端页面能打开但接口 404？**
A：`npm run dev` 走 Vite 代理，需要网关在 8080；容器方式访问 8088 时由 nginx 反代到 `gateway:8080`。两种方式都不需要改前端代码。

---

## 九、文档索引

| 文档                     | 用途                                        |
| ---------------------- | ----------------------------------------- |
| `docs/PROGRESS.md`     | 里程碑进度、关键决策、已知限制与技术债、新会话续接指南               |
| `docs/dev-setup.md`    | 环境准备、端口、构建命令、内部凭证与购买一致性约定                 |
| `docs/ide-setup.md`    | IDEA 导入、JDK 17、共享运行配置、常见报错速查               |
| `docs/theme-design.md` | 主题规范：纸·墨·印三层材质、色彩 2.0、印章体系、动效与降级         |
| `api-spec.json`        | OpenAPI 契约（与实现的已知偏差见 PROGRESS 决策记录）       |
