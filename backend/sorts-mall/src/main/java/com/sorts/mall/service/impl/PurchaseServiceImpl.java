package com.sorts.mall.service.impl;

import com.sorts.common.exception.BizException;
import com.sorts.common.result.ErrorCode;
import com.sorts.common.result.Result;
import com.sorts.mall.client.NotificationClient;
import com.sorts.mall.client.UserClient;
import com.sorts.mall.client.dto.CreateNotificationCommand;
import com.sorts.mall.client.dto.PointsChangeCommand;
import com.sorts.mall.config.MallProperties;
import com.sorts.mall.dto.PurchaseResultVO;
import com.sorts.mall.entity.MallItem;
import com.sorts.mall.enums.ItemStatus;
import com.sorts.mall.mapper.MallItemMapper;
import com.sorts.mall.mapper.WardrobeItemMapper;
import com.sorts.mall.service.PurchaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.concurrent.TimeUnit;

/**
 * 购买服务实现。
 *
 * <p><b>购买顺序与失败语义（这是本类最重要的决策）</b>：</p>
 * <ol>
 *   <li><b>先扣积分，再落本地事务</b>。积分不足是购买最常见的失败原因，
 *       先扣可以让绝大多数失败在「尚未占用库存」时就返回；
 *       反过来先落库再扣积分，失败时就要删除已经写好的订单（账目不该被删）。</li>
 *   <li><b>本地事务失败则补偿退款</b>。补偿失败会以 ERROR 级别打出
 *       userId/itemId/金额，供人工或后续对账任务收敛——绝不能静默吞掉。</li>
 *   <li><b>锁内只有「读商品 + 查重 + 扣积分 + 落库」</b>。通知投递放在锁外，
 *       否则一次下游抖动就会把锁的租期耗光，反而放大并发问题。</li>
 *   <li><b>Redis 不可用时 fail-closed（拒绝购买）</b>。库存与积分不允许在没有互斥保护的情况下裸奔。</li>
 * </ol>
 *
 * <p>已知限制：扣积分成功与本地事务提交之间存在极小的崩溃窗口（进程被杀），
 * 补偿代码不会执行，表现为「扣了积分没拿到装扮」。彻底消除需要事务性消息
 * （M7 的 RabbitMQ outbox + 对账任务），此处以明确的 ERROR 日志兜底。</p>
 *
 * @author sorts
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PurchaseServiceImpl implements PurchaseService {

    private static final String LOCK_KEY_PREFIX = "sorts:mall:lock:item:";

    /** 通知标题前缀，配合商品名（≤64）不会超过通知表标题列宽 */
    private static final String NOTIFY_TITLE_PREFIX = "购买成功：";

    private final MallItemMapper mallItemMapper;

    private final WardrobeItemMapper wardrobeItemMapper;

    private final PurchasePersister purchasePersister;

    private final UserClient userClient;

    private final NotificationClient notificationClient;

    private final RedissonClient redissonClient;

    private final MallProperties mallProperties;

    @Override
    public PurchaseResultVO purchase(Long userId, Long itemId) {
        MallItem item = requireItem(itemId);
        assertOnSale(item);
        // 锁外预检：已拥有就不必再抢锁、更不必扣积分，能挡掉绝大多数重复点击
        assertNotOwned(userId, itemId);

        RLock lock = lockFor(itemId);
        PurchaseResultVO result;
        if (!tryLock(lock)) {
            throw new BizException(ErrorCode.RATE_LIMITED, "同时购买的人有点多，请稍后再试");
        }
        try {
            // 锁内重新加载：锁外的商品状态可能已经在排队等待期间发生变化
            MallItem fresh = requireItem(itemId);
            assertOnSale(fresh);
            assertNotOwned(userId, itemId);

            Integer balance = deductPoints(userId, fresh);
            try {
                result = purchasePersister.persist(userId, fresh, balance);
            } catch (RuntimeException e) {
                refundPoints(userId, fresh);
                throw e;
            }
        } finally {
            unlock(lock);
        }

        notifyPurchase(userId, result);
        return result;
    }

    private MallItem requireItem(Long itemId) {
        if (itemId == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "商品 ID 不能为空");
        }
        MallItem item = mallItemMapper.selectById(itemId);
        if (item == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "商品不存在");
        }
        return item;
    }

    private void assertOnSale(MallItem item) {
        if (!ItemStatus.ON_SALE.name().equals(item.getStatus())) {
            throw new BizException(ErrorCode.CONFLICT, "商品已下架");
        }
    }

    private void assertNotOwned(Long userId, Long itemId) {
        if (wardrobeItemMapper.countOwned(userId, itemId) > 0) {
            throw new BizException(ErrorCode.CONFLICT, "已拥有该装扮");
        }
    }

    /** 按商品维度加锁：不同商品之间互不影响，同商品严格串行 */
    private RLock lockFor(Long itemId) {
        return redissonClient.getLock(LOCK_KEY_PREFIX + itemId);
    }

    private boolean tryLock(RLock lock) {
        try {
            return lock.tryLock(mallProperties.getLockWaitSeconds(),
                    mallProperties.getLockLeaseSeconds(), TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException(ErrorCode.SYSTEM_ERROR, "购买请求被中断，请重试");
        } catch (Exception e) {
            // Redis 挂了也不能让库存与积分失去互斥保护
            log.error("获取商品锁失败，拒绝本次购买", e);
            throw new BizException(ErrorCode.SERVICE_UNAVAILABLE, "商城繁忙，请稍后再试");
        }
    }

    private void unlock(RLock lock) {
        try {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        } catch (Exception e) {
            // 没释放成功不是灾难：租期到了 Redis 会自动释放
            log.warn("释放商品锁失败（租期到期后将自动释放）：{}", e.getMessage());
        }
    }

    /**
     * 扣减光阴砂。
     *
     * <p>下游的「光阴砂不足」等业务码原样透出，前端才能给出准确提示；
     * 网络异常则统一转成「依赖服务不可用」，避免把栈信息当业务提示抛给用户。</p>
     */
    private Integer deductPoints(Long userId, MallItem item) {
        Result<Integer> result;
        try {
            result = userClient.changePoints(userId, new PointsChangeCommand(
                    -Math.abs(item.getPrice()), "锦市消费：" + item.getName(), item.getId()));
        } catch (Exception e) {
            log.warn("调用积分服务异常：userId={}, itemId={}, error={}", userId, item.getId(), e.getMessage());
            throw new BizException(ErrorCode.SERVICE_UNAVAILABLE, "积分服务暂时不可用，请稍后再试");
        }
        if (result == null) {
            throw new BizException(ErrorCode.SERVICE_UNAVAILABLE, "积分服务暂时不可用，请稍后再试");
        }
        if (result.getCode() != ErrorCode.SUCCESS.getCode()) {
            throw new BizException(result.getCode(),
                    StringUtils.hasText(result.getMessage()) ? result.getMessage() : "光阴砂扣减失败");
        }
        return result.getData();
    }

    /** 补偿退款：只记日志不抛异常，否则会掩盖真正的失败原因 */
    private void refundPoints(Long userId, MallItem item) {
        try {
            Result<Integer> refund = userClient.changePoints(userId, new PointsChangeCommand(
                    Math.abs(item.getPrice()), "购买失败退款：" + item.getName(), item.getId()));
            if (refund == null || refund.getCode() != ErrorCode.SUCCESS.getCode()) {
                log.error("购买失败退款未成功，需人工对账：userId={}, itemId={}, amount={}, message={}",
                        userId, item.getId(), item.getPrice(),
                        refund == null ? "无响应" : refund.getMessage());
            }
        } catch (Exception e) {
            log.error("购买失败退款异常，需人工对账：userId={}, itemId={}, amount={}",
                    userId, item.getId(), item.getPrice(), e);
        }
    }

    /** 购买成功通知是旁路能力：投递失败绝不能影响购买结果 */
    private void notifyPurchase(Long userId, PurchaseResultVO result) {
        if (!mallProperties.isPurchaseNotify() || result == null || result.getItem() == null) {
            return;
        }
        try {
            CreateNotificationCommand command = new CreateNotificationCommand();
            command.setUserId(userId);
            command.setType("SYSTEM");
            command.setTitle(NOTIFY_TITLE_PREFIX + result.getItem().getName());
            command.setContent("已消耗 " + result.getItem().getPrice() + " 光阴砂，剩余 "
                    + result.getRemainingPoints() + "。装扮已放入你的装扮仓库，可前往装扮页启用。");
            command.setRelatedId(result.getItem().getId());
            Result<Long> response = notificationClient.create(command);
            if (response == null || response.getCode() != ErrorCode.SUCCESS.getCode()) {
                log.warn("购买成功通知投递返回异常：{}", response == null ? "无响应" : response.getMessage());
            }
        } catch (Exception e) {
            log.warn("购买成功通知投递失败（不影响购买结果）：{}", e.getMessage());
        }
    }
}
