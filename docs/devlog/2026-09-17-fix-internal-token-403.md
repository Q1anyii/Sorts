# 2026-09-17 · 修复：锦市购买 403「无权访问该资源」（内部凭证从不附加）

## 现象

- 用户访问 `POST /api/v1/mall/purchase` 返回 `{"code":403,"message":"无权访问该资源"}`；
- **带有效 JWT 也 403**——不是网关鉴权问题（无 token 时网关正确返回 401）。

## 排查链路

1. 实测四场景：无 token GET → 401；带 token GET → 500；**带 token POST → 403**（稳定复现）。
2. 403 文案唯一来源是 `sorts-common` 的 `ErrorCode.FORBIDDEN`，由 `InternalApiInterceptor` 在**入站凭证校验失败**时抛出。
3. mall 日志：`MallItemMapper`/`WardrobeItemMapper` 正常执行后抛 403；user 日志铁证：
   `InternalApiInterceptor : 内部接口 /api/v1/users/points/change 凭证校验失败`。
4. 两容器 `INTERNAL_TOKEN` 一致（`sorts-internal-dev-token`），排除环境变量不一致。
5. 查 `t_points_log`：只有种子追加记录，**购买扣减与落梭奖励从未出现过**——说明这不是本次回归，而是**从上线起内部调用从未成功**（购买链路从未跑通过）。
6. 给 `InternalFeignInterceptor` 加诊断日志后实测：
   `[internal-feign] 路径不在白名单，不附加凭证：path=/points/change, url=/points/change`。

## 根因

`RequestTemplate.path()`（及 `url()`）在 `RequestInterceptor.apply()` 阶段**只包含方法级路径**（如 `/points/change`），
`@FeignClient(path = "/api/v1/users")` 的前缀要到请求发出前才由 `Target` 拼接。

原 `matches()` 只拿 `template.path()` 与白名单（`/internal/`、`/api/v1/users/points/change`）比较：
`/points/change` 既不 `startsWith` 也不 `contains` 白名单项 → **凭证从不附加** →
对端 `InternalApiInterceptor` 一律 403，且 `deductPoints` 会把 403 原样透传给前端。

影响面：**所有带 `@FeignClient(path=...)` 前缀的内部调用**（mall→user 扣积分、schedule→user 落梭发积分、
ai→user/schedule、notification→schedule 等）都受影响；单测此前只覆盖「方法路径即完整路径」的场景，未覆盖该拆分场景。

## 修复

`backend/sorts-common/src/main/java/com/sorts/common/internal/InternalFeignInterceptor.java`

- `matches()` 增加三路判断：方法级 path / 原始 url / **`Target.url()`（含 client path）+ 方法路径拼接后的完整 URL**；
- `Target.url()`（如 `http://sorts-user/api/v1/users`）需再拼 `path`（`/points/change`）才是白名单要匹配的完整路径；
- 新增 `resolveTargetUrl()` / `joinPath()` 辅助，容错斜杠缺失/重复；保留原有「方法路径直接命中」判断（`/internal/**` 场景不受影响）；
- 保留 DEBUG 级诊断日志（`com.sorts` 默认 debug，生产可调）。

## 验证（全部实测）

| 场景 | 修复前 | 修复后 |
|---|---|---|
| `POST /mall/purchase`（probe_ok, 商品3） | 403 无权访问该资源 | **code=0 购买成功**：purchase_id=1、扣 80、余额 4920、装扮入库、积分流水「锦市消费」 |
| 重复购买 | — | 409「已拥有该装扮」（业务正确拦截） |
| 落梭（今日过期 PENDING → start → end） | 无积分（静默） | **+5 落梭奖励**，余额 4925 |
| 诊断日志 | 路径不在白名单 | 已为 path=/points/change 附加内部凭证 |

回归单测：`InternalFeignInterceptorTest` 新增 2 例
（client path 前缀拆分仍命中白名单 / 非白名单方法路径拼接后仍不附加）。

## 变更文件

- `backend/sorts-common/.../InternalFeignInterceptor.java`（修复）
- `backend/sorts-common/src/test/.../InternalFeignInterceptorTest.java`（回归用例）

## 遗留

- 存量数据无历史内部调用痕迹，无需对账；`t_purchase_record` 只有本次验证的 1 条。
- 其余服务（schedule/ai/notification/mall）已全部重建并加载修复后的 common。
