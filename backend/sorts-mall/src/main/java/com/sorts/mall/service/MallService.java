package com.sorts.mall.service;

import com.sorts.mall.dto.MallItemDetailVO;
import com.sorts.mall.dto.MallItemPageVO;

/**
 * 商城服务：商品浏览与购买。
 *
 * @author sorts
 */
public interface MallService {

    /**
     * 商品列表（仅上架商品）。
     *
     * @param type 商品类型筛选，空表示全部
     */
    MallItemPageVO list(Long userId, String type, int page, int pageSize);

    /** 商品详情（附「是否已拥有」与购买人次） */
    MallItemDetailVO detail(Long userId, Long itemId);
}
