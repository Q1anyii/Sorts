# Devlog · 2026-09-17 · 修复前端登录/注册 405（API 基地址丢 `/api/v1`）

## 现象

前端站点（nginx 8088）加载正常，但登录/注册提交失败，nginx 返回：

```
405 Not Allowed  (nginx/1.27.5)
```

nginx 访问日志：

```
POST /auth/login   405
POST /auth/register 405
```

请求路径少了 `/api/v1` 前缀，被 `location /` 的静态兜底拦下（静态 location 不允许 POST）。

## 根因

`frontend/src/api/http.ts`：

```ts
const BASE_URL = (import.meta.env.VITE_API_BASE as string | undefined) ?? '/api/v1'
```

`docker/.env` 中 `VITE_API_BASE=` 为空字符串，Dockerfile 通过 `ENV VITE_API_BASE=$VITE_API_BASE` 把它作为**空字符串**注入构建环境，Vite 将其内联为 `import.meta.env.VITE_API_BASE === ""`。

`??` 只对 `null / undefined` 兜底，**对空字符串直接生效**，于是 `BASE_URL === ""`，axios 请求全部变为 `/auth/login`、`/auth/register` 这类根路径，nginx 静态 location 对 POST 返回 405。

与 `docker/compose.yml` 的约定「VITE_API_BASE 留空 = 走同源 /api/v1」相悖：留空本应触发兜底，实际却没有。

## 修复

`frontend/src/api/http.ts`：

```diff
- const BASE_URL = (import.meta.env.VITE_API_BASE as string | undefined) ?? '/api/v1'
+ const BASE_URL = import.meta.env.VITE_API_BASE || '/api/v1'
```

`||` 对空字符串同样兜底。`.env` 保持 `VITE_API_BASE=` 不用改，任何新克隆（`.env.example` 同为空）也不会再踩这个坑。

## 验证

- 全仓 `VITE_API_BASE` 引用仅 `env.d.ts`（类型声明）与 `http.ts`（本处）两处，无其他隐含假设。
- 重建前端镜像（`docker.sh web`）后，走 nginx 8088 实测：
  - `GET /` → 200（SPA 正常加载）
  - `POST /api/v1/auth/login`（错误密码）→ `{"code":401,"message":"用户名或密码错误"}`，已穿透 nginx → 网关 → sorts-user，不再 405
  - `POST /api/v1/auth/register`（新用户）→ `code:0` + 完整令牌，注册链路可用
- 期间遇到的 `code:500 系统繁忙` 是 PowerShell 5.1 调用 curl 时剥掉请求体双引号导致的 Jackson 解析失败（测试端问题），改用 payload 文件后消失，与本次修复无关。
- 涉及 SSRF 无；改动只影响 baseURL 拼装，`absoluteApiUrl`（SSE 用）同享该值，一并修复。

## 备注

- 不影响后端与网关：网关路由 `/api/v1/auth/**`、nginx `/api/` 反代均无需改动。
