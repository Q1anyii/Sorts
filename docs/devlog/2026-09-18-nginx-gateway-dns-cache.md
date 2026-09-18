# Devlog · 2026-09-18 · nginx 反代网关 IP 缓存导致 /api 全 502

## 现象

重启电脑（WSL2 升级 + Windows 重启）后，网页能打开，但**所有 `/api/*` 请求返回 502 Bad Gateway**，前端统一弹「操作失败 · 网络异常」。

## 定位过程

1. WSL 内 `curl localhost:8088` = 200、`curl localhost:8080/actuator/health` = 200 → **服务本身正常**。
2. `docker exec sorts-frontend curl http://gateway:8080/actuator/health` = 200 → **容器间网络可达**。
3. 但浏览器经 nginx 反代 `/api/` 全部 502，且 `GET /api/v1/users/me`、`POST /api/v1/auth/login` 均 502。
4. 怀疑 nginx `proxy_pass http://gateway:8080` 的 **upstream 主机名只在启动时解析一次并缓存**。
5. 时间线佐证：网关容器因重启被 Docker 重新创建（`Up 17 minutes`、创建于 1 小时前），其容器 IP 可能已变化；前端容器若早于网关就绪（compose `depends_on: service_started`），启动时解析到旧 IP 并缓存 → 之后一直连旧地址 → 502。
6. 验证性修复：`docker restart sorts-frontend` 后登录接口立即恢复 200 → 确认根因。

## 根因

nginx 对 `proxy_pass http://<hostname>` 的 upstream 解析时机是**配置加载时**（无 `resolver` 时仅解析一次）；网关容器重建后 IP 变化，nginx 仍连旧 IP，产生 502。重启电脑后容器恢复顺序不定，此问题随机复现。

## 修复

### 1. `docker/nginx.conf` — 动态 DNS 解析（根治）

```nginx
# Docker 内置 DNS：让 proxy_pass 变量每 30s 重新解析网关容器地址
resolver 127.0.0.11 valid=30s ipv6=off;

location /api/ {
    set $gw http://gateway:8080;   # 变量方式 → 每个请求动态解析
    proxy_pass $gw;                # 无 URI 部分，请求原样转发，行为与之前一致
    ...
}
```

要点：`proxy_pass` 使用变量时 nginx 不再缓存主机名，每个请求（按 `resolver` TTL）经 Docker DNS（127.0.0.11）重新解析 `gateway` 容器地址；`set` + 无 URI 的 `proxy_pass $gw` 保持原 `/api/**` 路径转发，SSE 相关头不变。

### 2. `docker/compose.yml` — 启动顺序双保险

```yaml
frontend:
  depends_on:
    gateway: { condition: service_healthy }   # 由 service_started 收紧为 healthy
```

保证前端容器在网关健康后才启动，避免“前端先起、网关后重建”的初始解析窗口。

## 验证

- `docker exec sorts-frontend nginx -t`：语法 OK。
- 容器内 `cat /etc/nginx/conf.d/default.conf`：`resolver 127.0.0.11` 与 `set $gw` 已生效。
- 浏览器实测 `POST /api/v1/auth/login` → **HTTP 200**，`code:0`。
- 页面 19 个 `/api/*` 请求全部成功，无 502。

## 影响面

- 仅前端 nginx 配置与 compose 依赖条件，后端无改动。
- 前端容器需重建（镜像 `sorts/frontend:local` 已重建并部署）。

## 后续

- 重启电脑后如仍偶发 502（理论概率极低），重启一次 `sorts-frontend` 即可恢复。
