package com.sorts.metrics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sorts.ai.config.DeepSeekProperties;
import com.sorts.ai.llm.protocol.ToolCall;
import com.sorts.ai.support.PlanRangeDetector;
import com.sorts.ai.tool.AiTool;
import com.sorts.ai.tool.ToolLevel;
import com.sorts.ai.tool.ToolRegistry;
import com.sorts.ai.tool.support.ToolJsonCodec;
import com.sorts.common.constant.AuthConstants;
import com.sorts.common.exception.BizException;
import com.sorts.common.result.ErrorCode;
import com.sorts.common.result.Result;
import com.sorts.gateway.config.RateLimitConfig;
import com.sorts.gateway.config.SortsGatewayProperties;
import com.sorts.gateway.filter.AuthGlobalFilter;
import com.sorts.mall.client.NotificationClient;
import com.sorts.mall.client.UserClient;
import com.sorts.mall.config.MallProperties;
import com.sorts.mall.dto.MallItemVO;
import com.sorts.mall.dto.PurchaseResultVO;
import com.sorts.mall.entity.MallItem;
import com.sorts.mall.mapper.MallItemMapper;
import com.sorts.mall.mapper.WardrobeItemMapper;
import com.sorts.mall.service.impl.PurchasePersister;
import com.sorts.mall.service.impl.PurchaseServiceImpl;
import com.sorts.schedule.enums.ScheduleStatus;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 梭子安全机制指标测试（独立工程，仅测试代码）。
 *
 * <p>覆盖 README 五段关键描述，每组给出量化指标：
 * ① 双钥匙权限模型（AI 写工具声明级过滤）
 * ② 六态状态机（非法跃迁 100% 拒绝）
 * ③ AI 多日规划（相对日期解析 + 连续覆盖）
 * ④ 兑换并发扣减（条件更新兜底，任意并发超卖 = 0）
 * ⑤ 网关统一收口（鉴权 / 伪造凭证剥离 / 限流 key 策略）
 *
 * <p>测试数据集位于 {@code datasets/}，测试结果由 Surefire 写入 {@code results/}。
 *
 * @author sorts
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MetricsIndicatorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ------------------------------------------------------------------
    // ① 双钥匙权限模型
    // ------------------------------------------------------------------
    @Nested
    @DisplayName("① 双钥匙权限模型：2×2 矩阵，仅 (服务端开关 ∧ 请求授权) 放行写工具")
    class DualKeyPermission {

        private AiTool readTool;
        private AiTool writeTool;

        private ToolRegistry registry(boolean serverAllowWrite) {
            readTool = mock(AiTool.class);
            when(readTool.name()).thenReturn("QuerySchedules");
            when(readTool.level()).thenReturn(ToolLevel.READ);
            when(readTool.description()).thenReturn("查询日程");
            when(readTool.parameters()).thenReturn(Map.of());

            writeTool = mock(AiTool.class);
            when(writeTool.name()).thenReturn("CreateSchedule");
            when(writeTool.level()).thenReturn(ToolLevel.WRITE);
            when(writeTool.description()).thenReturn("创建日程");
            when(writeTool.parameters()).thenReturn(Map.of());

            DeepSeekProperties props = new DeepSeekProperties();
            props.getTool().setAllowWrite(serverAllowWrite);
            return new ToolRegistry(List.of(readTool, writeTool), props, new ToolJsonCodec());
        }

        @Test
        @DisplayName("A1 双关：开关 off + 授权 false → 写工具不下发")
        void bothOff() throws Exception {
            ToolRegistry r = registry(false);
            assertThat(r.writeEnabled(false)).isFalse();
            assertThat(r.definitions(false).stream().anyMatch(d -> d.getFunction().getName().equals("CreateSchedule")))
                    .as("写工具不得下发").isFalse();
            assertThat(r.definitions(false).stream().anyMatch(d -> d.getFunction().getName().equals("QuerySchedules")))
                    .as("读工具仍全量下发").isTrue();
        }

        @Test
        @DisplayName("A2 服务端开关一票否决：开关 off + 授权 true → 仍不下发")
        void serverOffRequestOn() throws Exception {
            ToolRegistry r = registry(false);
            assertThat(r.writeEnabled(true)).isFalse();
            assertThat(r.definitions(true).stream().anyMatch(d -> d.getFunction().getName().equals("CreateSchedule")))
                    .as("服务端开关一票否决，写工具不得下发").isFalse();
        }

        @Test
        @DisplayName("A3 授权缺失：开关 on + 授权 false → 仍不下发")
        void serverOnRequestOff() throws Exception {
            ToolRegistry r = registry(true);
            assertThat(r.writeEnabled(false)).isFalse();
            assertThat(r.definitions(false).stream().anyMatch(d -> d.getFunction().getName().equals("CreateSchedule")))
                    .as("用户授权缺失，写工具不得下发").isFalse();
            assertThat(r.isWriteDenied("CreateSchedule", false)).as("预判为写操作被拒").isTrue();
        }

        @Test
        @DisplayName("A4 唯一放行：开关 on + 授权 true → 写工具下发，读工具不缺失")
        void bothOn() throws Exception {
            ToolRegistry r = registry(true);
            assertThat(r.writeEnabled(true)).isTrue();
            List<String> names = r.definitions(true).stream().map(d -> d.getFunction().getName()).toList();
            assertThat(names).as("唯一放行组合，写工具下发").contains("CreateSchedule");
            assertThat(names).as("读工具不缺失").contains("QuerySchedules");
            assertThat(r.isWriteDenied("CreateSchedule", true)).isFalse();
        }

        @Test
        @DisplayName("A5 声明级过滤：未授权写工具即使被模型调用也不执行（写副作用 0）")
        void invokeWriteWithoutAuthorizationIsBlocked() throws Exception {
            ToolRegistry r = registry(false); // 服务端开关关闭
            ToolCall call = mock(ToolCall.class);
            ToolCall.Function fn = mock(ToolCall.Function.class);
            when(call.getFunction()).thenReturn(fn);
            when(fn.getName()).thenReturn("CreateSchedule");
            when(fn.getArguments()).thenReturn("{}");

            String observation = r.invoke(42L, call, false);

            assertThat(observation).as("返回错误观察文本").contains("未授权");
            verify(writeTool, never()).execute(anyLong(), any(JsonNode.class));
        }

        @Test
        @DisplayName("A6 读工具回归：四种组合下读工具始终可用")
        void readToolsAlwaysAvailable() throws Exception {
            for (boolean server : new boolean[]{false, true}) {
                for (boolean req : new boolean[]{false, true}) {
                    ToolRegistry r = registry(server);
                    assertThat(r.definitions(req).stream().anyMatch(d -> d.getFunction().getName().equals("QuerySchedules")))
                            .as("server=%s req=%s 读工具均可用", server, req).isTrue();
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // ② 六态状态机（30 格 = 11 合法 + 19 非法）
    // ------------------------------------------------------------------
    @Nested
    @DisplayName("② 六态状态机：11 格合法跃迁 100% 通过，19 格非法跃迁 100% 拒绝，终态冻结")
    class StateMachine {

        @Test
        @DisplayName("B1 合法链路：PENDING→IN_PROGRESS→PAUSED→IN_PROGRESS→COMPLETED 每步都合法")
        void legalChain() throws Exception {
            assertThat(ScheduleStatus.startableFrom()).contains(ScheduleStatus.PENDING);
            assertThat(ScheduleStatus.pausableFrom()).contains(ScheduleStatus.IN_PROGRESS);
            assertThat(ScheduleStatus.startableFrom()).contains(ScheduleStatus.PAUSED); // 续梭
            assertThat(ScheduleStatus.endableFrom()).contains(ScheduleStatus.IN_PROGRESS); // 落梭
            assertThat(ScheduleStatus.COMPLETED.isFinal()).isTrue();
        }

        @Test
        @DisplayName("B2 全矩阵校验：依据 datasets/state-machine-matrix.json，30 格（11 合法 / 19 非法）逐格比对")
        void fullTransitionMatrix() throws Exception {
            JsonNode root = MAPPER.readTree(loadDataset("state-machine-matrix.json"));
            JsonNode matrix = root.get("transitionMatrix");
            int legal = 0, illegal = 0, rejected = 0, passed = 0;

            for (String state : List.of("PENDING", "IN_PROGRESS", "PAUSED", "COMPLETED", "CANCELLED", "TIMEOUT")) {
                ScheduleStatus s = ScheduleStatus.valueOf(state);
                for (String action : List.of("start", "pause", "resume", "end", "cancel")) {
                    boolean expectLegal = matrix.get(state).get(action).asBoolean();
                    boolean actual = isTransitionLegal(s, action);
                    if (expectLegal) { legal++; if (actual) passed++; }
                    else { illegal++; if (!actual) rejected++; }
                }
            }

            assertThat(legal + illegal).as("矩阵规模：6 态 × 5 动作 = 30 格").isEqualTo(30);
            assertThat(legal).as("合法格数应与数据集一致（11）").isEqualTo(11);
            assertThat(illegal).as("非法格数应与数据集一致（19）").isEqualTo(19);
            assertThat(passed).as("合法跃迁全部通过").isEqualTo(legal);
            assertThat(rejected).as("非法跃迁全部拒绝").isEqualTo(illegal);
            assertThat(illegal).isGreaterThan(0);
        }

        @Test
        @DisplayName("B3 终态冻结：COMPLETED/CANCELLED 上任意动作均被拒")
        void finalStatesFrozen() throws Exception {
            for (ScheduleStatus s : List.of(ScheduleStatus.COMPLETED, ScheduleStatus.CANCELLED)) {
                assertThat(s.isFinal()).as("%s 是终态", s).isTrue();
                for (String action : List.of("start", "pause", "resume", "end", "cancel")) {
                    assertThat(isTransitionLegal(s, action))
                            .as("%s 上执行 %s 必须被拒", s, action).isFalse();
                }
            }
        }

        @Test
        @DisplayName("B4 非终态可继续流转；TIMEOUT 是终态，仅保留取消入口")
        void nonFinalAndTimeoutRules() throws Exception {
            assertThat(ScheduleStatus.PENDING.isFinal()).isFalse();
            assertThat(ScheduleStatus.IN_PROGRESS.isFinal()).isFalse();
            assertThat(ScheduleStatus.PAUSED.isFinal()).isFalse();
            assertThat(ScheduleStatus.TIMEOUT.isFinal()).isTrue();
            assertThat(ScheduleStatus.cancellableFrom()).contains(ScheduleStatus.TIMEOUT); // 超时后仅可取消
            assertThat(ScheduleStatus.startableFrom()).doesNotContain(ScheduleStatus.TIMEOUT);
            assertThat(ScheduleStatus.endableFrom()).doesNotContain(ScheduleStatus.TIMEOUT);
        }

        private boolean isTransitionLegal(ScheduleStatus from, String action) {
            return switch (action) {
                case "start" -> ScheduleStatus.startableFrom().contains(from);
                case "pause" -> ScheduleStatus.pausableFrom().contains(from);
                case "resume" -> ScheduleStatus.startableFrom().contains(from)
                        && from == ScheduleStatus.PAUSED;
                case "end" -> ScheduleStatus.endableFrom().contains(from);
                case "cancel" -> ScheduleStatus.cancellableFrom().contains(from);
                default -> false;
            };
        }
    }

    // ------------------------------------------------------------------
    // ③ AI 多日规划：相对日期解析
    // ------------------------------------------------------------------
    @Nested
    @DisplayName("③ AI 多日规划：相对日期解析正确率 100%，未来一周连续覆盖")
    class PlanRange {

        @Test
        @DisplayName("C1 数据集全量校验：datasets/plan-range-dataset.json")
        void datasetCases() throws Exception {
            JsonNode root = MAPPER.readTree(loadDataset("plan-range-dataset.json"));
            int total = 0, passed = 0;
            for (JsonNode c : root.get("cases")) {
                total++;
                LocalDate today = LocalDate.parse(c.get("today").asText());
                PlanRangeDetector.Range r = PlanRangeDetector.parse(c.get("input").asText(), today);
                boolean matched = c.get("matched").asBoolean();
                boolean ok = r.matched() == matched;
                if (matched) {
                    ok = ok
                            && r.startDate().equals(LocalDate.parse(c.get("start").asText()))
                            && r.endDate().equals(LocalDate.parse(c.get("end").asText()));
                }
                if (ok) passed++;
            }
            assertThat(passed).as("相对日期解析 %d/%d 全部命中", passed, total).isEqualTo(total);
        }

        @Test
        @DisplayName("C2 未来一周：今天起连续 7 天，逐日 +1 无断档")
        void futureWeekContinuity() throws Exception {
            LocalDate today = LocalDate.of(2026, 9, 18);
            PlanRangeDetector.Range r = PlanRangeDetector.parse("帮我生成未来一周规划", today);
            assertThat(r.matched()).isTrue();
            assertThat(r.startDate()).isEqualTo(today);
            assertThat(r.endDate()).isEqualTo(today.plusDays(6));
            LocalDate cursor = r.startDate();
            int days = 0;
            while (!cursor.isAfter(r.endDate())) {
                days++;
                cursor = cursor.plusDays(1);
            }
            assertThat(days).as("覆盖 7 天且日期连续").isEqualTo(7);
        }

        @Test
        @DisplayName("C3 跨年解析：2026-12-28 说「下周」落在 2027-01-04~01-10")
        void nextWeekCrossYear() throws Exception {
            PlanRangeDetector.Range r = PlanRangeDetector.parse("下周", LocalDate.of(2026, 12, 28));
            assertThat(r.matched()).isTrue();
            assertThat(r.startDate()).isEqualTo(LocalDate.of(2027, 1, 4));
            assertThat(r.endDate()).isEqualTo(LocalDate.of(2027, 1, 10));
        }

        @Test
        @DisplayName("C4 本月：1 号至月末（含 30 天月份）")
        void currentMonth() throws Exception {
            PlanRangeDetector.Range r = PlanRangeDetector.parse("生成本月的计划", LocalDate.of(2026, 9, 18));
            assertThat(r.startDate()).isEqualTo(LocalDate.of(2026, 9, 1));
            assertThat(r.endDate()).isEqualTo(LocalDate.of(2026, 9, 30));
        }

        @Test
        @DisplayName("C5 未命中回退：matched=false，调用方回退默认单日")
        void unmatchedFallback() throws Exception {
            PlanRangeDetector.Range r = PlanRangeDetector.parse("帮我安排一下我的时间", LocalDate.of(2026, 9, 18));
            assertThat(r.matched()).isFalse();
            assertThat(r.startDate()).isNull();
        }
    }

    // ------------------------------------------------------------------
    // ④ 兑换并发扣减
    // ------------------------------------------------------------------
    @Nested
    @DisplayName("④ 兑换并发扣减：锁失效最坏场景下条件更新兜底，超卖 = 0")
    class ConcurrentPurchase {

        private static final Long USER_ID = 41L;
        private static final Long ITEM_ID = 1L;

        private MallItemMapper mallItemMapper;
        private WardrobeItemMapper wardrobeItemMapper;
        private PurchasePersister persister;
        private UserClient userClient;
        private RedissonClient redissonClient;
        private ConcurrentHashMap<Long, MallItem> items;
        private final Object stockMonitor = new Object();

        private PurchaseServiceImpl newService(int stock, boolean lockBusy) throws Exception {
            mallItemMapper = mock(MallItemMapper.class);
            wardrobeItemMapper = mock(WardrobeItemMapper.class);
            persister = mock(PurchasePersister.class);
            userClient = mock(UserClient.class);
            NotificationClient notificationClient = mock(NotificationClient.class);
            redissonClient = mock(RedissonClient.class);

            // 内存仿真库存表：条件更新语义与真实 SQL 一致（原子 stock > 0 判定）
            items = new ConcurrentHashMap<>();
            MallItem item = new MallItem();
            item.setId(ITEM_ID);
            item.setName("测试装扮");
            item.setType("theme");
            item.setPrice(200);
            item.setStatus("ON_SALE");
            item.setStock(stock);
            items.put(ITEM_ID, item);

            when(mallItemMapper.selectById(ITEM_ID)).thenAnswer(i -> items.get(ITEM_ID));
            when(mallItemMapper.deductStock(ITEM_ID)).thenAnswer(i -> {
                synchronized (stockMonitor) {
                    MallItem m = items.get(ITEM_ID);
                    if (m.getStock() != null && m.getStock() > 0) {
                        m.setStock(m.getStock() - 1);
                        return 1;
                    }
                    return 0;
                }
            });
            when(mallItemMapper.restoreStock(ITEM_ID)).thenAnswer(i -> {
                synchronized (stockMonitor) {
                    MallItem m = items.get(ITEM_ID);
                    if (m.getStock() != null && m.getStock() >= 0) {
                        m.setStock(m.getStock() + 1);
                        return 1;
                    }
                    return 0;
                }
            });

            when(wardrobeItemMapper.countOwned(anyLong(), anyLong())).thenReturn(0);
            when(userClient.changePoints(anyLong(), any()))
                    .thenReturn(Result.success(1000 - 200));

            // 仿真事务落库：内部执行与真实 PurchasePersister 一致的条件更新
            when(persister.persist(eq(USER_ID), any(MallItem.class), anyInt())).thenAnswer(inv -> {
                if (mallItemMapper.deductStock(ITEM_ID) == 0) {
                    throw new BizException(ErrorCode.CONFLICT, "商品已售罄");
                }
                MallItemVO vo = MallItemVO.from(items.get(ITEM_ID));
                return new PurchaseResultVO(1L, vo, inv.getArgument(2));
            });

            RLock lock = mock(RLock.class);
            when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class)))
                    .thenReturn(!lockBusy); // 模拟锁租期提前失效（恒可获取）或繁忙
            when(lock.isHeldByCurrentThread()).thenReturn(true);
            when(redissonClient.getLock(anyString())).thenReturn(lock);

            MallProperties props = new MallProperties();
            props.setPurchaseNotify(false); // 关闭旁路通知，聚焦交易一致性

            return new PurchaseServiceImpl(mallItemMapper, wardrobeItemMapper, persister,
                    userClient, notificationClient, redissonClient, props);
        }

        @Test
        @DisplayName("D1 50 并发抢 10 库存：成功 ≤10、库存 ≥0、超卖 = 0")
        void concurrentNeverOversells() throws Exception {
            PurchaseServiceImpl service = newService(10, false);
            int threads = 50;
            CountDownLatch ready = new CountDownLatch(threads);
            CountDownLatch go = new CountDownLatch(1);
            AtomicInteger success = new AtomicInteger();
            List<Throwable> unexpected = new ArrayList<>();
            ExecutorService pool = Executors.newFixedThreadPool(threads);

            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    ready.countDown();
                    try {
                        go.await();
                        service.purchase(USER_ID, ITEM_ID);
                        success.incrementAndGet();
                    } catch (BizException e) {
                        if (e.getCode() != ErrorCode.CONFLICT.getCode()) {
                            unexpected.add(e);
                        }
                    } catch (Exception e) {
                        unexpected.add(e);
                    }
                });
            }
            ready.await(10, TimeUnit.SECONDS);
            go.countDown();
            pool.shutdown();
            pool.awaitTermination(30, TimeUnit.SECONDS);

            int stockLeft = items.get(ITEM_ID).getStock();
            assertThat(unexpected).as("不允许出现非售罄异常").isEmpty();
            assertThat(success.get()).as("成功数 ≤ 库存 10").isLessThanOrEqualTo(10);
            assertThat(stockLeft).as("库存不为负").isGreaterThanOrEqualTo(0);
            assertThat(success.get() + stockLeft).as("成功数 + 剩余库存 = 初始 10（无超卖且无丢失）")
                    .isEqualTo(10);
        }

        @Test
        @DisplayName("D2 售罄：落库被拒，扣减的积分原路退回（扣减与退回成对，净变化 0）")
        void soldOutCompensated() throws Exception {
            PurchaseServiceImpl service = newService(0, false);
            try {
                service.purchase(USER_ID, ITEM_ID);
                org.junit.jupiter.api.Assertions.fail("应当抛出售罄异常");
            } catch (BizException e) {
                assertThat(e.getCode()).isEqualTo(ErrorCode.CONFLICT.getCode());
            }
            // 真实链路：扣积分(-200) → 事务落库发现售罄 → 退积分(+200)，补偿成对
            verify(userClient, org.mockito.Mockito.times(2)).changePoints(anyLong(), any());
            assertThat(items.get(ITEM_ID).getStock()).isZero();
        }

        @Test
        @DisplayName("D3 锁繁忙：tryLock=false → RATE_LIMITED(429)")
        void lockBusyRejected() throws Exception {
            PurchaseServiceImpl service = newService(10, true);
            try {
                service.purchase(USER_ID, ITEM_ID);
                org.junit.jupiter.api.Assertions.fail("应当抛出限流异常");
            } catch (BizException e) {
                assertThat(e.getCode()).isEqualTo(ErrorCode.RATE_LIMITED.getCode());
            }
            assertThat(items.get(ITEM_ID).getStock()).isEqualTo(10);
        }

        @Test
        @DisplayName("D4 落库失败：原路退积分，补偿一致")
        void persistFailureRefunds() throws Exception {
            PurchaseServiceImpl service = newService(10, false);
            when(persister.persist(eq(USER_ID), any(MallItem.class), anyInt()))
                    .thenThrow(new RuntimeException("db down"));

            try {
                service.purchase(USER_ID, ITEM_ID);
                org.junit.jupiter.api.Assertions.fail("应当抛出异常");
            } catch (RuntimeException ignored) {
                // 预期
            }
            // 第一次为扣减（-200），第二次为退款（+200）→ 正负成对
            verify(userClient, org.mockito.Mockito.times(2)).changePoints(anyLong(), any());
            assertThat(items.get(ITEM_ID).getStock()).isEqualTo(10);
        }
    }

    // ------------------------------------------------------------------
    // ⑤ 网关统一收口
    // ------------------------------------------------------------------
    @Nested
    @DisplayName("⑤ 网关统一收口：鉴权 401 / 伪造凭证剥离 / 限流 key 策略")
    class GatewayGuard {

        private JwtUtilMock jwtUtil;
        private SortsGatewayProperties gatewayProperties;
        private AuthGlobalFilter filter;

        private void newFilter() throws Exception {
            jwtUtil = new JwtUtilMock();
            gatewayProperties = mock(SortsGatewayProperties.class);
            when(gatewayProperties.getWhitelist())
                    .thenReturn(List.of("/api/v1/auth/**"));
            filter = new AuthGlobalFilter(jwtUtil, gatewayProperties);
        }

        @Test
        @DisplayName("E1 无 token → 401")
        void noTokenUnauthorized() throws Exception {
            newFilter();
            MockServerWebExchange ex = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/v1/plans").build());
            filter.filter(ex, mock(GatewayFilterChain.class)).block();
            assertThat(ex.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("E2 过期 token → 401（TOKEN_EXPIRED）")
        void expiredTokenUnauthorized() throws Exception {
            newFilter();
            jwtUtil.throwOnParse = new BizException(ErrorCode.TOKEN_EXPIRED, "访问令牌已过期");
            MockServerWebExchange ex = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/v1/plans")
                            .header("Authorization", "Bearer expired.token.here").build());
            filter.filter(ex, mock(GatewayFilterChain.class)).block();
            assertThat(ex.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("E3 有效 token → 放行，注入 X-User-Id / X-Username")
        void validTokenInjectsIdentity() throws Exception {
            newFilter();
            jwtUtil.subject = "42";
            jwtUtil.username = "user";
            GatewayFilterChain chain = mock(GatewayFilterChain.class);
            when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

            MockServerWebExchange ex = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/v1/plans")
                            .header("Authorization", "Bearer valid.token").build());
            filter.filter(ex, chain).block();

            ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
            verify(chain).filter(captor.capture());
            var downstream = captor.getValue().getRequest().getHeaders();
            assertThat(downstream.getFirst("X-User-Id")).isEqualTo("42");
            assertThat(downstream.getFirst("X-Username")).isEqualTo("user");
        }

        @Test
        @DisplayName("E4 伪造内部凭证剥离：白名单放行时 X-Internal-Token 必被移除")
        void stripForgedInternalTokenOnWhitelist() throws Exception {
            newFilter();
            GatewayFilterChain chain = mock(GatewayFilterChain.class);
            when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

            MockServerWebExchange ex = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/v1/auth/login")
                            .header("X-Internal-Token", "forged").build());
            filter.filter(ex, chain).block();

            ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
            verify(chain).filter(captor.capture());
            assertThat(captor.getValue().getRequest().getHeaders().containsKey("X-Internal-Token"))
                    .as("伪造的内部凭证不得透传下游").isFalse();
        }

        @Test
        @DisplayName("E5 鉴权路径同样剥离：登录态请求携带伪造 X-Internal-Token 被移除")
        void stripForgedInternalTokenOnAuthPath() throws Exception {
            newFilter();
            jwtUtil.subject = "42";
            jwtUtil.username = "user";
            GatewayFilterChain chain = mock(GatewayFilterChain.class);
            when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

            MockServerWebExchange ex = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/v1/plans")
                            .header("Authorization", "Bearer valid.token")
                            .header("X-Internal-Token", "forged").build());
            filter.filter(ex, chain).block();

            ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
            verify(chain).filter(captor.capture());
            assertThat(captor.getValue().getRequest().getHeaders().containsKey("X-Internal-Token"))
                    .as("鉴权路径下伪造凭证同样被剥离").isFalse();
        }

        @Test
        @DisplayName("E6 限流 key 策略：登录按用户 rate:user:{id}，未登录按 IP rate:ip:{ip}")
        void rateLimitKeyStrategy() throws Exception {
            RateLimitConfig config = new RateLimitConfig();
            KeyResolver resolver = config.userKeyResolver();

            MockServerWebExchange logged = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/x").header("X-User-Id", "42").build());
            assertThat(resolver.resolve(logged).block()).isEqualTo("rate:user:42");

            MockServerWebExchange anon = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/x")
                            .remoteAddress(new InetSocketAddress("203.0.113.7", 1234)).build());
            String ipKey = resolver.resolve(anon).block();
            assertThat(ipKey).startsWith("rate:ip:");
        }

        /** 轻量 JwtUtil 替身：避免真实 JWT 密钥与签名依赖 */
        static class JwtUtilMock extends com.sorts.common.jwt.JwtUtil {
            String subject;
            String username;
            RuntimeException throwOnParse;

            JwtUtilMock() {
                super(new com.sorts.common.jwt.JwtProperties());
            }

            @Override
            public Claims parse(String token, String expectedType) {
                if (throwOnParse != null) {
                    throw throwOnParse;
                }
                Claims claims = mock(Claims.class);
                when(claims.getSubject()).thenReturn(subject);
                when(claims.get(AuthConstants.CLAIM_USERNAME, String.class)).thenReturn(username);
                return claims;
            }
        }
    }

    // ------------------------------------------------------------------
    // 工具方法
    // ------------------------------------------------------------------
    private static java.io.File loadDataset(String name) {
        return new java.io.File("datasets", name);
    }
}
