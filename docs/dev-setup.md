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
| Spring AI | 1.0.0 |
| ORM | MyBatis-Plus 3.5.7 |
| 分布式锁 | Redisson 3.36.0 |
| 数据库 / 缓存 / 消息 | MySQL 8 · Redis 7 · RabbitMQ 3.13 |
| AI 模型 | DeepSeek（OpenAI 协议兼容） |

## 三、本地环境准备

### 1) WSL Ubuntu：启动中间件

```bash
# 在 WSL 内执行（Redis → 6380、Nacos 8848、RabbitMQ 5672/15672、初始化 5 个库）
bash scripts/wsl-middleware.sh start

# 查看状态 / 停止
bash scripts/wsl-middleware.sh status
bash scripts/wsl-middleware.sh stop
```

> MySQL 账号密码通过环境变量传入：`MYSQL_USER=root MYSQL_PASSWORD=xxx bash scripts/wsl-middleware.sh start`

### 2) Windows：构建后端

```bash
bash scripts/mvn.sh              # clean install（全模块 + 单测）
bash scripts/mvn.sh clean test   # 仅跑测试
```

> 本机 PATH 中的 `mvn` 已损坏（classworlds 报错），`scripts/mvn.sh` 直接用 java 启动 Maven，请勿改用裸 `mvn` 命令。

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
