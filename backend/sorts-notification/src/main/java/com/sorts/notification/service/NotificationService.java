package com.sorts.notification.service;

import com.sorts.notification.dto.CreateNotificationCommand;
import com.sorts.notification.dto.NotificationPageVO;
import com.sorts.notification.dto.NotificationQuery;

import java.util.List;

/**
 * 通知服务。
 *
 * @author sorts
 */
public interface NotificationService {

    /** 分页查询通知（附未读总数） */
    NotificationPageVO list(Long userId, NotificationQuery query);

    /** 标记单条已读（他人通知按不存在处理；重复标记幂等） */
    void markRead(Long userId, Long notificationId);

    /** 全部标记已读，返回本次变更条数 */
    int markAllRead(Long userId);

    /** 未读总数 */
    long unreadCount(Long userId);

    /** 创建单条通知（供内部服务与提醒任务调用），返回通知 ID */
    Long create(CreateNotificationCommand command);

    /** 批量创建，返回成功条数 */
    int createBatch(List<CreateNotificationCommand> commands);
}
