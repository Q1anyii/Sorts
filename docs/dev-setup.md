# 梭子 SORTS · 开发手册

> 主题：**光阴似箭，日月如梭**（详见 [主题设计规范](./theme-design.md)）

## 一、微服务地图

| 服务 | 模块名 | 端口 | 职责 |
|---|---|---|---|
| 网关 | `sorts-gateway` | 8080 | 统一入口、路由、鉴权校验、限流 |
| 用户服务 | `sorts-user` | 8081 | 注册登录、双令牌续期、用户信息、积分账户 |
| 日程服务 | `sorts-schedule` | 8082 | 日程 CRUD、计时状态机、日历视图、统计 |
| AI 服务 | `sorts-ai` | 8083 | DeepSeek 接入、工具集调用、规划生成、总结报告 |
| 通知服务 | `sorts-notification` | 8084 | 提醒推送、站内消息、已读管理 |
| 商城服务 | `sorts-mall` | 8085 | 虚拟商品、积分购买、装扮仓库 |
| 公共模块 | `sorts-common` | — | 统一响应、异常体系、JWT 工具、常量 |

**调用约定**：请求 → 网关（鉴权/限流）→ 各微服务；跨模块调用统一使用 OpenFeign，禁止跨库直连。

## 二、技术栈版本矩阵

| 组件 | 版本 |
|---|---|
| JDK / 编译目标 | 17（本机运行 JDK 22） |
| Spring Boot | 3.3.4 |
| Spring Cloud | 2023.0.3 |
| Spring Cloud Alibaba | 2023.0.3.2（Nacos 2.4.3） |
| AI 客户端 | **自实现**（OpenAI 兼容协议，见下方说明） |
| ORM | MyBatis-Plus 3.5.7 |
| 分布式锁 | Redisson 3.36.0 |
| 数据库 / 缓存 / 消息 | MySQL 8 · Redis 7 · RabbitMQ 3.13 |
| AI 模型 | DeepSeek（OpenAI 协议兼容） |

## 三、本地环境准备

### 1) WSL Ubuntu：启动中间件

```bash
# 在 WSL 内执行（全部用 Docker 承载，端口：Redis 6380 / MySQL 3307 / Nacos 8848 / RabbitMQ 5672）
bash scripts/wsl-middleware.sh start

# 查看状态 / 跟随日志 / 停止
bash scripts/wsl-middleware.sh status
bash scripts/wsl-middleware.sh logs sorts-mysql
bash scripts/wsl-middleware.sh stop

# 新增模块的建表脚本后，补执行一次（容器初始化目录只在「首次创建」时执行）
bash scripts/wsl-middleware.sh sql
```

| 中间件 | 端口 | 账号 |
|---|---|---|
| Redis | **6380**（约定：默认端口 +1） | 无密码 |
| MySQL 8 | **3307**（避开宿主 3306 冲突） | root / sorts_dev |
| Nacos | 8848（控制台 `/nacos`） | 免鉴权（单机开发） |
| RabbitMQ | 5672 / 管理台 15672 | sorts / sorts_dev |

> 首次启动 MySQL 时，`scripts/sql/*.sql` 会被自动执行（建 5 个库 + 建表，按文件名排序，`00-init-databases.sql` 在最前）。
> 容器已存在时不会重跑初始化目录，用 `bash scripts/wsl-middleware.sh sql` 补执行（脚本内均为 `CREATE ... IF NOT EXISTS`，可重复执行）。
> 账号密码可用环境变量覆盖：`MYSQL_ROOT_PASSWORD=xxx MYSQL_PORT=3307 bash scripts/wsl-middleware.sh start`
> 中间件相关环境变量：`MYSQL_HOST` / `MYSQL_PORT` / `MYSQL_USER` / `MYSQL_PASSWORD` / `REDIS_HOST` / `REDIS_PORT` / `NACOS_ADDR` / `NACOS_ENABLED`

**AI 服务额外环境变量**（`sorts-ai`）：

| 变量 | 默认值 | 说明 |
|---|---|---|
| `DEEPSEEK_API_KEY` | 空 | **必填**才能调用模型；未配置时 AI 接口返回 503 可读提示，服务仍可正常启动 |
| `DEEPSEEK_BASE_URL` | `https://api.deepseek.com/v1` | OpenAI 兼容入口，可指向任意同协议网关 |
| `DEEPSEEK_MODEL` | `deepseek-chat` | `deepseek-reasoner` 为推理模型 |
| `AI_TEMPERATURE` / `AI_MAX_TOKENS` | `0.7` / `2048` | 采样温度与单次输出上限 |
| `AI_TOOL_ALLOW_WRITE` | `false` | **双钥匙的第一把**：关闭时 AI 永远无法直接写入数据 |
| `AI_PORT` | `8083` | 服务端口 |

> 仓库内不留任何密钥。本地调试可在 IDEA 的 `SORTS · AI (8083)` 运行配置里填 `DEEPSEEK_API_KEY`（该配置已预置空占位），或设系统环境变量。
> 第二把钥匙是单次请求的 `allowWrite=true`（前端在用户确认后置位）；两把都到位，写工具才会下发给模型并被执行。

**为什么不用 Spring AI**：`spring-ai-starter-model-openai:1.0.0` 会把 `spring-boot-starter` 锁定在 3.4.5，与本项目的 Boot 3.3.4（Cloud 2023.0.3 / SCA 2023.0.3.2 不支持 Boot 3.4）冲突；1.0.0-M5/M6 在阿里云镜像上取不到。因此按 OpenAI 兼容协议自实现了一个薄客户端，藏在 `ChatModelClient` 接口之后——将来升级 Boot 3.4+ 想换回 Spring AI，只需新增一个实现类，业务代码零改动。

### 2) Windows：构建后端

```bash
bash scripts/mvn.sh              # clean install（全模块 + 单测）
bash scripts/mvn.sh clean test   # 仅跑测试
```

> **构建命令怎么选**：IDEA 内用 Maven 面板 / `backend/mvnw`；你自己的终端与 WSL / CI 用 `cd backend && ./mvnw clean install`；**AI 沙箱内必须用 `bash scripts/mvn.sh`**。
>
> ⚠️ **2026-09-16 更正：此前「本机 mvn 已损坏」是误诊。** 本机 `mvn -v` 输出 Apache Maven 3.9.15（`E:\develop`），完全正常。真实原因是 AI 沙箱注入了 `MSYS_NO_PATHCONV=1` 与 `MSYS2_ARG_CONV_EXCL=*`，关闭了 MSYS 路径自动转换，而 Maven 的 `bin/mvn` 是 POSIX sh 脚本，会把 `/c/...` 这类路径直接交给原生 `java.exe` → 报 `ClassNotFoundException: ...classworlds.launcher.Launcher`。`scripts/mvn.sh` 用 `cygpath` 转换路径后交给 java，因此在沙箱内始终可用。详见 [`docs/ide-setup.md`](./ide-setup.md)。

### 3) 启动顺序

Nacos → 业务服务（user → schedule → ai → notification → mall）→ 网关。

## 四、目录结构

```
backend/
├── pom.xml              # 父工程（依赖与插件版本统一管理）
├── sorts-common/        # 公共模块（自动装配）
├── sorts-gateway/       # 网关
└── sorts-{user,schedule,ai,notification,mall}/   # 各业务服务（按里程碑交付）
docs/                    # 设计文档
scripts/                 # 构建与中间件脚本
frontend/                # 前端（M6 起工程化重写）
```

## 五、Git 提交规范

采用 Conventional Commits：

```
<type>(<scope>): <subject>
```

- **type**：`feat` 新功能 / `fix` 修复 / `docs` 文档 / `style` 格式 / `refactor` 重构 / `perf` 性能 / `test` 测试 / `build` 构建 / `ci` 流水线 / `chore` 杂项
- **scope**（可选）：`gateway` / `user` / `schedule` / `ai` / `notify` / `mall` / `common` / `frontend` / `ci`
- 示例：`feat(user): 实现双令牌无感续期`

**每个功能点完成即提交一次**，保证提交粒度与里程碑一一对应。

## 六、编码约定

- 分层：`controller` → `service` → `mapper`，跨服务调用走 `client`（OpenFeign）。
- 对外一律返回 `Result<T>`，错误码取自 `ErrorCode` 枚举。
- 业务异常抛 `BizException`，禁止在 Controller 里 try-catch 后吞掉。
- 配置中的密钥、地址一律走环境变量，禁止硬编码进仓库。
- 每个模块配套单元测试，测试类命名 `*Test`，与业务代码同步交付。

### AI 与流式接口约定（M4 起）

- 模型调用统一走 `ChatModelClient`（`sorts-ai/.../llm`），**不要在业务里直接用 HttpClient**；将来换 Spring AI 只需换实现类。
- 工具（Tool Calling）必须经 `ToolRegistry` 下发与执行，**不要绕过它直连**：它承担「声明过滤 + 执行前复核 + 异常兜底」三段职责。
- 写工具是**双钥匙**：服务端 `sorts.ai.tool.allow-write=true` **且** 单次请求 `allowWrite=true`，缺一不可。
- 回灌给模型的 JSON 一律用 `ToolJsonCodec`（时间固定 ISO-8601），不要用全局 `ObjectMapper`——全局配置一旦被改成 timestamp，模型会把 `[2026,9,16,9,0]` 当普通数组照抄。
- 同一路径要同时支持 JSON 与 SSE 时，用 `params` 条件拆成两个处理方法；**不要**把返回类型写成 `Object`（Spring 按声明类型选处理器，`SseEmitter` 会被当普通对象序列化）。
- SSE 事件约定：`delta`（正文增量）/ `done`（最终结果）/ `error`（`{code,message}`）。
