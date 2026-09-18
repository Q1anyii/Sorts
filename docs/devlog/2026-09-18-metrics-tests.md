# Devlog 2026-09-18 · 指标测试工程落地

## 背景

用户要求为 README 五段安全机制描述（AI 双钥匙、五态状态机、AI 多日规划、Redisson 兑换并发扣减、网关统一收口）
生成**对应指标测试**：单独维护一个测试文件 + 测试数据集 + 测试结果，并**实际执行一遍**。

## 交付物

- 新模块 `backend/sorts-metrics-tests/`（加入父 pom modules，仅 test scope 依赖，不产出业务构件）
- 单测试文件 `MetricsIndicatorTest.java`：5 组 `@Nested` 指标测试（25 用例）
- 测试数据集 `datasets/*.json`（4 份：双钥匙矩阵 / 状态机矩阵 / 日期解析样例 / 并发参数）
- 测试结果 `results/`（Surefire XML + txt + SUMMARY.md）
- **执行结果：25/25 通过，BUILD SUCCESS**

## 关键问题与修复

### 1. Spring Boot repackage 导致依赖方找不到类
- **现象**：独立测试模块编译报「程序包 com.sorts.ai.tool 不存在」，但构件已 install 成功。
- **根因**：服务模块 install 的是 Spring Boot 可执行 fat jar（类在 `BOOT-INF/classes/`），非普通 jar。
- **解决**：`mvn test -pl sorts-metrics-tests -am` 让依赖模块在 reactor 内以 `target/classes` 参与构建，
  绕开 fat jar 依赖问题（`-Dspring-boot.repackage.skip=true` 经实测对该版本插件不生效）。

### 2. 测试断言与真实实现语义校准（3 处）
- TIMEOUT 状态：`isFinal=true` 但 `cancellableFrom` 含 TIMEOUT（仅保留取消入口），不可开梭/落梭 —— 数据集与断言跟随真实代码。
- 售罄路径：真实链路为「扣积分 → 落库发现售罄 → 退积分」，补偿成对（净 0），非「不扣积分」—— 断言改为补偿一致性。
- 包路径校准：`ToolCall`/`ToolDefinition` 位于 `com.sorts.ai.llm.protocol`，`ToolJsonCodec` 位于 `com.sorts.ai.tool.support`，
  `RateLimitConfig` 位于 `com.sorts.gateway.config`。

### 3. 构建环境
- 本机 WSL 无 JDK；Windows 侧 JDK 22（`E:\Java\java22`）以 `maven.compiler.release=17` 编译，兼容 Java 17 目标。

## 指标达成

| 机制 | 关键指标 | 结果 |
| --- | --- | --- |
| 双钥匙 | 写工具放行组合 = 仅 (on, true) 1/4 | ✅ 6/6 |
| 状态机 | 非法跃迁 100% 拒绝、终态冻结 | ✅ 4/4 |
| 多日规划 | 相对日期解析 100%（含跨年）、7 天连续 | ✅ 5/5 |
| 兑换并发 | 50 并发抢 10 库存超卖 = 0（锁失效最坏场景） | ✅ 4/4 |
| 网关 | 401 / 伪造凭证剥离 / 限流 key 策略 | ✅ 6/6 |

## 备注

- 并发正确性以「条件更新语义仿真（内存 CAS）」标注口径，未引入 Testcontainers（免 Docker、可离线复现）。
- 提交：先提交不推送（遵循仓库约定）。
