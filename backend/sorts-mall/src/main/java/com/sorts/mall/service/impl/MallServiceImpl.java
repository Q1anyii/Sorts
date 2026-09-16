package com.sorts.mall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sorts.common.exception.BizException;
import com.sorts.common.result.ErrorCode;
import com.sorts.common.result.Result;
import com.sorts.mall.client.UserClient;
import com.sorts.mall.client.dto.UserPointsDto;
import com.sorts.mall.dto.MallItemDetailVO;
import com.sorts.mall.dto.MallItemPageVO;
import com.sorts.mall.dto.MallItemVO;
import com.sorts.mall.entity.MallItem;
import com.sorts.mall.enums.ItemStatus;
import com.sorts.mall.enums.ItemType;
import com.sorts.mall.mapper.MallItemMapper;
import com.sorts.mall.mapper.PurchaseRecordMapper;
import com.sorts.mall.mapper.WardrobeItemMapper;
import com.sorts.mall.service.MallService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 商城服务实现：商品浏览。
 *
 * @author sorts
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MallServiceImpl implements MallService {

    /** 单页上限，与 mybatis-plus maxLimit 双保险 */
    private static final int MAX_PAGE_SIZE = 200;

    private static final int DEFAULT_PAGE_SIZE = 20;

    private final MallItemMapper mallItemMapper;

    private final PurchaseRecordMapper purchaseRecordMapper;

    private final WardrobeItemMapper wardrobeItemMapper;

    private final UserClient userClient;

    @Override
    public MallItemPageVO list(Long userId, String type, int page, int pageSize) {
        long pageNo = Math.max(1, page);
        long size = pageSize <= 0 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);

        LambdaQueryWrapper<MallItem> wrapper = new LambdaQueryWrapper<MallItem>()
                .eq(MallItem::getStatus, ItemStatus.ON_SALE.name())
                // 排序权重相同是常态（都填 0 时），需要 id 兜底保证翻页稳定
                .orderByDesc(MallItem::getSortOrder)
                .orderByDesc(MallItem::getId);

        if (StringUtils.hasText(type)) {
            ItemType itemType = ItemType.from(type);
            if (itemType == null) {
                throw new BizException(ErrorCode.PARAM_ERROR, "不支持的商品类型：" + type);
            }
            wrapper.eq(MallItem::getType, itemType.name());
        }

        Page<MallItem> result = mallItemMapper.selectPage(new Page<>(pageNo, size), wrapper);
        List<MallItemVO> records = result.getRecords().stream().map(MallItemVO::from).toList();

        return new MallItemPageVO(records, result.getTotal(), pageNo, size, fetchUserPoints(userId));
    }

    @Override
    public MallItemDetailVO detail(Long userId, Long itemId) {
        MallItem item = requireItem(itemId);
        boolean owned = wardrobeItemMapper.countOwned(userId, itemId) > 0;
        long purchaseCount = purchaseRecordMapper.countByItem(itemId);
        return MallItemDetailVO.from(item, owned, purchaseCount);
    }

    /** 加载商品；已下架商品依然可查详情（用户需要能看到自己已拥有的装扮） */
    MallItem requireItem(Long itemId) {
        if (itemId == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "商品 ID 不能为空");
        }
        MallItem item = mallItemMapper.selectById(itemId);
        if (item == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "商品不存在");
        }
        return item;
    }

    /**
     * 查询用户光阴砂余额。
     *
     * <p>失败返回 null 而不是抛异常：商城应该能被浏览，
     * 不该因为用户服务抖一下就让整个商品列表 500。</p>
     */
    private Integer fetchUserPoints(Long userId) {
        if (userId == null) {
            return null;
        }
        try {
            Result<UserPointsDto> result = userClient.points(userId, 1);
            if (result != null && result.getCode() == 0 && result.getData() != null) {
                return result.getData().getPoints();
            }
            log.warn("查询光阴砂余额返回异常结果：{}", result == null ? null : result.getMessage());
            return null;
        } catch (Exception e) {
            log.warn("查询光阴砂余额失败，列表降级为不显示余额：{}", e.getMessage());
            return null;
        }
    }
}
