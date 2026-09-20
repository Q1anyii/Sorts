# 梭子 SORTS · IDE 运行手册（IntelliJ IDEA）

> 目标：**clone / 检出后，在 IDEA 里点一下就能跑起来**（Run 按钮直接启动 8080/8081/8082）
> 适用：IntelliJ IDEA 2025.x（本机已验证 2025.3）

---

## 一、为什么现在在 IDEA 里跑不起来

本仓库是 **「容器目录 + Maven 工程在子目录」** 的结构：

```
D:\SORTS(梭子)\            ← 你当前用 IDEA 打开的是这里（不含 pom.xml）
├── backend\               ← 真正的 Maven 根工程在这里
│   ├── pom.xml
│   ├── sorts-common\
│   ├── sorts-gateway\
│   ├── sorts-user\
│   └── sorts-schedule\
├── frontend\  docs\  scripts\  api-spec.json
```

IDEA 打开根目录时，只会把它当成一个普通文件夹（`.idea/modules.xml` 里仅注册了一个空的根模块），**没有把 `backend/pom.xml` 导入成 Maven 项目**，所以：

- 没有 Maven 工具窗、没有依赖下载、没有 `target/classes`
- 源码里 `com.sorts.*`、`Result`、`@SpringBootApplication` 全部飘红
- 任何类都没有 Run 按钮（因为 IDEA 不知道它属于哪个模块）

次要问题两条：

| 问题 | 现状 | 影响 |
|---|---|---|
| 项目 SDK | 被设成了 **26** | 项目要求编译目标 17；JDK 26 上 Lombok / Spring Boot 3.3 易报错 |
| 运行配置 | 一个都没有 | 需要手点三次 Run 才能起服务（现已补上共享配置） |

---

## 二、三步让它在 IDEA 里跑起来

### 第 1 步：把 `backend/pom.xml` 导入为 Maven 项目（二选一）

**方式 A — 保持当前窗口（推荐，能同时看到 frontend/docs）**

1. 在项目树里右键 `backend/pom.xml`
2. 选 **Add as Maven Project**（有些版本叫 "Add as Maven Project / Link Maven Project"）
3. 打开右侧 **Maven** 工具窗 → 点 ⟳ 刷新，等待依赖下载完成

> 若右键没有该菜单：`File → Settings → Build, Execution, Deployment → Build Tools → Maven`，
> 在 **Maven Projects** 列表里点 `+` 添加 `D:\SORTS(梭子)\backend\pom.xml`。

**方式 B — 直接以 Maven 工程为项目根（最干净）**

`File → Open` → 选中 `D:\SORTS(梭子)\backend\pom.xml` → **Open as Project**。
IDEA 会把它识别为 Maven 多模块工程，`sorts-common / gateway / user / schedule` 自动成为四个模块。

> 本仓库的 `.idea/workspace.xml` 已写入 `originalFiles = $PROJECT_DIR$/backend/pom.xml`
> 并补了 `.run/` 共享运行配置；直接重开 IDEA 一般会自己完成导入，若没生效就手动点一次方式 A。

### 第 2 步：对齐 JDK 17

IDEA 里已注册的 17 叫 **`ms-17`**（对应 `E:\Java\java17`）。

- `File → Project Structure → Project`
  - **SDK**：`ms-17`（17）
  - **Language level**：`17 - Sealed types, always-strict floating-point semantics`
- `File → Project Structure → Modules` → 每个模块的 SDK 继承 Project SDK 即可（Maven 工程的 `maven.compiler.release=17` 已写在父 pom 里）

> 为什么不能直接用 26：父 pom 的 `lombok`（1.18.34）对 JDK 23+ 支持不完整，会在编译期抛
> `java.lang.NoSuchFieldError: com.sun.tools.javac.tree.JCTree$JCImport.qualid` 之类错误。

### 第 3 步：确认 Maven 与仓库配置

`File → Settings → Build, Execution, Deployment → Build Tools → Maven`：

| 项 | 建议值 | 说明 |
|---|---|---|
| Maven home path | **Bundled (Maven 3)** 或 `E:\develop\maven\apache-maven-3.9.16` | 本机两者都可用 |
| User settings file | `C:\Users\Qianyi\.m2\settings.xml`（已配阿里云镜像） | 不配会下载很慢 |
| Local repository | 默认 `~/.m2/repository` | |

> ⚠️ 不要选 "Use Maven wrapper" 以外的旧式 `cmd` 脚本；本仓库已带 Wrapper（见第六节），
> 选 **Use Maven wrapper (mvnw)** 也可以正常工作。

---

## 三、Lombok（必须，否则满屏飘红）

1. `Settings → Plugins` → 搜索 **Lombok** → 确保已启用（IDEA 2020.3+ 已内置，通常默认开启）
2. `Settings → Build, Execution, Deployment → Compiler → Annotation Processors`
   → 勾选 **Enable annotation processing**
3. 若 `@Data/@Slf4j` 仍不生效：`File → Invalidate Caches → Invalidate and Restart`

---

## 四、一键运行：已内置 4 个共享运行配置

`.run/` 目录已提交 4 个配置，导入 Maven 后会自动出现在右上角 Run 下拉框里：

| 配置名 | 主类 | 端口 |
|---|---|---|
| `SORTS · Gateway (8080)` | `com.sorts.gateway.GatewayApplication` | 8080 |
| `SORTS · User (8081)` | `com.sorts.user.UserApplication` | 8081 |
| `SORTS · Schedule (8082)` | `com.sorts.schedule.ScheduleApplication` | 8082 |
| `SORTS · 全部服务`（Compound） | 同时启动上面三个 | 8080/8081/8082 |

用法：右上角下拉选中 → 点 ▶ Run 或 🐞 Debug。
每个服务右侧都有 **Services 面板**，可看端口、日志、手动停止。

> 如果下拉框里配置的名称带「红色模块名」提示，说明模块名不一致：
> 打开配置 → 把 **Use classpath of module** 重新选成 `sorts-gateway` / `sorts-user` / `sorts-schedule` 即可。

> 📌 `.run/` 放在**仓库根目录**（对应「方式 A：以 `D:\SORTS(梭子)` 为项目根」）。
> 如果你改用「方式 B：以 `backend` 为项目根」，IDEA 只认 `<项目根>/.run/`，
> 把整个 `.run` 目录复制到 `backend/` 下即可（4 个小文件，模块名无需改）。

### 运行前先起中间件（否则只能启动，不能读写数据）

```bash
# 在 WSL 内执行
bash scripts/wsl-middleware.sh start     # Redis 6379 / MySQL 3307 / Nacos 8848 / RabbitMQ 5672
bash scripts/wsl-middleware.sh sql       # 首次或新增模块建表脚本后补执行
```

启动顺序：**Nacos → sorts-user → sorts-schedule → sorts-gateway**（网关要能发现后端服务）。
Nacos 没起时，服务会打一段注册失败日志但**仍然能启动**，只是网关路由不可用。

---

## 五、如何确认「导入成功」

- [ ] Maven 工具窗出现 `sorts-parent` 及 4 个子模块，依赖无红色波浪线
- [ ] `sorts-common` 等模块下的 `src/main/java` 被 IDEA 识别为 **Sources Root**（蓝色文件夹）
- [ ] `ScheduleApplication` 类名左侧出现绿色 ▶ 图标
- [ ] 终端执行 `./mvnw -q clean install` 或 `bash scripts/mvn.sh` 能 BUILD SUCCESS
- [ ] 运行 `SORTS · Schedule (8082)` 后日志出现 `Tomcat started on port 8082` 与 `Started ScheduleApplication`

---

## 六、构建命令对照（**重要更正**）

> 历史文档里写的「本机 mvn 已损坏」是**误诊**，真实原因见下。

| 场景 | 用什么 | 说明 |
|---|---|---|
| IDEA 内构建 / 运行 | IDEA 自带 Maven 集成 | 不受任何 shell 影响，**推荐** |
| 你自己的 Git Bash / PowerShell | `mvn` 或 `backend/mvnw` | 本机 `mvn -v` 输出 `Apache Maven 3.9.15`，**完全正常** |
| WSL / Linux / CI | `backend/mvnw` | 标准 Maven Wrapper，与本机是否装 Maven 无关 |
| **AI（WorkBuddy 沙箱）内** | `bash scripts/mvn.sh` | 沙箱 shell 禁用了路径转换，裸 `mvn` 会报 classworlds 错误 |

**真实根因**：WorkBuddy 沙箱给 shell 注入了
`MSYS_NO_PATHCONV=1` 与 `MSYS2_ARG_CONV_EXCL=*`，**关闭了 MSYS 的路径自动转换**。
Maven 的 `bin/mvn` 是 POSIX sh 脚本，它会把 `/c/Users/.../plexus-classworlds.jar` 这样的
POSIX 路径直接交给原生 `java.exe`，Windows 版 java 无法解析该路径，于是报：

```
错误: 找不到或无法加载主类 org.codehaus.plexus.classworlds.launcher.Launcher
原因: java.lang.ClassNotFoundException: org.codehaus.plexus.classworlds.launcher.Launcher
```

`scripts/mvn.sh` 之所以能用，是因为它**显式用 `cygpath` 把路径转成 `C:/...`** 再交给 java。

验证方法（在干净 shell 里做对照）：

```bash
# 沙箱 shell（转换被禁用）→ 失败
mvn -v
# 临时启用转换 → 成功，输出 Apache Maven 3.9.15
env -u MSYS_NO_PATHCONV -u MSYS2_ARG_CONV_EXCL bash -c 'mvn -v'
```

---

## 七、Maven Wrapper 说明

仓库已内置 Wrapper（仅脚本模式，不携带 jar）：

```
backend/
├── mvnw                  # Linux / macOS / Git Bash
├── mvnw.cmd              # Windows CMD / PowerShell
└── .mvn/wrapper/maven-wrapper.properties   # distributionUrl 指向阿里云镜像
```

首次执行会自动下载 Maven 3.9.16 到 `~/.m2/wrapper/dists/`，之后直接用：

```bash
cd backend
./mvnw clean install          # Git Bash / WSL
mvnw.cmd clean install        # CMD / PowerShell
```

---

## 八、常见报错速查

| 报错 | 原因 | 解决 |
|---|---|---|
| `ClassNotFoundException: org.codehaus.plexus.classworlds.launcher.Launcher` | MSYS 路径转换被禁用（沙箱内） | 用 `bash scripts/mvn.sh`；或临时 `env -u MSYS_NO_PATHCONV -u MSYS2_ARG_CONV_EXCL` |
| 源码里 `com.sorts.*` 全飘红 | `backend/pom.xml` 未导入 Maven | 按第二节第 1 步操作 |
| `无效的目标发行版: 17` / Lombok 编译期崩溃 | 项目 SDK 是 26 或其它 | 改成 `ms-17`（第二节第 2 步） |
| `Cannot resolve symbol 'lombok'` / getter 不存在 | 未开启注解处理 | 第三节 |
| `Connection refused: localhost:8848` | Nacos 未启动 | `wsl-middleware.sh start`；不影响服务启动 |
| `Table 'sorts_schedule.t_schedule' doesn't exist` | 建表脚本未执行 | `wsl-middleware.sh sql` |
| `Access denied for user 'root'@'localhost'` | 连到了宿主 3306 而不是容器 3307 | 确认 `MYSQL_PORT=3307`（默认值已写在 application.yml） |
| Run 下拉里的配置报模块无效 | 模块名不一致 | 重开配置，重选 classpath 模块 |

---

## 九、附：启动参数覆盖

各服务配置项都可用环境变量覆盖，IDEA 里填在运行配置的 **Environment variables**：

```
SCHEDULE_PORT=9082
MYSQL_HOST=localhost;MYSQL_PORT=3307;MYSQL_USER=root;MYSQL_PASSWORD=sorts_dev
REDIS_PORT=6379
NACOS_ENABLED=false        # 本地联调不想起 Nacos 时关掉服务注册
REWARD_ENABLED=false       # 关掉落梭奖励，避免依赖 sorts-user
```

> 最省事的本地单跑组合：`NACOS_ENABLED=false` + 只起 MySQL/Redis。
