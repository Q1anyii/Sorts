package com.sorts.mall.service.impl;

import com.sorts.common.exception.BizException;
import com.sorts.mall.dto.WardrobeItemVO;
import com.sorts.mall.entity.MallItem;
import com.sorts.mall.entity.WardrobeItem;
import com.sorts.mall.mapper.MallItemMapper;
import com.sorts.mall.mapper.WardrobeItemMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 装扮仓库单元测试：列表组装（含 N+1 规避与缺失商品降级）与切换互斥。
 *
 * @author sorts
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WardrobeServiceImplTest {

    private static final Long USER_ID = 61L;

    private static final Long SKIN_ITEM_ID = 1L;

    private static final Long STICKER_ITEM_ID = 7L;

    @Mock
    private WardrobeItemMapper wardrobeItemMapper;

    @Mock
    private MallItemMapper mallItemMapper;

    @InjectMocks
    private WardrobeServiceImpl wardrobeService;

    private MallItem skin;

    private WardrobeItem skinEntry;

    @BeforeEach
    void setUp() {
        skin = new MallItem();
        skin.setId(SKIN_ITEM_ID);
        skin.setName("织锦流光·皮肤");
        skin.setType("SKIN");
        skin.setPrice(200);

        skinEntry = new WardrobeItem();
        skinEntry.setId(500L);
        skinEntry.setUserId(USER_ID);
        skinEntry.setItemId(SKIN_ITEM_ID);
        skinEntry.setItemType("SKIN");
        skinEntry.setIsActive(1);
        skinEntry.setPurchasedAt(LocalDateTime.of(2026, 9, 1, 10, 0));

        when(mallItemMapper.selectBatchIds(ArgumentMatchers.<Collection<Long>>any()))
                .thenReturn(List.of(skin));
    }

    @Test
    @DisplayName("list：空仓库返回空集合而不是 null")
    void listEmpty() {
        when(wardrobeItemMapper.selectList(any())).thenReturn(List.of());
        assertTrue(wardrobeService.list(USER_ID).isEmpty());
        verify(mallItemMapper, never()).selectBatchIds(ArgumentMatchers.<Collection<Long>>any());
    }

    @Test
    @DisplayName("list：一次批量取商品，条目与商品明细正确配对")
    void listPairsItems() {
        when(wardrobeItemMapper.selectList(any())).thenReturn(List.of(skinEntry));

        List<WardrobeItemVO> result = wardrobeService.list(USER_ID);

        assertEquals(1, result.size());
        WardrobeItemVO vo = result.get(0);
        assertEquals(500L, vo.getId());
        assertEquals(USER_ID, vo.getUserId());
        assertTrue(vo.getIsActive());
        assertEquals("织锦流光·皮肤", vo.getItem().getName());
        verify(mallItemMapper).selectBatchIds(ArgumentMatchers.<Collection<Long>>any());
    }

    @Test
    @DisplayName("list：商品记录缺失时条目仍返回，item 为 null 而不是整仓查询失败")
    void listToleratesMissingItem() {
        when(wardrobeItemMapper.selectList(any())).thenReturn(List.of(skinEntry));
        when(mallItemMapper.selectBatchIds(ArgumentMatchers.<Collection<Long>>any())).thenReturn(List.of());

        List<WardrobeItemVO> result = wardrobeService.list(USER_ID);

        assertEquals(1, result.size());
        assertNull(result.get(0).getItem());
        assertEquals(500L, result.get(0).getId());
    }

    @Test
    @DisplayName("activate：未拥有该装扮时报 404（顺带挡掉通过切换接口白嫖）")
    void activateRejectsNotOwned() {
        when(wardrobeItemMapper.selectOne(any())).thenReturn(null);

        assertThrows(BizException.class, () -> wardrobeService.activate(USER_ID, SKIN_ITEM_ID, "SKIN"));
        verify(wardrobeItemMapper, never()).activate(anyLong(), anyLong());
    }

    @Test
    @DisplayName("activate：缺少装扮 ID 时报参数错误")
    void activateRejectsNullId() {
        assertThrows(BizException.class, () -> wardrobeService.activate(USER_ID, null, "SKIN"));
    }

    @Test
    @DisplayName("activate：互斥类型先取消同类生效项再置为使用中")
    void activateExclusiveType() {
        when(wardrobeItemMapper.selectOne(any())).thenReturn(skinEntry);

        wardrobeService.activate(USER_ID, SKIN_ITEM_ID, "SKIN");

        verify(wardrobeItemMapper).deactivateByType(USER_ID, "SKIN");
        verify(wardrobeItemMapper).activate(USER_ID, SKIN_ITEM_ID);
    }

    @Test
    @DisplayName("activate：贴纸等可叠加类型不做同类互斥")
    void activateNonExclusiveType() {
        WardrobeItem sticker = new WardrobeItem();
        sticker.setId(501L);
        sticker.setUserId(USER_ID);
        sticker.setItemId(STICKER_ITEM_ID);
        sticker.setItemType("STICKER");
        sticker.setIsActive(0);
        when(wardrobeItemMapper.selectOne(any())).thenReturn(sticker);

        wardrobeService.activate(USER_ID, STICKER_ITEM_ID, "STICKER");

        verify(wardrobeItemMapper, never()).deactivateByType(anyLong(), anyString());
        verify(wardrobeItemMapper).activate(USER_ID, STICKER_ITEM_ID);
    }

    @Test
    @DisplayName("activate：请求类型与仓库记录不一致时拒绝，防止同类装扮同时生效")
    void activateRejectsTypeMismatch() {
        when(wardrobeItemMapper.selectOne(any())).thenReturn(skinEntry);

        assertThrows(BizException.class, () -> wardrobeService.activate(USER_ID, SKIN_ITEM_ID, "AVATAR"));
        verify(wardrobeItemMapper, never()).activate(anyLong(), anyLong());
    }

    @Test
    @DisplayName("activate：不传类型时按仓库记录判定（契约允许省略）")
    void activateWithoutType() {
        when(wardrobeItemMapper.selectOne(any())).thenReturn(skinEntry);

        wardrobeService.activate(USER_ID, SKIN_ITEM_ID, null);

        verify(wardrobeItemMapper).deactivateByType(USER_ID, "SKIN");
        verify(wardrobeItemMapper).activate(USER_ID, SKIN_ITEM_ID);
    }

    @Test
    @DisplayName("activate：仓库记录类型为脏数据时按可叠加处理，不让切换失败")
    void activateToleratesDirtyType() {
        WardrobeItem dirty = new WardrobeItem();
        dirty.setId(502L);
        dirty.setUserId(USER_ID);
        dirty.setItemId(SKIN_ITEM_ID);
        dirty.setItemType("MYSTERY");
        dirty.setIsActive(0);
        when(wardrobeItemMapper.selectOne(any())).thenReturn(dirty);

        wardrobeService.activate(USER_ID, SKIN_ITEM_ID, "MYSTERY");

        verify(wardrobeItemMapper, never()).deactivateByType(anyLong(), anyString());
        verify(wardrobeItemMapper).activate(USER_ID, SKIN_ITEM_ID);
    }

    @Test
    @DisplayName("list：使用中的装扮排在前面（依赖 mapper 排序，这里校验映射不丢失该字段）")
    void listKeepsActiveFlag() {
        WardrobeItem inactive = new WardrobeItem();
        inactive.setId(503L);
        inactive.setUserId(USER_ID);
        inactive.setItemId(SKIN_ITEM_ID);
        inactive.setItemType("SKIN");
        inactive.setIsActive(0);
        when(wardrobeItemMapper.selectList(any())).thenReturn(List.of(skinEntry, inactive));

        List<WardrobeItemVO> result = wardrobeService.list(USER_ID);

        assertTrue(result.get(0).getIsActive());
        assertFalse(result.get(1).getIsActive());
    }
}
