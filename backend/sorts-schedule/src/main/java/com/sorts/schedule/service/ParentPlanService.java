package com.sorts.schedule.service;

import com.sorts.schedule.dto.ParentPlanSaveRequest;
import com.sorts.schedule.dto.ParentPlanVO;

import java.util.List;

/**
 * 主计划（长时间计划容器）业务接口。
 *
 * @author sorts
 */
public interface ParentPlanService {

    ParentPlanVO create(Long userId, ParentPlanSaveRequest req);

    /** 分页列表（含子计划聚合统计） */
    List<ParentPlanVO> page(Long userId, int page, int pageSize);

    long count(Long userId);

    ParentPlanVO detail(Long userId, Long id);

    ParentPlanVO update(Long userId, Long id, ParentPlanSaveRequest req);

    /** 软删除主计划并解除其全部子计划的归属（子计划保留为独立日程） */
    void delete(Long userId, Long id);

    /** 状态流转：start / pause / resume / complete，非法跃迁抛 409 */
    ParentPlanVO changeStatus(Long userId, Long id, String action);

    /** 批量挂载子计划（校验属主，重复挂载幂等） */
    ParentPlanVO attachChildren(Long userId, Long id, List<Long> scheduleIds);

    /** 移出单个子计划 */
    ParentPlanVO detachChild(Long userId, Long id, Long scheduleId);
}
