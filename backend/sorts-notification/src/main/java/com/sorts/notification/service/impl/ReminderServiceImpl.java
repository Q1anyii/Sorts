package com.sorts.notification.service.impl;

import com.sorts.common.result.Result;
import com.sorts.notification.client.ScheduleClient;
import com.sorts.notification.client.dto.ReminderCandidateDto;
import com.sorts.notification.config.NotificationProperties;
import com.sorts.notification.dto.CreateNotificationCommand;
import com.sorts.notification.entity.ReminderSetting;
import com.sorts.notification.enums.NotificationType;
import com.sorts.notification.enums.ReminderChannel;
import com.sorts.notification.mapper.ReminderLogMapper;
import com.sorts.notification.service.NotificationService;
import com.sorts.notification.service.ReminderService;
import com.sorts.notification.service.ReminderSettingService;
import com.sorts.notification.support.QuietHours;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 定时提醒服务实现。
 *
 * <p>核心决策：</p>
 * <ol>
 *   <li><b>先占额度再发通知</b>（at-most-once）。提醒是「过期即无价值」的信息，
 *       宁可漏一次也不要重复轰炸；反过来做就会在异常重试时连发多条。</li>
 *   <li><b>依赖不可用即跳过本轮</b>。日程服务挂了不应该让调度线程抛异常，
 *       更不应该把「取不到日程」误判成「没有日程需要提醒」而留下错误日志。</li>
 *   <li><b>免打扰不占额度</b>。这样免打扰结束后若日程仍在窗口内，提醒会自动补上
 *       ——用户要的是「那个时间段别响」，不是「那条提醒作废」。</li>
 * </ol>
 *
 * @author sorts
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReminderServiceImpl implements ReminderService {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** 提前量上下限：下限 1 分钟，上限 24 小时 */
    private static final int MIN_ADVANCE_MINUTES = 1;

    private static final int MAX_ADVANCE_MINUTES = 1440;

    /** 通知标题里保留的日程标题长度，避免把标题撑爆 */
    private static final int TITLE_KEEP = 40;

    /** 单次清理的批量上限，避免一次删除锁表过久 */
    private static final int CLEANUP_BATCH = 1000;

    private final ScheduleClient scheduleClient;

    private final ReminderSettingService reminderSettingService;

    private final NotificationService notificationService;

    private final ReminderLogMapper reminderLogMapper;

    private final NotificationProperties properties;

    @Override
    public int scanOnce() {
        return scanAt(LocalDateTime.now());
    }

    /** 以指定时刻为「当前时间」执行一次扫描（便于单测驱动） */
    int scanAt(LocalDateTime now) {
        NotificationProperties.Reminder config = properties.getReminder();
        if (!config.isEnabled()) {
            return 0;
        }
        LocalDateTime current = now.withSecond(0).withNano(0);

        List<ReminderCandidateDto> candidates = fetchCandidates(config);
        if (candidates == null || candidates.isEmpty()) {
            return 0;
        }

        Map<Long, ReminderSetting> settings = reminderSettingService.findEffectiveAll(
                candidates.stream()
                        .map(ReminderCandidateDto::getUserId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toCollection(LinkedHashSet::new)));
        if (settings.isEmpty()) {
            return 0;
        }

        int created = 0;
        int notDue = 0;
        int quiet = 0;
        int noChannel = 0;
        int duplicate = 0;

        for (ReminderCandidateDto candidate : candidates) {
            if (candidate.getUserId() == null || candidate.getScheduleId() == null
                    || candidate.getPlannedStartTime() == null) {
                // 上游理论上不会给残缺数据，真给了也只想跳过这一条而不是整轮失败
                continue;
            }
            ReminderSetting setting = settings.get(candidate.getUserId());
            if (setting == null) {
                continue;
            }

            int advance = clampAdvance(setting.getDefaultAdvanceMinutes());
            LocalDateTime remindAt = candidate.getPlannedStartTime()
                    .minusMinutes(advance).withSecond(0).withNano(0);
            if (remindAt.isAfter(current)) {
                notDue++;
                continue;
            }
            if (isQuietNow(setting, current)) {
                quiet++;
                continue;
            }
            if (!hasAppChannel(setting)) {
                noChannel++;
                continue;
            }
            // 幂等闸门：唯一键由数据库保证，重复触发只会有一轮能抢到
            if (reminderLogMapper.tryAcquire(candidate.getUserId(), candidate.getScheduleId(), remindAt) == 0) {
                duplicate++;
                continue;
            }
            notificationService.create(buildCommand(candidate, advance));
            created++;
        }

        log.info("提醒扫描完成：候选 {} 条，新建 {} 条（未到点 {}，免打扰 {}，无站内信渠道 {}，重复 {}）",
                candidates.size(), created, notDue, quiet, noChannel, duplicate);
        return created;
    }

    @Override
    public int cleanupExpiredLogs() {
        int retentionDays = properties.getReminder().getRetentionDays();
        if (retentionDays <= 0) {
            return 0;
        }
        int deleted = reminderLogMapper.deleteBefore(LocalDateTime.now().minusDays(retentionDays), CLEANUP_BATCH);
        if (deleted > 0) {
            log.info("清理过期提醒留痕 {} 条（保留 {} 天）", deleted, retentionDays);
        }
        return deleted;
    }

    /**
     * 拉取候选日程。
     *
     * <p>捕获所有异常：调度任务里的异常会静默终止后续调度，
     * 而「提醒服务因为日程服务抖了一下就再也不再提醒」是不可接受的。</p>
     */
    private List<ReminderCandidateDto> fetchCandidates(NotificationProperties.Reminder config) {
        try {
            Result<List<ReminderCandidateDto>> result =
                    scheduleClient.upcoming(config.getLookaheadMinutes(), config.getBatchLimit());
            if (result == null || result.getCode() != 0) {
                log.warn("拉取即将开始的日程返回非成功码：code={}, message={}",
                        result == null ? null : result.getCode(),
                        result == null ? null : result.getMessage());
                return List.of();
            }
            return result.getData();
        } catch (Exception e) {
            log.warn("拉取即将开始的日程失败，本轮提醒跳过：{}", e.getMessage());
            return List.of();
        }
    }

    private boolean isQuietNow(ReminderSetting setting, LocalDateTime now) {
        if (!Integer.valueOf(1).equals(setting.getQuietHoursEnabled())) {
            return false;
        }
        return QuietHours.covers(setting.getQuietStart(), setting.getQuietEnd(), now.toLocalTime());
    }

    /** 未勾选站内信则不产生通知（EMAIL/SMS 通道尚未接入，勾了也不会发） */
    private boolean hasAppChannel(ReminderSetting setting) {
        return ReminderChannel.split(setting.getChannels()).contains(ReminderChannel.APP);
    }

    private int clampAdvance(Integer advance) {
        if (advance == null) {
            return properties.getDefaultAdvanceMinutes();
        }
        return Math.max(MIN_ADVANCE_MINUTES, Math.min(advance, MAX_ADVANCE_MINUTES));
    }

    private CreateNotificationCommand buildCommand(ReminderCandidateDto candidate, int advance) {
        String scheduleTitle = StringUtils.hasText(candidate.getTitle()) ? candidate.getTitle().trim() : "未命名日程";
        String brief = scheduleTitle.length() <= TITLE_KEEP ? scheduleTitle : scheduleTitle.substring(0, TITLE_KEEP);

        StringBuilder content = new StringBuilder()
                .append("计划开始时间 ")
                .append(candidate.getPlannedStartTime().format(DATE_TIME))
                .append("，已提前 ").append(advance).append(" 分钟提醒。");
        if (candidate.getTags() != null && !candidate.getTags().isEmpty()) {
            Set<String> tags = candidate.getTags().stream()
                    .filter(StringUtils::hasText)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            if (!tags.isEmpty()) {
                content.append(" 标签：").append(String.join("/", tags));
            }
        }

        CreateNotificationCommand command = new CreateNotificationCommand();
        command.setUserId(candidate.getUserId());
        command.setType(NotificationType.REMINDER.name());
        command.setTitle("「" + brief + "」即将开始");
        command.setContent(content.toString());
        command.setRelatedId(candidate.getScheduleId());
        return command;
    }
}
