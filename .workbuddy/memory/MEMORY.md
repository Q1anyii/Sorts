# 梭子 SORTS · 项目长期约定

## 项目身份
- 名称：梭子 SORTS（智能日程管理平台）
- 主题：**光阴似箭，日月如梭**（设计规范见 `docs/theme-design.md`，选定「织锦流光」方向）
- 架构：微服务，请求 → 网关（鉴权 + 限流）→ 各微服务；跨模块调用用 OpenFeign，禁止跨库直连
- 契约来源：`api-spec.json`（开发接口前必须先对齐）

## 强制规范
1. **构建**：必须用 `bash scripts/mvn.sh`（本机 `mvn` 已损坏），构建后确认单测全绿
2. **命名**：模块与 `spring.application.name` 一律 `sorts-` 前缀；包名 `com.sorts.*`
3. **提交**：Conventional Commits（`feat(scope): subject`）；**每完成一个功能点提交一次**
4. **推送**：每个模块单测通过后推送到 `https://github.com/Q1anyii/sorts.git`
5. **测试**：每个模块必须配套单元测试，与业务代码同步交付
6. **配置**：密钥/地址一律环境变量，禁止硬编码；对外统一返回 `Result<T>` + `ErrorCode`
7. **分层**：controller → service（接口 + impl）→ mapper；跨服务调用走 `client`（Feign）

## 环境
| 资源 | 地址 | 备注 |
|---|---|---|
| MySQL | localhost:3306 | WSL，库：sorts_user / sorts_schedule / sorts_ai / sorts_notification / sorts_mall |
| Redis | localhost:**6380** | 约定默认端口+1；实际当前跑在 6379，需迁移 |
| Nacos | localhost:8848 | 未安装，用 `scripts/wsl-middleware.sh start` |
| RabbitMQ | localhost:5672 / 15672 | 未安装，同上 |
| 网关 | localhost:8080 | 服务端口 8081-8085 |

- WSL 命令被沙箱拦截，中间件相关操作需用户手动执行脚本
- Maven 受管版本：`~/.workbuddy/binaries/maven/apache-maven-3.9.16`；`~/.m2/settings.xml` 已配阿里云镜像
- 版本矩阵：Boot 3.3.4 / Cloud 2023.0.3 / SCA 2023.0.3.2 / MyBatis-Plus 3.5.7 / JDK 编译目标 17
- AI 模型：DeepSeek（OpenAI 协议兼容）

## 踩坑记录
- **MyBatis-Plus + Mockito**：`insert(any(X.class))` 会因 `insert(T)`/`insert(Collection<T>)` 重载产生编译歧义，须用 `ArgumentMatchers.<X>any()` 或 AtomicReference 捕获
- **Git 推送**：账户 Q1anyiii 已封停（勿用 SSH）；Q1anyii 的 PAT 需要 **Contents: Read and write** 权限
