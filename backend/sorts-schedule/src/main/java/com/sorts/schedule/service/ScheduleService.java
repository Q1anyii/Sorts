package com.sorts.schedule.service;

import com.sorts.common.result.PageData;
import com.sorts.schedule.dto.ScheduleQuery;
import com.sorts.schedule.dto.ScheduleSaveRequest;
import com.sorts.schedule.dto.ScheduleVO;

import java.util.List;

/**
 * 日程基础服务：增删改查与批量创建。
 *
 * @author sorts
 */
public interface ScheduleService {

    /** 创建日程 */
    ScheduleVO create(Long userId, ScheduleSaveRequest request);

    /** 批量创建（AI 规划采纳 / 导入场景），返回创建成功的日程 */
    List<ScheduleVO> batchCreate(Long userId, List<ScheduleSaveRequest> requests);

    /** 更新日程（仅更新传入的非空字段，终态日程不可改） */
    ScheduleVO update(Long userId, Long scheduleId, ScheduleSaveRequest request);

    /** 逻辑删除日程 */
    void delete(Long userId, Long scheduleId);

    /**
     * 批量逻辑删除：事务内逐条软删除，任意一条不存在/越权即整体回滚，
     * 保证全部成功或全部失败。
     */
    void deleteBatch(Long userId, java.util.List<Long> scheduleIds);

    /** 查询详情（含属主校验） */
    ScheduleVO get(Long userId, Long scheduleId);

    /** 条件分页查询 */
    PageData<ScheduleVO> list(Long userId, ScheduleQuery query);

    /** 加载实体并校验属主（供计时服务与统计服务复用） */
    com.sorts.schedule.entity.Schedule requireOwned(Long userId, Long scheduleId);

    /**
     * 加载区间内全部日程（不分页，按计划开始时间升序）。
     *
     * <p>日历与统计聚合全量拉取后内存分组：个人日程数据量小，
     * 换来实现简单、口径统一、单测无需 mock 复杂 SQL。</p>
     */
    List<com.sorts.schedule.entity.Schedule> listInRange(Long userId, java.time.LocalDate start, java.time.LocalDate end);

    /** 即将开始的日程（今日 00:00 起的待开始日程，按计划时间升序，最多 limit 条） */
    List<com.sorts.schedule.entity.Schedule> listUpcoming(Long userId, int limit);

    /**
     * 跨用户查询「计划开始时间落在指定窗口内」的待开始日程（内部接口专用）。
     *
     * <p>供通知服务的定时提醒扫描调用：提醒是按用户各自的「提前分钟数」触发的，
     * 因此通知服务按最大提前量拉一个宽窗口，再自行按用户过滤。</p>
     *
     * @param from  窗口起点（含）
     * @param to    窗口终点（含）
     * @param limit 最多返回条数，服务端会再夹紧上限
     */
    List<com.sorts.schedule.dto.ReminderCandidateVO> listReminderCandidates(java.time.LocalDateTime from,
                                                                            java.time.LocalDateTime to,
                                                                            int limit);
}
