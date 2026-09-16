package com.sorts.notification.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sorts.common.exception.BizException;
import com.sorts.common.result.ErrorCode;
import com.sorts.notification.dto.CreateNotificationCommand;
import com.sorts.notification.dto.NotificationPageVO;
import com.sorts.notification.dto.NotificationQuery;
import com.sorts.notification.dto.NotificationVO;
import com.sorts.notification.entity.Notification;
import com.sorts.notification.enums.NotificationType;
import com.sorts.notification.mapper.NotificationMapper;
import com.sorts.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * 通知服务实现。
 *
 * @author sorts
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    /** 单页上限，与 mybatis-plus maxLimit 双保险 */
    private static final int MAX_PAGE_SIZE = 200;

    private static final int DEFAULT_PAGE_SIZE = 20;

    private static final int UNREAD = 0;

    /** 批量创建上限：内部调用方应分批发，一次塞一万条会把库压垮 */
    private static final int MAX_BATCH_SIZE = 200;

    /** 标题、正文长度与库表列宽保持一致，超长直接截断而不是让 DB 报错 */
    private static final int MAX_TITLE_LENGTH = 128;

    private static final int MAX_CONTENT_LENGTH = 512;

    private final NotificationMapper notificationMapper;

    @Override
    public NotificationPageVO list(Long userId, NotificationQuery query) {
        NotificationQuery condition = query == null ? new NotificationQuery() : query;
        long pageNo = Math.max(1, condition.getPage());
        long size = condition.getPageSize() <= 0 ? DEFAULT_PAGE_SIZE
                : Math.min(condition.getPageSize(), MAX_PAGE_SIZE);

        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .orderByDesc(Notification::getCreatedAt)
                // 同一秒内创建的多条通知需要一个稳定的次级排序，否则翻页会出现重复/漏项
                .orderByDesc(Notification::getId);

        if (StringUtils.hasText(condition.getType())) {
            wrapper.eq(Notification::getType, parseType(condition.getType()).name());
        }
        if (condition.getIsRead() != null) {
            wrapper.eq(Notification::getIsRead, condition.getIsRead() ? 1 : UNREAD);
        }

        Page<Notification> page = notificationMapper.selectPage(new Page<>(pageNo, size), wrapper);
        List<NotificationVO> records = page.getRecords().stream().map(NotificationVO::from).toList();

        // 未读角标始终是「全部未读」，与当前筛选条件无关，否则筛「已读」时角标会变 0
        return new NotificationPageVO(records, page.getTotal(), pageNo, size,
                notificationMapper.countUnread(userId));
    }

    @Override
    public void markRead(Long userId, Long notificationId) {
        if (notificationId == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "通知 ID 不能为空");
        }
        if (notificationMapper.countOwned(notificationId, userId) == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "通知不存在");
        }
        // 重复标记返回 0 行属正常情况（本来就已读），直接幂等成功
        notificationMapper.markRead(notificationId, userId);
    }

    @Override
    public int markAllRead(Long userId) {
        return notificationMapper.markAllRead(userId);
    }

    @Override
    public long unreadCount(Long userId) {
        return notificationMapper.countUnread(userId);
    }

    @Override
    public Long create(CreateNotificationCommand command) {
        if (command == null || !StringUtils.hasText(command.getTitle())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "通知标题不能为空");
        }
        if (command.getUserId() == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "通知接收人不能为空");
        }

        Notification notification = new Notification();
        notification.setUserId(command.getUserId());
        notification.setType(NotificationType.from(command.getType()).name());
        notification.setTitle(truncate(command.getTitle(), MAX_TITLE_LENGTH));
        notification.setContent(truncate(command.getContent(), MAX_CONTENT_LENGTH));
        notification.setIsRead(UNREAD);
        notification.setRelatedId(command.getRelatedId());
        notification.setDeleted(0);
        notification.setCreatedAt(LocalDateTime.now());
        notificationMapper.insert(notification);
        return notification.getId();
    }

    @Override
    public int createBatch(List<CreateNotificationCommand> commands) {
        if (commands == null || commands.isEmpty()) {
            return 0;
        }
        if (commands.size() > MAX_BATCH_SIZE) {
            throw new BizException(ErrorCode.PARAM_ERROR, "单次批量创建通知不能超过 " + MAX_BATCH_SIZE + " 条");
        }
        int created = 0;
        // 逐条插入：批量插入拿不到自增主键，而提醒任务需要 notificationId 做后续关联
        for (CreateNotificationCommand command : commands) {
            try {
                create(command);
                created++;
            } catch (BizException e) {
                // 单条非法不应让整批失败：通知是「尽力而为」的旁路能力
                log.warn("批量创建通知跳过非法项 userId={}, title={}, reason={}",
                        command == null ? null : command.getUserId(),
                        command == null ? null : command.getTitle(),
                        e.getMessage());
            }
        }
        return created;
    }

    /** 类型非法直接 400：静默忽略筛选条件会返回「全部通知」，让调用方以为筛选生效了 */
    private NotificationType parseType(String type) {
        return Arrays.stream(NotificationType.values())
                .filter(candidate -> candidate.name().equalsIgnoreCase(type.trim()))
                .findFirst()
                .orElseThrow(() -> new BizException(ErrorCode.PARAM_ERROR, "不支持的通知类型：" + type));
    }

    private String truncate(String text, int maxLength) {
        if (text == null) {
            return null;
        }
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }
}
