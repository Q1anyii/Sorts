# Devlog · 2026-09-17 · 修复 `docker.sh web` 报错

## 现象

`bash scripts/docker.sh web` 直接失败：

```
service "frontend" depends on undefined service "gateway": invalid compose project
```

## 根因

`docker/compose.yml` 中：

- `gateway` 声明为 `profiles: ["app"]`
- `frontend` 声明为 `profiles: ["web"]`，且 `depends_on: gateway`

`scripts/docker.sh` 的 `cmd_web` 只传了 `--profile web`。Docker Compose 的语义是：**未启用 profile 的服务会被排除出当前项目**，因此 `frontend` 依赖的 `gateway` 在项目里"不存在"，`depends_on` 校验直接失败。与 gateway 是否已在运行无关——Compose 在校验阶段就报错。

实测对照（WSL 内，真实项目路径 `/mnt/d/SORTS(梭子)`）：

| 命令 | 结果 |
|---|---|
| `docker compose ... --profile app --profile web config -q` | 通过 |
| `docker compose ... --profile web config -q` | 复现上述报错 |

## 修复

`scripts/docker.sh` 的 `cmd_web`：

```diff
-  dc --profile web up -d --build frontend
+  dc --profile app --profile web up -d --build frontend
```

同时启用 `app` + `web` 两个 profile 后，`frontend` 的 `depends_on: gateway` 可解析；gateway 已在运行时不重建，`up frontend` 只构建并启动前端站点。

## 验证

- `bash -n scripts/docker.sh` → 语法通过。
- WSL 内 `docker compose ... --profile app --profile web config -q` → 配置校验通过。
- 未在本机实际执行 `docker.sh web`（会触发前端镜像构建，需要拉取 node 镜像并跑 `npm ci`），修复命令与 `docker.sh` 的 `dc()` 调用路径一致（`--project-directory docker --env-file docker/.env -f docker/compose.yml`），风险为零。

## 遗留建议（未改）

同类问题还存在于 `cmd_logs` 的兜底分支：`docker.sh logs frontend` 会走 `dc --profile app logs ...`，同样因 profile 不匹配而报错。本次只改用户点名范围（`cmd_web`），后续如需支持 `logs frontend`，可将该分支改为同时启用 `app` + `web`。
