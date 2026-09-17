package com.sorts.schedule.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sorts.schedule.entity.ParentPlan;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * 主计划数据访问。
 *
 * @author sorts
 */
@Mapper
public interface ParentPlanMapper extends BaseMapper<ParentPlan> {

    /**
     * 属主校验 + 软删除。
     */
    @Update("UPDATE t_parent_plan SET deleted = 1, deleted_at = NOW(), updated_at = NOW() "
            + "WHERE id = #{id} AND user_id = #{userId} AND deleted = 0")
    int softDeleteOwned(@Param("userId") Long userId, @Param("id") Long id);

    /**
     * 批量聚合子计划统计（总数 / 已完成数），供主计划列表展示进度。
     */
    @Select("<script>"
            + "SELECT parent_id AS parentId, COUNT(*) AS total, "
            + "SUM(CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END) AS done "
            + "FROM t_schedule WHERE deleted = 0 AND parent_id IN "
            + "<foreach collection='parentIds' item='pid' open='(' separator=',' close=')'>#{pid}</foreach> "
            + "GROUP BY parent_id"
            + "</script>")
    List<Map<String, Object>> aggregateChildren(@Param("parentIds") List<Long> parentIds);

    /**
     * 删除主计划时解除其全部子计划归属（子计划保留为独立日程）。
     */
    @Update("UPDATE t_schedule SET parent_id = NULL, updated_at = NOW() "
            + "WHERE parent_id = #{id} AND user_id = #{userId} AND deleted = 0")
    int clearChildren(@Param("userId") Long userId, @Param("id") Long id);

    /**
     * 批量挂载子计划（已属于其他主计划的日程会被改挂到当前主计划）。
     */
    @Update("<script>"
            + "UPDATE t_schedule SET parent_id = #{id}, updated_at = NOW() "
            + "WHERE user_id = #{userId} AND deleted = 0 AND id IN "
            + "<foreach collection='scheduleIds' item='sid' open='(' separator=',' close=')'>#{sid}</foreach>"
            + "</script>")
    int attachChildren(@Param("userId") Long userId, @Param("id") Long id,
                       @Param("scheduleIds") List<Long> scheduleIds);

    /**
     * 移出单个子计划（仅当它确实挂在该主计划下）。
     */
    @Update("UPDATE t_schedule SET parent_id = NULL, updated_at = NOW() "
            + "WHERE id = #{scheduleId} AND user_id = #{userId} AND parent_id = #{id} AND deleted = 0")
    int detachChild(@Param("userId") Long userId, @Param("id") Long id, @Param("scheduleId") Long scheduleId);
}
