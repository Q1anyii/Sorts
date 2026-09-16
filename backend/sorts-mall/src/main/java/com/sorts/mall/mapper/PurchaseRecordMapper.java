package com.sorts.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sorts.mall.entity.PurchaseRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 购买记录数据访问。
 *
 * @author sorts
 */
@Mapper
public interface PurchaseRecordMapper extends BaseMapper<PurchaseRecord> {

    /** 某商品的总购买人次（商品详情展示用） */
    @Select("SELECT COUNT(*) FROM t_purchase_record WHERE item_id = #{itemId}")
    long countByItem(@Param("itemId") Long itemId);
}
