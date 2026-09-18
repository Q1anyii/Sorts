# 开源贡献指南

欢迎提交 Issue 和 Pull Request。为保证协作顺畅，请遵循以下约定：

## 分支与提交

- 主干分支为 `main`，功能开发请开独立分支，完成后提 PR 合入
- 提交信息采用 **Conventional Commits**：`feat(scope): subject`，常用类型 `feat` / `fix` / `refactor` / `docs` / `test` / `chore`
- **按功能分段提交——一个功能一次提交，不要攒批**，便于回溯与 cherry-pick

## 代码规范

- 分层固定为 `controller → service（接口 + impl）→ mapper`；跨服务调用统一放 `client`（Feign），**禁止跨库直连**
- 模块名与 `spring.application.name` 一律 `sorts-` 前缀，包名统一 `com.sorts.*`
- 对外响应统一 `Result<T>` + `ErrorCode`，不要自定义响应结构
- 密钥、地址一律走环境变量，**禁止硬编码**
- 新增内部接口必须放 `/internal/**` 并标 `@InternalApi`，同时同步 `sorts.internal.paths`
- 日程与统计的时长对外口径一律「秒」，仅 `plannedDuration` 用「分钟」

## 测试与提交前自检

- **每个模块必须配套单元测试**，与业务代码同步交付
- 提交前请确认：

```bash
cd backend && ./mvnw clean install   # 全模块构建 + 单测全绿
cd frontend/vite-app && npm test && npm run build   # 存档 Vite 工程
```

- 改接口前先对齐 `api-spec.json`；与契约有意偏差时，在 `docs/PROGRESS.md` 的决策记录里备案

## 文档

- 环境、端口、命令、环境变量见 [docs/dev-setup.md](docs/dev-setup.md)
- IDEA 导入、JDK、运行配置见 [docs/ide-setup.md](docs/ide-setup.md)
- 里程碑、关键决策与已知限制见 [docs/PROGRESS.md](docs/PROGRESS.md)
- 主题规范见 [docs/theme-design.md](docs/theme-design.md)
