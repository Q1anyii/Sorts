package com.sorts.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sorts.mall.entity.MallItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 商品数据访问。
 *
 * @author sorts
 */
@Mapper
public interface MallItemMapper extends BaseMapper<MallItem> {

    /**
     * 扣减一件库存（限量商品专用）。
     *
     * <p>{@code AND stock > 0} 是数据库层面的最后一道防线：
     * 即便分布式锁因超时提前释放，也不可能有事务把库存扣成负数。</p>
     *
     * @return 1 扣减成功；0 表示已售罄
     */
    @Update("UPDATE t_mall_item SET stock = stock - 1 WHERE id = #{id} AND stock > 0")
    int deductStock(@Param("id") Long id);

    /**
     * 归还一件库存（购买后续步骤失败时补偿用）。
     *
     * <p>同样带 {@code stock >= 0} 条件：无限库存商品（-1）从未被扣减过，
     * 若被误补偿就会把 -1 变成 0，反而让商品变成「已售罄」。</p>
     */
    @Update("UPDATE t_mall_item SET stock = stock + 1 WHERE id = #{id} AND stock >= 0")
    int restoreStock(@Param("id") Long id);
}
