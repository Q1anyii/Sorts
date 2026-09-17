package com.sorts.schedule.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sorts.common.exception.BizException;
import com.sorts.schedule.dto.ScheduleQuery;
import com.sorts.schedule.dto.ScheduleSaveRequest;
import com.sorts.schedule.dto.ScheduleVO;
import com.sorts.schedule.entity.Schedule;
import com.sorts.schedule.enums.ScheduleStatus;
import com.sorts.schedule.mapper.ScheduleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 日程基础服务单元测试：CRUD、字段默认值、终态保护、属主校验、分页归一。
 *
 * @author sorts
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ScheduleServiceImplTest {

    private static final Long USER_ID = 7L;

    @Mock
    private ScheduleMapper scheduleMapper;

    @InjectMocks
    private ScheduleServiceImpl scheduleService;

    private Schedule owned;

    @BeforeEach
    void setUp() {
        owned = new Schedule();
        owned.setId(100L);
        owned.setUserId(USER_ID);
        owned.setTitle("织一段代码");
        owned.setStatus(ScheduleStatus.PENDING.name());
        owned.setPriority("MEDIUM");
        owned.setActualDuration(0);
        owned.setDeleted(0);
    }

    @Test
    @DisplayName("create：状态默认 PENDING、优先级默认 MEDIUM，标签以逗号拼接存储")
    void createAppliesDefaults() {
        ScheduleSaveRequest request = new ScheduleSaveRequest();
        request.setTitle("学习 Spring Cloud");
        request.setTags(List.of(" 学习 ", "Java", "学习"));

        ScheduleVO vo = scheduleService.create(USER_ID, request);

        assertEquals(ScheduleStatus.PENDING.name(), vo.getStatus());
        assertEquals("MEDIUM", vo.getPriority());
        // 去空格 + 去重
        assertEquals(List.of("学习", "Java"), vo.getTags());
        assertEquals(USER_ID, vo.getUserId());
        verify(scheduleMapper).insert(ArgumentMatchers.<Schedule>any());
    }

    @Test
    @DisplayName("create：优先级非法直接拒绝，不做静默兜底")
    void createRejectsIllegalPriority() {
        ScheduleSaveRequest request = new ScheduleSaveRequest();
        request.setTitle("x");
        request.setPriority("SUPER");

        assertThrows(BizException.class, () -> scheduleService.create(USER_ID, request));
        verify(scheduleMapper, never()).insert(ArgumentMatchers.<Schedule>any());
    }

    @Test
    @DisplayName("batchCreate：批量落库并返回全部创建结果")
    void batchCreateInsertsAll() {
        ScheduleSaveRequest first = new ScheduleSaveRequest();
        first.setTitle("A");
        ScheduleSaveRequest second = new ScheduleSaveRequest();
        second.setTitle("B");

        List<ScheduleVO> created = scheduleService.batchCreate(USER_ID, List.of(first, second));

        assertEquals(2, created.size());
        assertEquals("A", created.get(0).getTitle());
        verify(scheduleMapper, org.mockito.Mockito.times(2)).insert(ArgumentMatchers.<Schedule>any());
    }

    @Test
    @DisplayName("batchCreate：空列表拒绝，避免脏数据与无意义事务")
    void batchCreateRejectsEmpty() {
        assertThrows(BizException.class, () -> scheduleService.batchCreate(USER_ID, List.of()));
        assertThrows(BizException.class, () -> scheduleService.batchCreate(USER_ID, null));
    }

    @Test
    @DisplayName("update：只覆盖传入字段，未传字段保持原值")
    void updateOnlyOverwritesProvidedFields() {
        owned.setDescription("原描述");
        owned.setPlannedDuration(60);
        when(scheduleMapper.selectById(100L)).thenReturn(owned);

        ScheduleSaveRequest request = new ScheduleSaveRequest();
        request.setTitle("新标题");

        ScheduleVO vo = scheduleService.update(USER_ID, 100L, request);

        assertEquals("新标题", vo.getTitle());
        assertEquals("原描述", vo.getDescription());
        assertEquals(60, vo.getPlannedDuration());
        verify(scheduleMapper).updateById(owned);
    }

    @Test
    @DisplayName("update：已落梭 / 已取消的日程不可再修改")
    void updateRejectsFinalStatus() {
        owned.setStatus(ScheduleStatus.COMPLETED.name());
        when(scheduleMapper.selectById(100L)).thenReturn(owned);

        ScheduleSaveRequest request = new ScheduleSaveRequest();
        request.setTitle("改不动");

        assertThrows(BizException.class, () -> scheduleService.update(USER_ID, 100L, request));
        verify(scheduleMapper, never()).updateById(any(Schedule.class));
    }

    @Test
    @DisplayName("delete：走自定义软删除 UPDATE（属主条件一次完成），而非物理删除")
    void deleteUsesLogicDelete() {
        when(scheduleMapper.softDeleteOwned(USER_ID, 100L)).thenReturn(1);

        scheduleService.delete(USER_ID, 100L);

        // 不再走 selectById + deleteById 两段式，避免中间态
        verify(scheduleMapper).softDeleteOwned(USER_ID, 100L);
    }

    @Test
    @DisplayName("requireOwned：他人日程按「不存在」处理，不暴露存在性")
    void requireOwnedHidesOtherUsersSchedule() {
        Schedule other = new Schedule();
        other.setId(200L);
        other.setUserId(999L);
        when(scheduleMapper.selectById(200L)).thenReturn(other);

        BizException e = assertThrows(BizException.class, () -> scheduleService.requireOwned(USER_ID, 200L));
        assertEquals("日程不存在", e.getMessage());
    }

    @Test
    @DisplayName("requireOwned：id 为空或记录不存在均按不存在处理")
    void requireOwnedHandlesMissing() {
        assertThrows(BizException.class, () -> scheduleService.requireOwned(USER_ID, null));
        when(scheduleMapper.selectById(404L)).thenReturn(null);
        assertThrows(BizException.class, () -> scheduleService.requireOwned(USER_ID, 404L));
    }

    @Test
    @DisplayName("list：分页参数归一（page 最小 1、pageSize 上限 200）")
    void listNormalizesPagination() {
        ScheduleQuery query = new ScheduleQuery();
        query.setPage(0);
        query.setPageSize(9999);

        Page<Schedule> page = new Page<>(1, 200);
        page.setRecords(List.of(owned));
        page.setTotal(1);
        when(scheduleMapper.selectPage(any(), any())).thenReturn(page);

        var result = scheduleService.list(USER_ID, query);

        assertEquals(1L, result.getPage());
        assertEquals(200L, result.getPageSize());
        assertEquals(1L, result.getTotal());
        assertEquals(1, result.getList().size());
    }

    @Test
    @DisplayName("list：view 非法直接拒绝，避免前端拼错参数后静默返回全量")
    void listRejectsIllegalView() {
        ScheduleQuery query = new ScheduleQuery();
        query.setView("quarter");

        assertThrows(BizException.class, () -> scheduleService.list(USER_ID, query));
    }

    @Test
    @DisplayName("listInRange：边界可缺省，用于「全部」口径复用同一查询")
    void listInRangeSupportsOpenBounds() {
        when(scheduleMapper.selectList(any())).thenReturn(List.of(owned));

        assertEquals(1, scheduleService.listInRange(USER_ID, null, null).size());
        assertEquals(1, scheduleService.listInRange(USER_ID, java.time.LocalDate.now(), java.time.LocalDate.now()).size());
    }

    @Test
    @DisplayName("listUpcoming：limit 归一到 [1, 200]")
    void listUpcomingClampsLimit() {
        when(scheduleMapper.selectList(any())).thenReturn(List.of(owned));

        assertTrue(scheduleService.listUpcoming(USER_ID, 0).size() <= 200);
        assertTrue(scheduleService.listUpcoming(USER_ID, 5000).size() <= 200);
        verify(scheduleMapper, org.mockito.Mockito.times(2)).selectList(any());
    }

    @Test
    @DisplayName("VO 转换：标签拆分与空标签处理")
    void voSplitsTags() {
        owned.setTags("学习, Java ,,");
        ScheduleVO vo = ScheduleVO.from(owned);
        assertEquals(List.of("学习", "Java"), vo.getTags());

        owned.setTags(null);
        assertNull(owned.getTags());
        assertEquals(List.of(), ScheduleVO.from(owned).getTags());
    }

    @Test
    @DisplayName("VO 转换：时间为空、实体为 null 时均不抛异常")
    void voHandlesNullTimes() {
        owned.setPlannedStartTime(null);
        owned.setActualStartTime(null);
        ScheduleVO vo = ScheduleVO.from(owned);
        assertNull(vo.getPlannedStartTime());
        assertNull(vo.getActualStartTime());
        // 契约保护：null 实体映射为 null，而不是 NPE
        assertNull(ScheduleVO.from(null));
    }

    @Test
    @DisplayName("get：命中属主时返回详情")
    void getReturnsOwnedSchedule() {
        when(scheduleMapper.selectById(100L)).thenReturn(owned);
        assertEquals(100L, scheduleService.get(USER_ID, 100L).getId());
        verify(scheduleMapper, org.mockito.Mockito.atLeastOnce()).selectById(anyLong());
    }

    @Test
    @DisplayName("排序：priority 走语义排序，未知字段回落默认字段，注入串永远不会拼进 SQL")
    void sortingIsWhitelisted() {
        // URGENT 最高：DESC 时 URGENT 在前
        assertEquals("ORDER BY FIELD(priority, 'LOW', 'MEDIUM', 'HIGH', 'URGENT') DESC, id DESC",
                scheduleService.buildOrderBy("priority", "desc"));
        // 白名单字段正常映射
        assertEquals("ORDER BY created_at ASC, id DESC",
                scheduleService.buildOrderBy("createdAt", "asc"));
        assertEquals("ORDER BY actual_duration DESC, id DESC",
                scheduleService.buildOrderBy("actualDuration", null));
        // 非法 / 注入串一律回落默认排序
        String injection = scheduleService.buildOrderBy("planned_start_time; DROP TABLE t_schedule", "desc");
        assertEquals("ORDER BY planned_start_time DESC, id DESC", injection);
        assertFalse(injection.contains("DROP"));
        assertFalse(scheduleService.buildOrderBy(null, null).contains("null"));
    }

    @Test
    @DisplayName("提醒候选：时间窗口非法直接拒绝，避免退化成全量扫描")
    void reminderCandidatesRejectInvalidWindow() {
        LocalDateTime now = LocalDateTime.now();
        assertThrows(BizException.class,
                () -> scheduleService.listReminderCandidates(now, now, 10));
        assertThrows(BizException.class,
                () -> scheduleService.listReminderCandidates(now, now.minusMinutes(5), 10));
        assertThrows(BizException.class,
                () -> scheduleService.listReminderCandidates(null, now, 10));
        verify(scheduleMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("提醒候选：只带提醒所需字段，标签按逗号拆分为数组")
    void reminderCandidatesAreMapped() {
        owned.setTags(" 学习 , Java ");
        owned.setPlannedStartTime(LocalDateTime.of(2026, 9, 16, 9, 0));
        when(scheduleMapper.selectList(any())).thenReturn(List.of(owned));

        var candidates = scheduleService.listReminderCandidates(
                LocalDateTime.of(2026, 9, 16, 8, 0),
                LocalDateTime.of(2026, 9, 16, 10, 0), 20);

        assertEquals(1, candidates.size());
        var candidate = candidates.get(0);
        assertEquals(USER_ID, candidate.getUserId());
        assertEquals(100L, candidate.getScheduleId());
        assertEquals("织一段代码", candidate.getTitle());
        assertEquals(LocalDateTime.of(2026, 9, 16, 9, 0), candidate.getPlannedStartTime());
        assertEquals(List.of("学习", "Java"), candidate.getTags());
    }

    @Test
    @DisplayName("提醒候选：无匹配日程时返回空集合而不是 null")
    void reminderCandidatesEmptyWhenNone() {
        when(scheduleMapper.selectList(any())).thenReturn(List.of());
        assertTrue(scheduleService.listReminderCandidates(
                LocalDateTime.now(), LocalDateTime.now().plusMinutes(30), 10).isEmpty());
    }

    // ================= 删除：单条 + 批量（软删除 + 属主校验 + 整体回滚） =================

    @Test
    @DisplayName("delete：命中属主时软删除成功")
    void deleteSucceedsWhenOwned() {
        when(scheduleMapper.softDeleteOwned(USER_ID, 100L)).thenReturn(1);
        scheduleService.delete(USER_ID, 100L);
        verify(scheduleMapper).softDeleteOwned(USER_ID, 100L);
    }

    @Test
    @DisplayName("delete：不属于该用户或已删除时抛 NOT_FOUND，不静默")
    void deleteThrowsNotFoundWhenNotOwned() {
        when(scheduleMapper.softDeleteOwned(USER_ID, 100L)).thenReturn(0);
        BizException ex = assertThrows(BizException.class, () -> scheduleService.delete(USER_ID, 100L));
        assertEquals(404, ex.getCode());
        assertTrue(ex.getMessage().contains("日程不存在"));
    }

    @Test
    @DisplayName("deleteBatch：全部命中时一次性软删除")
    void deleteBatchSucceedsWhenAllOwned() {
        when(scheduleMapper.softDeleteBatchOwned(eq(USER_ID), anyList())).thenReturn(2);
        scheduleService.deleteBatch(USER_ID, List.of(1L, 2L));
        verify(scheduleMapper).softDeleteBatchOwned(eq(USER_ID), eq(List.of(1L, 2L)));
    }

    @Test
    @DisplayName("deleteBatch：空选择直接拒绝，不触碰数据库")
    void deleteBatchRejectsEmptySelection() {
        assertThrows(BizException.class, () -> scheduleService.deleteBatch(USER_ID, List.of()));
        verify(scheduleMapper, never()).softDeleteBatchOwned(anyLong(), anyList());
    }

    @Test
    @DisplayName("deleteBatch：超过 200 条上限直接拒绝")
    void deleteBatchRejectsOverLimit() {
        List<Long> ids = java.util.stream.IntStream.rangeClosed(1, 201).mapToObj(Long::valueOf).toList();
        BizException ex = assertThrows(BizException.class, () -> scheduleService.deleteBatch(USER_ID, ids));
        assertTrue(ex.getMessage().contains("200"));
        verify(scheduleMapper, never()).softDeleteBatchOwned(anyLong(), anyList());
    }

    @Test
    @DisplayName("deleteBatch：部分记录不存在或无权时整体回滚（全部成功或全部失败）")
    void deleteBatchRollsBackWhenAffectedMismatch() {
        when(scheduleMapper.softDeleteBatchOwned(eq(USER_ID), anyList())).thenReturn(1);
        BizException ex = assertThrows(BizException.class,
                () -> scheduleService.deleteBatch(USER_ID, List.of(1L, 2L)));
        assertTrue(ex.getMessage().contains("整体回滚"));
    }

    @Test
    @DisplayName("deleteBatch：重复 id 先去重，避免影响行数误判")
    void deleteBatchDistinctsIds() {
        when(scheduleMapper.softDeleteBatchOwned(eq(USER_ID), anyList())).thenReturn(2);
        scheduleService.deleteBatch(USER_ID, List.of(1L, 1L, 2L));
        verify(scheduleMapper).softDeleteBatchOwned(eq(USER_ID), eq(List.of(1L, 2L)));
    }
}
