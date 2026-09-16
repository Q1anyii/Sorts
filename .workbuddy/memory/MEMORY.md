# 梭子 SORTS · 项目长期约定

## 项目身份
- 名称：梭子 SORTS（智能日程管理平台）
- 主题：**光阴似箭，日月如梭**（设计规范见 `docs/theme-design.md`，选定「织锦流光」方向）
- 架构：微服务，请求 → 网关（鉴权 + 限流）→ 各微服务；跨模块调用用 OpenFeign，禁止跨库直连
- 契约来源：`api-spec.json`（开发接口前必须先对齐）

## 强制规范
1. **构建**：AI 沙箱内必须用 `bash scripts/mvn.sh`；用户终端 / IDEA / WSL / CI 用 `backend/mvnw`。构建后确认单测全绿
2. **命名**：模块与 `spring.application.name` 一律 `sorts-` 前缀；包名 `com.sorts.*`
3. **提交**：Conventional Commits（`feat(scope): subject`）；**按功能分段提交——一个功能一次提交，不要攒批**
4. **推送**：GitHub 推送由**用户本人**执行；AI 只负责本地开发 + 本地提交（远端名为 `sorts`，非 origin）
5. **测试**：每个模块必须配套单元测试，与业务代码同步交付
6. **配置**：密钥/地址一律环境变量，禁止硬编码；对外统一返回 `Result<T>` + `ErrorCode`
7. **分层**：controller → service（接口 + impl）→ mapper；跨服务调用走 `client`（Feign）
8. **时长口径**：日程/统计对外一律「秒」（`actualDuration`、`t_time_record.duration`、统计的 totalFocusTime/totalDuration）；仅 `plannedDuration` 是分钟

## 环境
| 资源 | 地址 | 备注 |
|---|---|---|
| MySQL | localhost:**3307** | WSL Docker（root / sorts_dev）；库：sorts_user / sorts_schedule / sorts_ai / sorts_notification / sorts_mall |
| Redis | localhost:**6380** | 约定默认端口+1，WSL Docker |
| Nacos | localhost:8848 | WSL Docker，单机免鉴权 |
| RabbitMQ | localhost:5672 / 15672 | WSL Docker（sorts / sorts_dev） |
| 网关 | localhost:8080 | 服务端口 8081-8085 |

- 中间件一键启动（在 WSL 内执行）：`bash scripts/wsl-middleware.sh start`

- WSL 命令被沙箱拦截，中间件相关操作需用户手动执行脚本
- Maven 受管版本：`~/.workbuddy/binaries/maven/apache-maven-3.9.16`；`~/.m2/settings.xml` 已配阿里云镜像
- Maven Wrapper 已入库：`backend/mvnw`、`mvnw.cmd`、`.mvn/wrapper/maven-wrapper.properties`（阿里云镜像源）
- IDE：IntelliJ IDEA 2025.3；已注册 JDK **`ms-17`** = `E:/Java/java17`；共享运行配置在 `.run/`（见 `docs/ide-setup.md`）
- 版本矩阵：Boot 3.3.4 / Cloud 2023.0.3 / SCA 2023.0.3.2 / MyBatis-Plus 3.5.7 / JDK 编译目标 17
- AI 模型：DeepSeek（OpenAI 协议兼容）

## 踩坑记录
- **`mvn` 报 classworlds ClassNotFoundException ≠ mvn 坏了**（2026-09-16 更正误诊）：
  本机 `mvn -v` → `Apache Maven 3.9.15 @ E:\develop`，**完全正常**。真实原因是 WorkBuddy 沙箱给 shell 注入了
  `MSYS_NO_PATHCONV=1` + `MSYS2_ARG_CONV_EXCL=*`，关闭了 MSYS 路径转换；Maven 的 `bin/mvn` 是 POSIX sh 脚本，
  把 `/c/Users/.../plexus-classworlds.jar` 直接交给原生 `java.exe`，Windows java 解析不了。
  对照验证：`mvn -v` 失败，`env -u MSYS_NO_PATHCONV -u MSYS2_ARG_CONV_EXCL bash -c 'mvn -v'` 成功。
  → 沙箱内用 `scripts/mvn.sh`（显式 cygpath 转换）；其余场景用 `backend/mvnw`
- **IDEA 打开仓库根目录不会自动导入 `backend/pom.xml`**（容器目录 + Maven 在子目录的结构）：
  表现为没有 Maven 面板、源码全飘红、没有 Run 按钮 → 右键 `backend/pom.xml` → Add as Maven Project
- **IDEA 项目 SDK 不能设 JDK 26**：Lombok 1.18.34 对 JDK 23+ 支持不完整，编译期会崩。
  本机 IDEA 已注册的 17 名称为 **`ms-17`**（`E:/Java/java17`），必须用它
- **MyBatis-Plus + Mockito**：`insert(any(X.class))` 会因 `insert(T)`/`insert(Collection<T>)` 重载产生编译歧义，须用 `ArgumentMatchers.<X>any()` 或 AtomicReference 捕获
- **MyBatis-Plus 逻辑删除**：配置了 `logic-delete-field: deleted` 后不能 `setDeleted(1)` + `updateById`（逻辑删除字段被排除在 SET 之外），必须 `deleteById`
- **MyBatis-Plus 分页**：必须显式注册 `PaginationInnerInterceptor`，否则 `selectPage` 不拼 LIMIT，会退化成全表查询
- **MP wrapper 的 `getSqlSegment()`** 在纯 Mockito 单测（无 MyBatis 上下文）会抛「can not find lambda cache」→ 把 SQL 片段拼装抽成包级可见纯函数当测试接缝
- **Git 推送**：账户 Q1anyiii 已封停（勿用 SSH）；Q1anyii 的 PAT 需要 **Contents: Read and write** 权限（当前由用户自行推送）
- **同文件批量 Edit 有丢失风险**：一条消息内对同一文件发多个 Edit，出现过只落最后一条的情况，改完要 grep 校验
