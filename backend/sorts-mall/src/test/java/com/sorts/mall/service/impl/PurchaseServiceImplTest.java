package com.sorts.mall.service.impl;

import com.sorts.common.exception.BizException;
import com.sorts.common.result.Result;
import com.sorts.mall.client.NotificationClient;
import com.sorts.mall.client.UserClient;
import com.sorts.mall.client.dto.CreateNotificationCommand;
import com.sorts.mall.client.dto.PointsChangeCommand;
import com.sorts.mall.config.MallProperties;
import com.sorts.mall.dto.MallItemVO;
import com.sorts.mall.dto.PurchaseResultVO;
import com.sorts.mall.entity.MallItem;
import com.sorts.mall.mapper.MallItemMapper;
import com.sorts.mall.mapper.WardrobeItemMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 购买服务单元测试。
 *
 * <p>覆盖四类关键路径：前置校验（不存在/下架/已拥有）、并发闸门（抢锁失败）、
 * 正常成交、以及各种失败下的补偿与异常语义。</p>
 *
 * @author sorts
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PurchaseServiceImplTest {

    private static final Long USER_ID = 41L;

    private static final Long ITEM_ID = 1L;

    private static final int PRICE = 200;

    @Mock
    private MallItemMapper mallItemMapper;

    @Mock
    private WardrobeItemMapper wardrobeItemMapper;

    @Mock
    private PurchasePersister purchasePersister;

    @Mock
    private UserClient userClient;

    @Mock
    private NotificationClient notificationClient;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RLock lock;

    private MallProperties properties;

    private PurchaseServiceImpl purchaseService;

    private MallItem item;

    private PurchaseResultVO persisted;

    @BeforeEach
    void setUp() throws InterruptedException {
        properties = new MallProperties();
        purchaseService = new PurchaseServiceImpl(mallItemMapper, wardrobeItemMapper, purchasePersister,
                userClient, notificationClient, redissonClient, properties);

        item = new MallItem();
        item.setId(ITEM_ID);
        item.setName("织锦流光·皮肤");
        item.setType("SKIN");
        item.setPrice(PRICE);
        item.setStock(10);
        item.setStatus("ON_SALE");

        persisted = new PurchaseResultVO(9001L, MallItemVO.from(item), 799);

        when(mallItemMapper.selectById(ITEM_ID)).thenReturn(item);
        when(redissonClient.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(wardrobeItemMapper.countOwned(USER_ID, ITEM_ID)).thenReturn(0);
        when(userClient.changePoints(eq(USER_ID), any(PointsChangeCommand.class))).thenReturn(Result.success(799));
        when(purchasePersister.persist(eq(USER_ID), any(MallItem.class), any())).thenReturn(persisted);
        when(notificationClient.create(any(CreateNotificationCommand.class))).thenReturn(Result.success(1L));
    }

    @Test
    @DisplayName("购买成功：按商品价扣积分、落库、释放锁、投递通知")
    void purchaseHappyPath() {
        PurchaseResultVO result = purchaseService.purchase(USER_ID, ITEM_ID);

        assertEquals(9001L, result.getPurchaseId());
        assertEquals(799, result.getRemainingPoints());

        ArgumentCaptor<PointsChangeCommand> pointsCaptor = ArgumentCaptor.forClass(PointsChangeCommand.class);
        verify(userClient).changePoints(eq(USER_ID), pointsCaptor.capture());
        // 必须扣成负数，否则会变成「买东西送积分」
        assertEquals(-PRICE, pointsCaptor.getValue().getDelta());

        verify(purchasePersister).persist(eq(USER_ID), any(MallItem.class), eq(799));
        verify(lock).unlock();
        verify(notificationClient).create(any(CreateNotificationCommand.class));
    }

    @Test
    @DisplayName("顺序保证：先锁 → 再扣积分 → 再落库")
    void purchaseOrderIsLockPointsPersist() {
        purchaseService.purchase(USER_ID, ITEM_ID);

        InOrder inOrder = Mockito.inOrder(redissonClient, userClient, purchasePersister);
        inOrder.verify(redissonClient).getLock(anyString());
        inOrder.verify(userClient).changePoints(eq(USER_ID), any(PointsChangeCommand.class));
        inOrder.verify(purchasePersister).persist(eq(USER_ID), any(MallItem.class), any());
    }

    @Test
    @DisplayName("商品不存在时报 404，且不加锁")
    void missingItem() {
        when(mallItemMapper.selectById(ITEM_ID)).thenReturn(null);
        assertThrows(BizException.class, () -> purchaseService.purchase(USER_ID, ITEM_ID));
        verify(redissonClient, never()).getLock(anyString());
    }

    @Test
    @DisplayName("缺少商品 ID 时报参数错误")
    void nullItemId() {
        assertThrows(BizException.class, () -> purchaseService.purchase(USER_ID, null));
    }

    @Test
    @DisplayName("商品已下架时拒绝，不进入加锁与扣积分环节")
    void offShelfRejected() {
        item.setStatus("OFF_SHELF");
        assertThrows(BizException.class, () -> purchaseService.purchase(USER_ID, ITEM_ID));
        verify(userClient, never()).changePoints(anyLong(), any());
    }

    @Test
    @DisplayName("锁外预检已拥有时直接拒绝，省掉抢锁与扣积分")
    void alreadyOwnedShortCircuits() {
        when(wardrobeItemMapper.countOwned(USER_ID, ITEM_ID)).thenReturn(1);

        BizException e = assertThrows(BizException.class, () -> purchaseService.purchase(USER_ID, ITEM_ID));
        assertTrue(e.getMessage().contains("已拥有"));
        verify(redissonClient, never()).getLock(anyString());
    }

    @Test
    @DisplayName("锁内复核发现已拥有时同样拒绝（挡住并发双击）")
    void alreadyOwnedInsideLockRejected() {
        // 锁外为 0，锁内变为 1
        when(wardrobeItemMapper.countOwned(USER_ID, ITEM_ID)).thenReturn(0, 1);

        assertThrows(BizException.class, () -> purchaseService.purchase(USER_ID, ITEM_ID));
        verify(userClient, never()).changePoints(anyLong(), any());
        verify(lock).unlock();
    }

    @Test
    @DisplayName("锁内复核发现已下架时拒绝（排队等待期间商品被下架）")
    void offShelfInsideLockRejected() {
        MallItem offShelf = new MallItem();
        offShelf.setId(ITEM_ID);
        offShelf.setName("织锦流光·皮肤");
        offShelf.setPrice(PRICE);
        offShelf.setStock(10);
        offShelf.setStatus("OFF_SHELF");
        when(mallItemMapper.selectById(ITEM_ID)).thenReturn(item, offShelf);

        assertThrows(BizException.class, () -> purchaseService.purchase(USER_ID, ITEM_ID));
        verify(userClient, never()).changePoints(anyLong(), any());
    }

    @Test
    @DisplayName("抢锁失败返回「稍后再试」，不扣积分也不落库")
    void lockContentionRejected() throws InterruptedException {
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(false);

        BizException e = assertThrows(BizException.class, () -> purchaseService.purchase(USER_ID, ITEM_ID));
        assertEquals(429, e.getCode());
        verify(userClient, never()).changePoints(anyLong(), any());
        verify(purchasePersister, never()).persist(anyLong(), any(), any());
    }

    @Test
    @DisplayName("Redis 不可用时 fail-closed：宁可拒绝购买，也不让库存与积分失去互斥保护")
    void redisFailureRejectsPurchase() throws InterruptedException {
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class)))
                .thenThrow(new RuntimeException("Redis connection refused"));

        BizException e = assertThrows(BizException.class, () -> purchaseService.purchase(USER_ID, ITEM_ID));
        assertEquals(503, e.getCode());
        verify(userClient, never()).changePoints(anyLong(), any());
    }

    @Test
    @DisplayName("获取锁被中断时恢复中断标记并报错")
    void lockInterrupted() throws InterruptedException {
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenThrow(new InterruptedException());

        assertThrows(BizException.class, () -> purchaseService.purchase(USER_ID, ITEM_ID));
        assertTrue(Thread.currentThread().isInterrupted());
        // 清理中断标记，避免影响同一线程上的后续测试
        Thread.interrupted();
    }

    @Test
    @DisplayName("光阴砂不足：透传下游业务码与提示，不落库、不退款")
    void insufficientPointsPropagated() {
        when(userClient.changePoints(eq(USER_ID), any(PointsChangeCommand.class)))
                .thenReturn(Result.fail(409, "光阴砂不足"));

        BizException e = assertThrows(BizException.class, () -> purchaseService.purchase(USER_ID, ITEM_ID));
        assertEquals(409, e.getCode());
        assertEquals("光阴砂不足", e.getMessage());
        verify(purchasePersister, never()).persist(anyLong(), any(), any());
        // 积分本就没扣成功，绝不能再退一次
        verify(userClient, times(1)).changePoints(eq(USER_ID), any(PointsChangeCommand.class));
        verify(lock).unlock();
    }

    @Test
    @DisplayName("积分服务异常：转为依赖不可用，且不调用退款")
    void pointsServiceFailure() {
        when(userClient.changePoints(eq(USER_ID), any(PointsChangeCommand.class)))
                .thenThrow(new RuntimeException("connect timed out"));

        BizException e = assertThrows(BizException.class, () -> purchaseService.purchase(USER_ID, ITEM_ID));
        assertEquals(503, e.getCode());
        verify(purchasePersister, never()).persist(anyLong(), any(), any());
    }

    @Test
    @DisplayName("本地落库失败：补偿退款后再抛出原异常，锁照常释放")
    void localFailureRefundsPoints() {
        when(purchasePersister.persist(eq(USER_ID), any(MallItem.class), any()))
                .thenThrow(new BizException(409, "商品已售罄"));

        BizException e = assertThrows(BizException.class, () -> purchaseService.purchase(USER_ID, ITEM_ID));
        assertEquals("商品已售罄", e.getMessage());

        // 第一次扣款、第二次退款
        ArgumentCaptor<PointsChangeCommand> captor = ArgumentCaptor.forClass(PointsChangeCommand.class);
        verify(userClient, times(2)).changePoints(eq(USER_ID), captor.capture());
        assertEquals(-PRICE, captor.getAllValues().get(0).getDelta());
        assertEquals(PRICE, captor.getAllValues().get(1).getDelta());
        verify(lock).unlock();
    }

    @Test
    @DisplayName("补偿退款本身失败时不掩盖原始异常（只记 ERROR 日志）")
    void refundFailureDoesNotMaskOriginalError() {
        when(purchasePersister.persist(eq(USER_ID), any(MallItem.class), any()))
                .thenThrow(new BizException(409, "商品已售罄"));
        when(userClient.changePoints(eq(USER_ID), any(PointsChangeCommand.class)))
                .thenReturn(Result.success(799))                 // 扣款成功
                .thenThrow(new RuntimeException("退款网络异常")); // 退款失败

        BizException e = assertThrows(BizException.class, () -> purchaseService.purchase(USER_ID, ITEM_ID));
        assertEquals("商品已售罄", e.getMessage());
    }

    @Test
    @DisplayName("通知投递失败不影响购买结果")
    void notificationFailureIgnored() {
        when(notificationClient.create(any(CreateNotificationCommand.class)))
                .thenThrow(new RuntimeException("notification down"));

        PurchaseResultVO result = purchaseService.purchase(USER_ID, ITEM_ID);
        assertEquals(9001L, result.getPurchaseId());
    }

    @Test
    @DisplayName("关闭购买通知时不投递")
    void notifyDisabled() {
        properties.setPurchaseNotify(false);

        purchaseService.purchase(USER_ID, ITEM_ID);
        verify(notificationClient, never()).create(any());
    }

    @Test
    @DisplayName("通知内容包含商品名与剩余光阴砂")
    void notificationContent() {
        purchaseService.purchase(USER_ID, ITEM_ID);

        ArgumentCaptor<CreateNotificationCommand> captor =
                ArgumentCaptor.forClass(CreateNotificationCommand.class);
        verify(notificationClient).create(captor.capture());
        CreateNotificationCommand command = captor.getValue();
        assertEquals(USER_ID, command.getUserId());
        assertEquals("SYSTEM", command.getType());
        assertTrue(command.getTitle().contains("织锦流光·皮肤"));
        assertTrue(command.getContent().contains("200"));
        assertTrue(command.getContent().contains("799"));
        assertEquals(ITEM_ID, command.getRelatedId());
    }

    @Test
    @DisplayName("锁未持有（租期已过）时不强行解锁，避免 IllegalMonitorStateException")
    void unlockSkippedWhenNotHeld() {
        when(lock.isHeldByCurrentThread()).thenReturn(false);

        purchaseService.purchase(USER_ID, ITEM_ID);
        verify(lock, never()).unlock();
    }
}
