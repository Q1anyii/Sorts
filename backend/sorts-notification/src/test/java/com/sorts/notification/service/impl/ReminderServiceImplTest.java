package com.sorts.notification.service.impl;

import com.sorts.common.result.Result;
import com.sorts.notification.client.ScheduleClient;
import com.sorts.notification.client.dto.ReminderCandidateDto;
import com.sorts.notification.config.NotificationProperties;
import com.sorts.notification.dto.CreateNotificationCommand;
import com.sorts.notification.entity.ReminderSetting;
import com.sorts.notification.mapper.ReminderLogMapper;
import com.sorts.notification.service.NotificationService;
import com.sorts.notification.service.ReminderSettingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 定时提醒服务单元测试。
 *
 * <p>重点覆盖「该不该提醒」的四类判断（未到点 / 免打扰 / 无渠道 / 重复）
 * 与依赖不可用时的降级行为。</p>
 *
 * @author sorts
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReminderServiceImplTest {

    private static final Long USER_ID = 21L;

    private static final Long SCHEDULE_ID = 300L;

    /** 固定「当前时间」，避免测试随时钟漂移 */
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 16, 9, 0);

    @Mock
    private ScheduleClient scheduleClient;

    @Mock
    private ReminderSettingService reminderSettingService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private ReminderLogMapper reminderLogMapper;

    private NotificationProperties properties;

    private ReminderServiceImpl reminderService;

    @BeforeEach
    void setUp() {
        properties = new NotificationProperties();
        reminderService = new ReminderServiceImpl(
                scheduleClient, reminderSettingService, notificationService, reminderLogMapper, properties);
    }

    @Test
    @DisplayName("总开关关闭时不拉取下游、不产生通知")
    void disabledSkipsEverything() {
        properties.getReminder().setEnabled(false);
        assertEquals(0, reminderService.scanAt(NOW));
        verify(scheduleClient, never()).upcoming(anyInt(), anyInt());
    }

    @Test
    @DisplayName("下游返回空列表时安静结束")
    void emptyCandidatesNoop() {
        mockCandidates(List.of());
        assertEquals(0, reminderService.scanAt(NOW));
        verify(notificationService, never()).create(any());
    }

    @Test
    @DisplayName("下游抛异常时降级为跳过本轮，不把异常抛给调度器")
    void downstreamFailureDegrades() {
        when(scheduleClient.upcoming(anyInt(), anyInt())).thenThrow(new RuntimeException("connect timed out"));
        assertEquals(0, reminderService.scanAt(NOW));
        verify(notificationService, never()).create(any());
    }

    @Test
    @DisplayName("下游返回非成功码时视为失败，不误判成「没有日程」")
    void nonZeroCodeDegrades() {
        when(scheduleClient.upcoming(anyInt(), anyInt())).thenReturn(Result.fail(503, "服务不可用"));
        assertEquals(0, reminderService.scanAt(NOW));
        verify(notificationService, never()).create(any());
    }

    @Test
    @DisplayName("未到提醒时刻不推送")
    void notDueYetSkipped() {
        // 9:30 开始、提前 15 分钟 → 9:15 才该提醒，当前 9:00 还早
        mockCandidates(List.of(candidate(LocalDateTime.of(2026, 9, 16, 9, 30))));
        mockSettings(setting(15, "APP", false, null, null));

        assertEquals(0, reminderService.scanAt(NOW));
        verify(notificationService, never()).create(any());
        verify(reminderLogMapper, never()).tryAcquire(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("到点后生成提醒：类型 REMINDER、关联日程、标题含日程名")
    void dueCandidateCreatesReminder() {
        // 9:10 开始、提前 15 分钟 → 8:55 就该提醒，当前 9:00 已到点（允许补发）
        mockCandidates(List.of(candidate(LocalDateTime.of(2026, 9, 16, 9, 10))));
        mockSettings(setting(15, "APP", false, null, null));
        when(reminderLogMapper.tryAcquire(USER_ID, SCHEDULE_ID, LocalDateTime.of(2026, 9, 16, 8, 55)))
                .thenReturn(1);

        assertEquals(1, reminderService.scanAt(NOW));

        ArgumentCaptor<CreateNotificationCommand> captor =
                ArgumentCaptor.forClass(CreateNotificationCommand.class);
        verify(notificationService).create(captor.capture());
        CreateNotificationCommand command = captor.getValue();
        assertEquals(USER_ID, command.getUserId());
        assertEquals("REMINDER", command.getType());
        assertEquals(SCHEDULE_ID, command.getRelatedId());
        assertTrue(command.getTitle().contains("织一段代码"));
        assertTrue(command.getContent().contains("2026-09-16 09:10"));
        assertTrue(command.getContent().contains("提前 15 分钟"));
    }

    @Test
    @DisplayName("同一日程同一次提醒重复触发时被幂等闸门拦下")
    void duplicateReminderSkipped() {
        mockCandidates(List.of(candidate(LocalDateTime.of(2026, 9, 16, 9, 10))));
        mockSettings(setting(15, "APP", false, null, null));
        when(reminderLogMapper.tryAcquire(anyLong(), anyLong(), any())).thenReturn(0);

        assertEquals(0, reminderService.scanAt(NOW));
        verify(notificationService, never()).create(any());
    }

    @Test
    @DisplayName("免打扰期间不推送，且不占用幂等额度（免打扰结束后仍能补上）")
    void quietHoursDeferReminder() {
        // 免打扰 23:00–07:00，当前 9:00 不在其中；改用 08:00–10:00 覆盖当前时刻
        mockCandidates(List.of(candidate(LocalDateTime.of(2026, 9, 16, 9, 10))));
        mockSettings(setting(15, "APP", true, "08:00", "10:00"));

        assertEquals(0, reminderService.scanAt(NOW));
        verify(notificationService, never()).create(any());
        // 关键：不占额度，下一轮扫描（免打扰结束后）还能发得出去
        verify(reminderLogMapper, never()).tryAcquire(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("跨天免打扰（23:00–07:00）在凌晨静默、早上恢复")
    void crossMidnightQuietHours() {
        ReminderSetting setting = setting(20, "APP", true, "23:00", "07:00");
        mockSettings(setting);
        mockCandidates(List.of(candidate(LocalDateTime.of(2026, 9, 16, 7, 10))));

        // 凌晨 3:00：处于免打扰
        assertEquals(0, reminderService.scanAt(LocalDateTime.of(2026, 9, 16, 3, 0)));
        verify(notificationService, never()).create(any());

        // 早上 7:05：免打扰已结束，提醒补发
        when(reminderLogMapper.tryAcquire(anyLong(), anyLong(), any())).thenReturn(1);
        assertEquals(1, reminderService.scanAt(LocalDateTime.of(2026, 9, 16, 7, 5)));
        verify(notificationService, times(1)).create(any());
    }

    @Test
    @DisplayName("未勾选站内信渠道时不产生通知（邮件/短信通道尚未接入）")
    void channelWithoutAppSkipped() {
        mockCandidates(List.of(candidate(LocalDateTime.of(2026, 9, 16, 9, 10))));
        mockSettings(setting(15, "EMAIL,SMS", false, null, null));

        assertEquals(0, reminderService.scanAt(NOW));
        verify(notificationService, never()).create(any());
    }

    @Test
    @DisplayName("提前量被夹紧到合法区间，脏数据不会算出荒谬的提醒时刻")
    void advanceMinutesClamped() {
        // 提前量 0 → 夹到 1 分钟：9:10 开始 → 9:09 提醒，当前 9:00 未到点
        mockSettings(setting(0, "APP", false, null, null));
        mockCandidates(List.of(candidate(LocalDateTime.of(2026, 9, 16, 9, 10))));
        assertEquals(0, reminderService.scanAt(NOW));

        // 提前量巨大 → 夹到 1440 分钟后仍未到点，不应把「刚建的日程」立刻当成要提醒
        mockSettings(setting(999999, "APP", false, null, null));
        mockCandidates(List.of(candidate(LocalDateTime.of(2026, 9, 16, 12, 0))));
        assertEquals(0, reminderService.scanAt(NOW));
        verify(notificationService, never()).create(any());
    }

    @Test
    @DisplayName("残缺候选（缺用户/日程/开始时间）被跳过，不影响其余候选")
    void incompleteCandidateSkipped() {
        ReminderCandidateDto broken = new ReminderCandidateDto();
        broken.setUserId(USER_ID);
        // scheduleId 与 plannedStartTime 缺失

        mockCandidates(List.of(broken, candidate(LocalDateTime.of(2026, 9, 16, 9, 10))));
        mockSettings(setting(15, "APP", false, null, null));
        when(reminderLogMapper.tryAcquire(anyLong(), anyLong(), any())).thenReturn(1);

        assertEquals(1, reminderService.scanAt(NOW));
    }

    @Test
    @DisplayName("超长日程标题在通知标题里被截断，不会撑爆列宽")
    void longTitleTruncated() {
        ReminderCandidateDto candidate = candidate(LocalDateTime.of(2026, 9, 16, 9, 10));
        candidate.setTitle("长".repeat(200));
        mockCandidates(List.of(candidate));
        mockSettings(setting(15, "APP", false, null, null));
        when(reminderLogMapper.tryAcquire(anyLong(), anyLong(), any())).thenReturn(1);

        reminderService.scanAt(NOW);

        ArgumentCaptor<CreateNotificationCommand> captor =
                ArgumentCaptor.forClass(CreateNotificationCommand.class);
        verify(notificationService).create(captor.capture());
        String title = captor.getValue().getTitle();
        assertEquals("「" + "长".repeat(40) + "」即将开始", title);
    }

    @Test
    @DisplayName("多用户候选按各自提前量独立判断")
    void multipleUsersIndependent() {
        ReminderCandidateDto early = candidate(LocalDateTime.of(2026, 9, 16, 9, 10));
        ReminderCandidateDto late = new ReminderCandidateDto();
        late.setUserId(22L);
        late.setScheduleId(301L);
        late.setTitle("晚点开始");
        late.setPlannedStartTime(LocalDateTime.of(2026, 9, 16, 12, 0));

        mockCandidates(List.of(early, late));
        Map<Long, ReminderSetting> settings = new HashMap<>();
        settings.put(USER_ID, setting(15, "APP", false, null, null));
        ReminderSetting otherSetting = setting(15, "APP", false, null, null);
        otherSetting.setUserId(22L);
        settings.put(22L, otherSetting);
        when(reminderSettingService.findEffectiveAll(ArgumentMatchers.<Collection<Long>>any())).thenReturn(settings);
        when(reminderLogMapper.tryAcquire(anyLong(), anyLong(), any())).thenReturn(1);

        // 只有 9:10 那条到点
        assertEquals(1, reminderService.scanAt(NOW));
        verify(notificationService, times(1)).create(any());
    }

    @Test
    @DisplayName("清理留痕：保留天数非正数时不动手")
    void cleanupSkippedWhenRetentionDisabled() {
        properties.getReminder().setRetentionDays(0);
        assertEquals(0, reminderService.cleanupExpiredLogs());
        verify(reminderLogMapper, never()).deleteBefore(any(), anyInt());
    }

    @Test
    @DisplayName("清理留痕：按保留天数删除并返回条数")
    void cleanupDeletesExpired() {
        properties.getReminder().setRetentionDays(30);
        when(reminderLogMapper.deleteBefore(any(), anyInt())).thenReturn(7);
        assertEquals(7, reminderService.cleanupExpiredLogs());
        verify(reminderLogMapper).deleteBefore(any(), anyInt());
    }

    private void mockCandidates(List<ReminderCandidateDto> candidates) {
        when(scheduleClient.upcoming(anyInt(), anyInt())).thenReturn(Result.success(candidates));
    }

    private void mockSettings(ReminderSetting setting) {
        Map<Long, ReminderSetting> map = new HashMap<>();
        map.put(setting.getUserId(), setting);
        when(reminderSettingService.findEffectiveAll(ArgumentMatchers.<Collection<Long>>any())).thenReturn(map);
    }

    private ReminderCandidateDto candidate(LocalDateTime plannedStart) {
        ReminderCandidateDto candidate = new ReminderCandidateDto();
        candidate.setUserId(USER_ID);
        candidate.setScheduleId(SCHEDULE_ID);
        candidate.setTitle("织一段代码");
        candidate.setPlannedStartTime(plannedStart);
        candidate.setTags(List.of("学习", "Java"));
        return candidate;
    }

    private ReminderSetting setting(int advanceMinutes, String channels, boolean quietEnabled,
                                    String quietStart, String quietEnd) {
        ReminderSetting setting = new ReminderSetting();
        setting.setUserId(USER_ID);
        setting.setDefaultAdvanceMinutes(advanceMinutes);
        setting.setChannels(channels);
        setting.setQuietHoursEnabled(quietEnabled ? 1 : 0);
        setting.setQuietStart(quietStart);
        setting.setQuietEnd(quietEnd);
        return setting;
    }
}
