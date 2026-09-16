package com.sorts.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sorts.mall.entity.WardrobeItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 装扮仓库数据访问。
 *
 * @author sorts
 */
@Mapper
public interface WardrobeItemMapper extends BaseMapper<WardrobeItem> {

    /** 是否已拥有某装扮 */
    @Select("SELECT COUNT(*) FROM t_wardrobe_item "
            + "WHERE user_id = #{userId} AND item_id = #{itemId} AND deleted = 0")
    int countOwned(@Param("userId") Long userId, @Param("itemId") Long itemId);

    /**
     * 取消同类型装扮的「使用中」标记。
     *
     * <p>先全部置 0 再置 1，保证同一类型最多只有一个生效项，
     * 不需要为此引入额外的一致性保障。</p>
     */
    @Update("UPDATE t_wardrobe_item SET is_active = 0 "
            + "WHERE user_id = #{userId} AND item_type = #{itemType} AND is_active = 1 AND deleted = 0")
    int deactivateByType(@Param("userId") Long userId, @Param("itemType") String itemType);

    /** 将指定装扮置为使用中 */
    @Update("UPDATE t_wardrobe_item SET is_active = 1 "
            + "WHERE user_id = #{userId} AND item_id = #{itemId} AND deleted = 0")
    int activate(@Param("userId") Long userId, @Param("itemId") Long itemId);
}
