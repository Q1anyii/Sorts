package com.sorts.mall.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sorts.common.exception.BizException;
import com.sorts.common.result.Result;
import com.sorts.mall.client.UserClient;
import com.sorts.mall.client.dto.UserPointsDto;
import com.sorts.mall.dto.MallItemDetailVO;
import com.sorts.mall.dto.MallItemPageVO;
import com.sorts.mall.entity.MallItem;
import com.sorts.mall.mapper.MallItemMapper;
import com.sorts.mall.mapper.PurchaseRecordMapper;
import com.sorts.mall.mapper.WardrobeItemMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 商城商品浏览单元测试：分页归一、类型校验、余额降级、详情组装。
 *
 * @author sorts
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MallServiceImplTest {

    private static final Long USER_ID = 31L;

    private static final Long ITEM_ID = 4L;

    @Mock
    private MallItemMapper mallItemMapper;

    @Mock
    private PurchaseRecordMapper purchaseRecordMapper;

    @Mock
    private WardrobeItemMapper wardrobeItemMapper;

    @Mock
    private UserClient userClient;

    @InjectMocks
    private MallServiceImpl mallService;

    private MallItem item;

    @BeforeEach
    void setUp() {
        item = new MallItem();
        item.setId(ITEM_ID);
        item.setName("鎏金梭·头像框");
        item.setType("AVATAR");
        item.setPrice(150);
        item.setStock(50);
        item.setStatus("ON_SALE");
        item.setSortOrder(70);

        UserPointsDto points = new UserPointsDto();
        points.setPoints(999);
        when(userClient.points(anyLong(), anyInt())).thenReturn(Result.success(points));
    }

    private void mockPage(List<MallItem> records) {
        Page<MallItem> page = new Page<>(1, 20);
        page.setRecords(records);
        page.setTotal(records.size());
        when(mallItemMapper.selectPage(any(), any())).thenReturn(page);
    }

    @Test
    @DisplayName("list：分页归一 + 附带用户光阴砂余额")
    void listNormalizesPagingAndCarriesPoints() {
        mockPage(List.of(item));

        MallItemPageVO result = mallService.list(USER_ID, null, 0, 0);

        assertEquals(1, result.getList().size());
        assertEquals(1, result.getPage());
        assertEquals(20, result.getPageSize());
        assertEquals(999, result.getUserPoints());
        assertEquals("鎏金梭·头像框", result.getList().get(0).getName());
    }

    @Test
    @DisplayName("list：pageSize 超上限被夹紧")
    void listClampsPageSize() {
        mockPage(List.of());
        assertEquals(200, mallService.list(USER_ID, null, 1, 9999).getPageSize());
    }

    @Test
    @DisplayName("list：非法商品类型直接报错，不静默返回全部商品")
    void listRejectsUnknownType() {
        assertThrows(BizException.class, () -> mallService.list(USER_ID, "WEAPON", 1, 20));
        verify(mallItemMapper, never()).selectPage(any(), any());
    }

    @Test
    @DisplayName("list：类型大小写不敏感")
    void listAcceptsLowercaseType() {
        mockPage(List.of(item));
        assertEquals(1, mallService.list(USER_ID, " avatar ", 1, 20).getList().size());
    }

    @Test
    @DisplayName("list：用户服务返回非成功码时余额降级为 null，列表照常返回")
    void listDegradesWhenPointsUnavailable() {
        mockPage(List.of(item));
        when(userClient.points(anyLong(), anyInt())).thenReturn(Result.fail(503, "服务不可用"));

        MallItemPageVO result = mallService.list(USER_ID, null, 1, 20);

        assertNull(result.getUserPoints());
        assertEquals(1, result.getList().size());
    }

    @Test
    @DisplayName("list：用户服务抛异常时同样降级，不让商品列表 500")
    void listDegradesWhenPointsThrows() {
        mockPage(List.of(item));
        when(userClient.points(anyLong(), anyInt())).thenThrow(new RuntimeException("connect timed out"));

        assertNull(mallService.list(USER_ID, null, 1, 20).getUserPoints());
    }

    @Test
    @DisplayName("detail：组装是否已拥有与购买人次")
    void detailCarriesOwnedAndPurchaseCount() {
        when(mallItemMapper.selectById(ITEM_ID)).thenReturn(item);
        when(wardrobeItemMapper.countOwned(USER_ID, ITEM_ID)).thenReturn(1);
        when(purchaseRecordMapper.countByItem(ITEM_ID)).thenReturn(12L);

        MallItemDetailVO vo = mallService.detail(USER_ID, ITEM_ID);

        assertEquals(ITEM_ID, vo.getId());
        assertTrue(vo.getOwned());
        assertEquals(12L, vo.getPurchaseCount());
        assertEquals(150, vo.getPrice());
    }

    @Test
    @DisplayName("detail：未拥有时为 false")
    void detailOwnedFalseWhenNotPurchased() {
        when(mallItemMapper.selectById(ITEM_ID)).thenReturn(item);
        when(wardrobeItemMapper.countOwned(USER_ID, ITEM_ID)).thenReturn(0);
        when(purchaseRecordMapper.countByItem(ITEM_ID)).thenReturn(0L);

        assertFalse(mallService.detail(USER_ID, ITEM_ID).getOwned());
    }

    @Test
    @DisplayName("detail：已下架商品仍可查看详情（用户需要看到自己已拥有的装扮）")
    void detailAllowsOffShelfItem() {
        item.setStatus("OFF_SHELF");
        when(mallItemMapper.selectById(ITEM_ID)).thenReturn(item);
        when(wardrobeItemMapper.countOwned(USER_ID, ITEM_ID)).thenReturn(1);
        when(purchaseRecordMapper.countByItem(ITEM_ID)).thenReturn(3L);

        assertEquals("OFF_SHELF", mallService.detail(USER_ID, ITEM_ID).getStatus());
    }

    @Test
    @DisplayName("detail：商品不存在时报 404")
    void detailRejectsMissingItem() {
        when(mallItemMapper.selectById(ITEM_ID)).thenReturn(null);
        assertThrows(BizException.class, () -> mallService.detail(USER_ID, ITEM_ID));
    }

    @Test
    @DisplayName("requireItem：缺少商品 ID 时给出参数错误")
    void requireItemRejectsNullId() {
        assertThrows(BizException.class, () -> mallService.requireItem(null));
    }
}
