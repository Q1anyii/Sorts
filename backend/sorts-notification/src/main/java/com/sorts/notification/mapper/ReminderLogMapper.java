package com.sorts.notification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sorts.notification.entity.ReminderLog;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * 提醒留痕数据访问。
 *
 * <p>幂等靠 {@code INSERT IGNORE} + 唯一键：一次原子写就能同时回答
 * 「这条提醒该不该发」，比「先查再插」少一次竞态窗口。</p>
 *
 * @author sorts
 */
@Mapper
public interface ReminderLogMapper extends BaseMapper<ReminderLog> {

    /**
     * 抢占提醒配额。
     *
     * @return 1 表示抢到（应发送）；0 表示重复（跳过）
     */
    @Insert("INSERT IGNORE INTO t_reminder_log (user_id, schedule_id, remind_at, created_at) "
            + "VALUES (#{userId}, #{scheduleId}, #{remindAt}, NOW())")
    int tryAcquire(@Param("userId") Long userId,
                   @Param("scheduleId") Long scheduleId,
                   @Param("remindAt") LocalDateTime remindAt);

    /** 清理过期留痕（留痕只为去重，不承担审计职责，过期即可删） */
    @Delete("DELETE FROM t_reminder_log WHERE created_at < #{before} LIMIT #{limit}")
    int deleteBefore(@Param("before") LocalDateTime before, @Param("limit") int limit);
}
