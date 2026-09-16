package com.sorts.mall.service.impl;

import com.sorts.common.exception.BizException;
import com.sorts.mall.dto.PurchaseResultVO;
import com.sorts.mall.entity.MallItem;
import com.sorts.mall.entity.PurchaseRecord;
import com.sorts.mall.entity.WardrobeItem;
import com.sorts.mall.mapper.MallItemMapper;
import com.sorts.mall.mapper.PurchaseRecordMapper;
import com.sorts.mall.mapper.WardrobeItemMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 购买落库执行器单元测试：库存扣减的两种形态（限量 / 无限）与快照字段。
 *
 * @author sorts
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PurchasePersisterTest {

    private static final Long USER_ID = 51L;

    @Mock
    private MallItemMapper mallItemMapper;

    @Mock
    private PurchaseRecordMapper purchaseRecordMapper;

    @Mock
    private WardrobeItemMapper wardrobeItemMapper;

    @InjectMocks
    private PurchasePersister persister;

    private MallItem item;

    @BeforeEach
    void setUp() {
        item = new MallItem();
        item.setId(4L);
        item.setName("鎏金梭·头像框");
        item.setType("AVATAR");
        item.setPrice(150);
        item.setStock(50);
        item.setStatus("ON_SALE");
        when(mallItemMapper.deductStock(4L)).thenReturn(1);
    }

    @Test
    @DisplayName("限量商品：扣库存、写购买记录快照、发装扮（默认不上身）")
    void persistLimitedItem() {
        PurchaseResultVO result = persister.persist(USER_ID, item, 849);

        verify(mallItemMapper).deductStock(4L);

        ArgumentCaptor<PurchaseRecord> recordCaptor = ArgumentCaptor.forClass(PurchaseRecord.class);
        verify(purchaseRecordMapper).insert(recordCaptor.capture());
        PurchaseRecord record = recordCaptor.getValue();
        assertEquals(USER_ID, record.getUserId());
        assertEquals(4L, record.getItemId());
        assertEquals("鎏金梭·头像框", record.getItemName());
        assertEquals(150, record.getPrice());
        assertEquals(849, record.getPointsAfter());

        ArgumentCaptor<WardrobeItem> wardrobeCaptor = ArgumentCaptor.forClass(WardrobeItem.class);
        verify(wardrobeItemMapper).insert(wardrobeCaptor.capture());
        WardrobeItem wardrobe = wardrobeCaptor.getValue();
        assertEquals("AVATAR", wardrobe.getItemType());
        assertEquals(0, wardrobe.getIsActive());
        assertEquals(0, wardrobe.getDeleted());
        assertEquals(record.getId(), wardrobe.getPurchaseId());

        assertEquals(849, result.getRemainingPoints());
    }

    @Test
    @DisplayName("无限库存商品：跳过扣库存，其余流程一致")
    void persistUnlimitedItemSkipsStock() {
        item.setStock(-1);

        persister.persist(USER_ID, item, 100);

        verify(mallItemMapper, never()).deductStock(ArgumentMatchers.<Long>any());
        verify(purchaseRecordMapper).insert(ArgumentMatchers.<PurchaseRecord>any());
        verify(wardrobeItemMapper).insert(ArgumentMatchers.<WardrobeItem>any());
    }

    @Test
    @DisplayName("库存已被抢完：条件更新返回 0 行，抛冲突且不写任何记录（事务回滚）")
    void persistRejectsSoldOut() {
        when(mallItemMapper.deductStock(4L)).thenReturn(0);

        BizException e = assertThrows(BizException.class, () -> persister.persist(USER_ID, item, 849));
        assertEquals(409, e.getCode());
        assertEquals("商品已售罄", e.getMessage());
        verify(purchaseRecordMapper, never()).insert(any(PurchaseRecord.class));
        verify(wardrobeItemMapper, never()).insert(any(WardrobeItem.class));
    }
}
