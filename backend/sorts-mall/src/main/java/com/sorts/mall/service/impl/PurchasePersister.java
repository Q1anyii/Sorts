package com.sorts.mall.service.impl;

import com.sorts.common.exception.BizException;
import com.sorts.common.result.ErrorCode;
import com.sorts.mall.dto.MallItemVO;
import com.sorts.mall.dto.PurchaseResultVO;
import com.sorts.mall.entity.MallItem;
import com.sorts.mall.entity.PurchaseRecord;
import com.sorts.mall.entity.WardrobeItem;
import com.sorts.mall.mapper.MallItemMapper;
import com.sorts.mall.mapper.PurchaseRecordMapper;
import com.sorts.mall.mapper.WardrobeItemMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 购买的本地落库执行器（独立 Bean，事务在此开启）。
 *
 * <p>为什么必须独立成 Bean：{@code @Transactional} 依赖 Spring 代理，
 * 同类内自调用会退化成一个普通方法调用——事务根本不会开启，而代码看起来「明明加了注解」。
 * 把它拆出来，从结构上就不给它退化的机会。</p>
 *
 * <p>事务内三件事必须同生共死：扣库存、写购买记录、发装扮。
 * 任何一步失败都会整体回滚，不会出现「扣了库存没发装扮」的中间态。</p>
 *
 * @author sorts
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PurchasePersister {

    private final MallItemMapper mallItemMapper;

    private final PurchaseRecordMapper purchaseRecordMapper;

    private final WardrobeItemMapper wardrobeItemMapper;

    /**
     * 落库：扣库存 → 写购买记录 → 发装扮。
     *
     * @param userId  买家
     * @param item    商品（调用方已在锁内重新加载，避免用到过期库存）
     * @param balance 扣减后的光阴砂余额（来自用户服务，作为订单快照）
     */
    @Transactional(rollbackFor = Exception.class)
    public PurchaseResultVO persist(Long userId, MallItem item, Integer balance) {
        // 条件更新即数据库层的最后防线：即便分布式锁意外失效，
        // UPDATE ... WHERE stock > 0 也保证不会把库存扣成负数
        if (!item.unlimited() && mallItemMapper.deductStock(item.getId()) == 0) {
            throw new BizException(ErrorCode.CONFLICT, "商品已售罄");
        }

        LocalDateTime now = LocalDateTime.now();

        PurchaseRecord record = new PurchaseRecord();
        record.setUserId(userId);
        record.setItemId(item.getId());
        // 商品名与成交价落快照：商品后续改名调价不该改写历史账目
        record.setItemName(item.getName());
        record.setPrice(item.getPrice());
        record.setPointsAfter(balance);
        record.setCreatedAt(now);
        purchaseRecordMapper.insert(record);

        WardrobeItem wardrobe = new WardrobeItem();
        wardrobe.setUserId(userId);
        wardrobe.setItemId(item.getId());
        wardrobe.setItemType(item.getType());
        // 新购装扮不自动上身：让用户在装扮页显式选择，避免「买完界面突然变了」
        wardrobe.setIsActive(0);
        wardrobe.setPurchaseId(record.getId());
        wardrobe.setDeleted(0);
        wardrobe.setPurchasedAt(now);
        wardrobeItemMapper.insert(wardrobe);

        log.info("购买落库成功：userId={}, itemId={}, itemName={}, price={}, purchaseId={}",
                userId, item.getId(), item.getName(), item.getPrice(), record.getId());

        return new PurchaseResultVO(record.getId(), MallItemVO.from(item), balance);
    }
}
