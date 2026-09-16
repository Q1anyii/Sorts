package com.sorts.mall.service;

import com.sorts.mall.dto.PurchaseResultVO;

/**
 * 购买服务。
 *
 * <p>与 {@link MallService}（浏览）分开：购买是「加锁 + 扣积分 + 补偿」的重流程，
 * 塞在浏览服务里会让它同时承担两类完全不同的失败语义。</p>
 *
 * @author sorts
 */
public interface PurchaseService {

    /**
     * 购买商品：扣减光阴砂并把装扮放入仓库。
     *
     * @throws com.sorts.common.exception.BizException 商品不存在/已下架/已拥有/已售罄/积分不足/并发冲突
     */
    PurchaseResultVO purchase(Long userId, Long itemId);
}
