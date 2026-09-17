package com.sorts.mall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sorts.common.exception.BizException;
import com.sorts.common.result.ErrorCode;
import com.sorts.mall.dto.WardrobeItemVO;
import com.sorts.mall.entity.MallItem;
import com.sorts.mall.entity.WardrobeItem;
import com.sorts.mall.enums.ItemType;
import com.sorts.mall.mapper.MallItemMapper;
import com.sorts.mall.mapper.WardrobeItemMapper;
import com.sorts.mall.service.WardrobeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 装扮仓库服务实现。
 *
 * @author sorts
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WardrobeServiceImpl implements WardrobeService {

    private final WardrobeItemMapper wardrobeItemMapper;

    private final MallItemMapper mallItemMapper;

    @Override
    public List<WardrobeItemVO> list(Long userId) {
        List<WardrobeItem> owned = wardrobeItemMapper.selectList(new LambdaQueryWrapper<WardrobeItem>()
                .eq(WardrobeItem::getUserId, userId)
                // 使用中的排最前，其余按获得时间倒序
                .orderByDesc(WardrobeItem::getIsActive)
                .orderByDesc(WardrobeItem::getPurchasedAt)
                .orderByDesc(WardrobeItem::getId));
        if (owned.isEmpty()) {
            return List.of();
        }

        // 一次性把商品取全：按条目逐个查商品会变成 N+1
        Set<Long> itemIds = owned.stream().map(WardrobeItem::getItemId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, MallItem> items = mallItemMapper.selectBatchIds(itemIds).stream()
                .collect(Collectors.toMap(MallItem::getId, Function.identity()));

        List<WardrobeItemVO> result = new ArrayList<>(owned.size());
        for (WardrobeItem wardrobe : owned) {
            MallItem item = items.get(wardrobe.getItemId());
            if (item == null) {
                // 商品被物理删除属于异常运维场景，条目仍然返回（item 为空），
                // 让前端能看到「有这件装扮但详情缺失」，而不是让整个仓库查询失败
                log.warn("装扮条目对应的商品不存在：userId={}, itemId={}", userId, wardrobe.getItemId());
            }
            result.add(WardrobeItemVO.from(wardrobe, item));
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void activate(Long userId, Long itemId, String type) {
        if (itemId == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "装扮 ID 不能为空");
        }
        WardrobeItem wardrobe = wardrobeItemMapper.selectOne(new LambdaQueryWrapper<WardrobeItem>()
                .eq(WardrobeItem::getUserId, userId)
                .eq(WardrobeItem::getItemId, itemId));
        if (wardrobe == null) {
            // 未拥有按「不存在」处理，顺带挡掉「通过切换接口白嫖装扮」
            throw new BizException(ErrorCode.NOT_FOUND, "尚未拥有该装扮");
        }

        String itemType = wardrobe.getItemType();
        if (StringUtils.hasText(type) && !type.trim().equalsIgnoreCase(itemType)) {
            // 类型由服务端依据仓库记录判定：前端传错类型就能让多个同类装扮同时生效
            throw new BizException(ErrorCode.PARAM_ERROR, "装扮类型与仓库记录不一致");
        }

        ItemType parsed = ItemType.from(itemType);
        if (parsed == null) {
            log.warn("装扮条目类型非法，按可叠加装扮处理：userId={}, itemId={}, type={}", userId, itemId, itemType);
        } else if (parsed.isExclusiveActive()) {
            // 先全部置 0 再置 1：同一类型最多一个生效，不需要额外的一致性保障
            wardrobeItemMapper.deactivateByType(userId, itemType);
        }
        wardrobeItemMapper.activate(userId, itemId);
        log.info("切换装扮：userId={}, itemId={}, type={}", userId, itemId, itemType);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deactivate(Long userId, String type) {
        if (StringUtils.hasText(type)) {
            ItemType parsed = ItemType.from(type.trim());
            if (parsed == null) {
                throw new BizException(ErrorCode.PARAM_ERROR, "装扮类型不合法");
            }
            wardrobeItemMapper.deactivateByType(userId, parsed.name());
            log.info("卸下装扮：userId={}, type={}", userId, parsed.name());
        } else {
            wardrobeItemMapper.deactivateAll(userId);
            log.info("卸下全部装扮：userId={}", userId);
        }
    }
}
