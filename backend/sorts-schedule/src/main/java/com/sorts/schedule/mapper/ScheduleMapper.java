package com.sorts.schedule.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sorts.schedule.entity.Schedule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 日程数据访问。
 *
 * <p>统计类查询（按天分组、标签分布）在服务层基于列表聚合，
 * 数据量在个人日程场景下可控，同时便于单元测试与口径统一。</p>
 *
 * @author sorts
 */
@Mapper
public interface ScheduleMapper extends BaseMapper<Schedule> {

    /**
     * 属主校验 + 软删除（带审计时间），一次 UPDATE 完成：
     * 影响行数为 0 表示记录不存在或不属于该用户，用于事务内整体回滚。
     */
    @Update("UPDATE t_schedule SET deleted = 1, deleted_at = NOW(), updated_at = NOW() "
            + "WHERE id = #{id} AND user_id = #{userId} AND deleted = 0")
    int softDeleteOwned(@Param("userId") Long userId, @Param("id") Long id);

    /** 批量软删除：整体校验属主，影响行数 < 请求数时由服务层抛错回滚 */
    @Update("<script>"
            + "UPDATE t_schedule SET deleted = 1, deleted_at = NOW(), updated_at = NOW() "
            + "WHERE user_id = #{userId} AND deleted = 0 "
            + "AND id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
            + "</script>")
    int softDeleteBatchOwned(@Param("userId") Long userId, @Param("ids") List<Long> ids);

    /**
     * PENDING 过期未开始 → 批量标 TIMEOUT（终态，仅可取消）。
     *
     * <p>条件更新原子执行：当前时间晚于「计划开始 + 计划时长」即命中。
     * 与开梭请求并发时，先到者抢占——本语句条件不匹配已开梭记录，开梭侧
     * 状态机校验兜住另一方。影响行数即本次标记数。</p>
     */
    @Update("UPDATE t_schedule SET status = 'TIMEOUT', updated_at = NOW() "
            + "WHERE status = 'PENDING' AND deleted = 0 "
            + "AND planned_start_time + INTERVAL planned_duration MINUTE < NOW() "
            + "LIMIT 100")
    int markPendingExpiredAsTimeout();
}
