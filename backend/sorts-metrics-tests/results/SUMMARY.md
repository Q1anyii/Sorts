# 梭子安全机制指标测试结果

- **执行时间**：2026-09-18 17:44（Asia/Shanghai）
- **执行方式**：`mvn test -pl sorts-metrics-tests -am`（backend 父工程，JDK 22 编译目标 release 17，Maven 3.9.15）
- **构建结果**：BUILD SUCCESS
- **测试总数**：25，失败 0，错误 0，跳过 0

## 分组成绩

| 组 | 覆盖机制 | 用例数 | 结果 | 关键指标 |
| --- | --- | --- | --- | --- |
| ① DualKeyPermission | AI 写工具声明级过滤 | 6 | ✅ 全过 | 2×2 矩阵仅 (服务端开关 ∧ 用户授权) 1/4 放行；未授权写调用不执行（写副作用 0）|
| ② StateMachine | 五态状态机 | 4 | ✅ 全过 | 全矩阵 30 格（13 合法 + 17 非法）逐格校验；非法跃迁 100% 拒绝；终态冻结 |
| ③ PlanRange | AI 多日规划相对日期 | 5 | ✅ 全过 | 数据集 9 例全命中（含跨年「下周」→ 2027-01-04）；未来一周 7 天连续无断档 |
| ④ ConcurrentPurchase | 兑换并发扣减 | 4 | ✅ 全过 | 50 并发抢 10 库存：成功 10、库存 0、**超卖 = 0**；售罄补偿成对、锁繁忙 429、落库失败退积分 |
| ⑤ GatewayGuard | 网关统一收口 | 6 | ✅ 全过 | 无/过期 token 100% 401；有效 token 注入 X-User-Id；伪造 X-Internal-Token 剥离率 100%；限流 key 登录按用户 / 未登录按 IP |

## 关键执行证据

### ④ 并发正确性（D1：50 并发抢 10 库存）
- 断言：`成功数 ≤ 10`、`库存 ≥ 0`、`成功数 + 剩余库存 = 10`（无超卖且无丢失）——**通过**
- 仿真口径：Redisson 锁 mock 为「恒可获取」（模拟锁租期提前失效的最坏场景），并发正确性完全由
  `UPDATE t_mall_item SET stock = stock - 1 WHERE id = #{id} AND stock > 0` 条件更新兜底（内存 CAS 语义与真实 SQL 一致）。

### ② 状态机真实语义（与 README/API 契约对齐）
- 合法起点：`startableFrom={PENDING, PAUSED}`、`pausableFrom={IN_PROGRESS}`、
  `endableFrom={PENDING, IN_PROGRESS, PAUSED}`、`cancellableFrom={PENDING, IN_PROGRESS, PAUSED, TIMEOUT}`
- TIMEOUT 为第六态：`isFinal=true`，仅保留「取消」入口（不可开梭/落梭）——数据集与断言已按真实实现校准。

### ⑤ 伪造内部凭证剥离
- 白名单放行路径与鉴权路径均断言：下游请求头 **不含 X-Internal-Token**。

## 产物结构

```
sorts-metrics-tests/
├── pom.xml                         # 独立测试模块（仅 test scope 依赖，不产出业务构件）
├── datasets/                       # 测试数据集（JSON）
│   ├── dual-key-matrix.json        # 双钥匙 2×2 矩阵
│   ├── state-machine-matrix.json   # 六态 × 五动作合法跃迁表
│   ├── plan-range-dataset.json     # 相对日期解析样例（含跨年）
│   └── concurrency-params.json     # 兑换并发参数与断言指标
├── results/                        # 测试结果（Surefire 报告 + 本汇总）
│   ├── SUMMARY.md
│   ├── TEST-*.xml                  # 结构化报告
│   └── *.txt                       # 文本报告
└── src/test/java/com/sorts/metrics/
    └── MetricsIndicatorTest.java   # 单一测试文件，5 组 @Nested 指标测试
```

## 复现命令

```bash
cd backend
mvn test -pl sorts-metrics-tests -am
# 或仅测试模块（依赖已安装时）：
mvn -f sorts-metrics-tests/pom.xml test
```

## 与现有测试体系的关系

- 独立于各业务模块 `src/test`（42 个既有测试类），不污染主构建；
- 复用真实实现类（ToolRegistry / PlanRangeDetector / ScheduleStatus / PurchaseServiceImpl / AuthGlobalFilter / RateLimitConfig），
  未复制业务逻辑；并发正确性以「条件更新语义仿真」标注口径，未引入 Testcontainers（免 Docker）。
