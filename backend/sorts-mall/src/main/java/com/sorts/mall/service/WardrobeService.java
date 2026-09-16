package com.sorts.mall.service;

import com.sorts.mall.dto.WardrobeItemVO;

import java.util.List;

/**
 * 装扮仓库服务。
 *
 * @author sorts
 */
public interface WardrobeService {

    /** 获取用户已拥有的全部装扮（使用中的排在最前） */
    List<WardrobeItemVO> list(Long userId);

    /**
     * 切换当前使用的装扮。
     *
     * @param itemId 装扮商品 ID
     * @param type   前端声明的类型，仅用于一致性校验；判定依据始终是仓库记录
     */
    void activate(Long userId, Long itemId, String type);
}
