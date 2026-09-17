# devlog · 2026-09-17 · 修复：织历整月空白（sessionExpired ReferenceError 中断初始化）

## 现象

用户反馈「九月包含了日期但是未显示」：织程页 2026-09-17 有 6 条日程，织历 2026年9月 整月无任何调色板色块/任务标记，且 9/17 被选中也空白。

## 排查过程

1. 数据层排查：`GET /schedules?view=all&pageSize=500`（织历数据源）接口正常返回；查库 user(id=6) 742 条日程，按 plannedStartTime asc 前 500 条已覆盖到 2026-11-13——9 月 93 条必然在内，**排除 pageSize 截断**。
2. 浏览器实测（bu）：`/schedules?view=all` 请求全部 200，但 **console 报 `ReferenceError: sessionExpired is not defined`**，且日历主体只有顶部控件、日期格未渲染。
3. 代码定位：`apiFetch`（**模块级**函数，app-logic.ts:135）在续期失败分支直接调用 `sessionExpired()`，而 `sessionExpired` 定义在 **`setup()` 内部**（:847）——模块级作用域不可见 → ReferenceError。

## 根因

- 模块级 HTTP 封装（`apiFetch`）引用了 setup 闭包内的 `sessionExpired`。
- 该路径只在「token 失效 → 续期失败」时触发，所以此前 token 有效时一切正常；**用户浏览器里 refresh token 失效后第一次触发就崩**。
- ReferenceError 沿初始化链（loadAllData）传播 → 后续数据加载（含织历 allSchedules）未执行 → 日历格子空白；布局模板已渲染所以页面不白屏，表现为「有日期无内容」。

## 修复

采用与 SSE 函数（`handlers.onAuthExpired`）一致的**回调注册**模式，模块级不再直接引用 setup 内符号：

- 模块级新增 `let sessionExpiredHandler = null` + `onSessionExpired(fn)`。
- `apiFetch` 续期失败分支改为 `if (sessionExpiredHandler) sessionExpiredHandler();`。
- `setup()` 内 `sessionExpired` 定义后立即 `onSessionExpired(sessionExpired)` 注册。

## 验证

- Vitest 19/19 通过（datetime/status/error/sse 四组）。
- 浏览器实测（bu）：
  - 修复前 token 失效场景：ReferenceError 消失，**干净登出**回登录页（预期行为，不再卡半渲染）。
  - probe_ok 重新登录后：织历 2026年9月 正常渲染，9/17 蓝色选中框、9/18 有日程色块。
- 织历空白问题随初始化链恢复而解决。

## 遗留/注意

- 排查中发现用户浏览器侧 refresh token 已失效（续期被拒）——这是触发该 bug 的导火索，登出重登即可。
- 建议后续审计模块级函数中是否还有其他 setup 内符号引用（本轮 grep 确认仅 sessionExpired 一处）。
