# devlog · 2026-09-17 · 失败响应弹窗只展示 message；修复网络抖动误登出

## 一、失败弹窗展示详情（用户反馈）

### 现象
操作失败弹窗展示「详细信息」区（展开为 `{code, message, status}` JSON）与「复制」按钮，用户要求失败响应只把 message 展示给页面，不展示详情。

### 根因
`notifyError`（app-logic.ts:86）在开发环境把错误对象 `JSON.stringify(e, null, 2)` 塞进 `detail`，弹窗模板 `v-if="notify.detail"` 渲染「详细信息 + 复制」区块。

### 修复
`notifyError` 不再生成 detail，只保留 `console.warn` 供开发排障；`openNotify` 不传 detail → 模板详情区自动不渲染。

### 验证
- 实拍：错误弹窗仅显示「操作失败 / message / 知道了」，无详细信息区与复制按钮。
- 业务 4xx/5xx 与网络异常共用 `notifyError` 同一入口，行为一致。
- 顺带验证 AI 多日规划采纳链路正常（采纳成功、列表移除、无异常）。

## 二、网络抖动误登出（验证过程中发现）

### 现象
gateway 重启/瞬时网络抖动时，前端连续报「网络异常」并**被登出回登录页**。

### 根因
`refreshTokens()` 的 `.catch` 无条件 `clearTokens()`——网络错误（fetch TypeError，无 code/status）与令牌业务失效（40103）一视同仁，网络抖动清掉令牌 → 触发 `sessionExpired` 登出。

### 修复
`.catch` 区分：仅令牌业务失效（40101/40102/40103 或 4xx）才 `clearTokens()`；网络瞬时错误保留令牌，下次请求重试。

### 验证
- 修复后登录、织历 9 月数据加载、AI 采纳链路全部正常（view=all 200、无 console 错误）。
- 弹窗仍只显示 message。
