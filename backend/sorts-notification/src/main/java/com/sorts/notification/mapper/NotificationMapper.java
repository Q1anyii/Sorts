package com.sorts.notification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sorts.notification.entity.Notification;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 通知数据访问。
 *
 * @author sorts
 */
@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {

    /** 未读数量（前端角标） */
    @Select("SELECT COUNT(*) FROM t_notification WHERE user_id = #{userId} AND is_read = 0 AND deleted = 0")
    long countUnread(@Param("userId") Long userId);

    /**
     * 条件更新已读：把「属主校验」交给 SQL。
     *
     * <p>返回 0 行既能表示「不属于该用户」，也能表示「本来就已读」；
     * 前者需要报错、后者需要幂等成功，因此调用方先查一次归属再更新，
     * 这里只负责绝不越权改写他人数据。</p>
     *
     * @return 影响行数
     */
    @Update("UPDATE t_notification SET is_read = 1 "
            + "WHERE id = #{id} AND user_id = #{userId} AND is_read = 0 AND deleted = 0")
    int markRead(@Param("id") Long id, @Param("userId") Long userId);

    /** 全部标记已读，返回本次实际变更条数（便于前端提示） */
    @Update("UPDATE t_notification SET is_read = 1 WHERE user_id = #{userId} AND is_read = 0 AND deleted = 0")
    int markAllRead(@Param("userId") Long userId);

    /** 归属校验：他人通知按「不存在」处理，不暴露存在性 */
    @Select("SELECT COUNT(*) FROM t_notification WHERE id = #{id} AND user_id = #{userId} AND deleted = 0")
    int countOwned(@Param("id") Long id, @Param("userId") Long userId);
}
