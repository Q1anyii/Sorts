package com.sorts.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sorts.user.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 用户数据访问。
 *
 * <p>积分变动走条件更新 SQL：由数据库保证「余额不会为负」，避免并发下的超扣。</p>
 *
 * @author sorts
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

    /**
     * 原子增减积分。
     *
     * @return 影响行数，0 表示余额不足或用户不存在
     */
    @Update("UPDATE t_user SET points = points + #{delta} WHERE id = #{userId} AND points + #{delta} >= 0")
    int changePoints(@Param("userId") Long userId, @Param("delta") int delta);

    @Select("SELECT points FROM t_user WHERE id = #{userId}")
    Integer selectPoints(@Param("userId") Long userId);
}
