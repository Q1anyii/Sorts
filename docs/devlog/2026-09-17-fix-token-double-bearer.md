# Devlog · 2026-09-17 · 修复登录后所有请求 40102「令牌非法」（Bearer 双重前缀）

## 现象

前端登录/注册成功后，进入业务页面所有受保护请求立即失败：

```
{"code":40102,"message":"令牌非法","data":null}
```

浏览器被踢回 `/login?redirect=/today`，无法使用应用。

## 根因

令牌在「签发 → 存储 → 携带」链路上被拼了**两次** `Bearer `：

| 环节 | 行为 |
|---|---|
| 后端 `AuthServiceImpl.issueTokens` | `TokenVO.accessToken = AuthConstants.TOKEN_PREFIX + jwt`（`"Bearer "` + JWT） |
| 前端 `tokenStore.save` | 原样存入 localStorage → `tokenStore.access === "Bearer eyJ..."` |
| 前端 `http.ts` 请求拦截器 | 无条件再拼一次：`Authorization: Bearer ${access}` → `"Bearer Bearer eyJ..."` |
| 网关 `AuthGlobalFilter.resolveToken` | 只剥一次 `"Bearer "` → 拿到 `"Bearer eyJ..."` → JWT 解析失败 → 40102 |

`api-spec.json` 契约明确定义 `accessToken` 为「JWT Access Token」（裸 JWT，不含认证方案前缀），**后端拼前缀违约**；前端对已带前缀的令牌也无防御，双重前缀直接触发。

## 修复（双端）

1. **后端（根因，对齐契约）**：`AuthServiceImpl.issueTokens` 返回裸 `accessToken`；同步更新 `AuthServiceImplTest` 断言（`startsWith("eyJ")` 且 `!startsWith("Bearer ")`），并移除不再使用的 `AuthConstants` 导入。
2. **前端（防御）**：`http.ts` 拦截器改为幂等——令牌已带 `Bearer ` 前缀则原样发送，否则才拼接，兼容存量 localStorage 中的旧前缀令牌。

## 验证

- `mvnw -pl sorts-user -am test` → sorts-common + sorts-user 全量单测通过（含更新后的断言）。
- 重建并部署 user / frontend / gateway 容器后，走 nginx 8088 全链路实测：
  - `POST /api/v1/auth/login` → `accessToken` 为裸 JWT（`startsWith("Bearer ")=false`，`startsWith("eyJ")=true`）。
  - `GET /api/v1/users/me`（`Authorization: Bearer <裸JWT>`）→ `code:0` + 用户信息，HTTP 200。此前正是这条链路 40102。

## 备注

- 网关侧无需改动：`resolveToken` 本就按「单个 `Bearer ` 前缀」设计，现在两端都与它对齐。
- 存量浏览器 localStorage 里的旧 `"Bearer eyJ..."` 令牌在本次修复后仍可用（前端幂等 + 令牌未过期），无需手动清除；若已过期，重新登录即可。
