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
}
