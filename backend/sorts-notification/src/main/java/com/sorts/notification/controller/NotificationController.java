package com.sorts.notification.controller;

import com.sorts.common.constant.AuthConstants;
import com.sorts.common.result.Result;
import com.sorts.notification.dto.NotificationPageVO;
import com.sorts.notification.dto.NotificationQuery;
import com.sorts.notification.dto.ReminderSettingRequest;
import com.sorts.notification.dto.ReminderSettingVO;
import com.sorts.notification.service.NotificationService;
import com.sorts.notification.service.ReminderSettingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 通知接口（api-spec.json 的 提醒通知 分组）。
 *
 * <p>用户身份来自网关透传的 X-User-Id；所有操作都带属主条件，做不到越权改他人数据。</p>
 *
 * @author sorts
 */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    private final ReminderSettingService reminderSettingService;

    /** 通知列表（含未读总数） */
    @GetMapping
    public Result<NotificationPageVO> list(@RequestHeader(AuthConstants.HEADER_USER_ID) Long userId,
                                           NotificationQuery query) {
        return Result.success(notificationService.list(userId, query));
    }

    /**
     * 标记单条已读。
     *
     * <p>注意与 {@code /read-all} 的路径优先级：Spring 优先匹配字面量路径，
     * 但 {@code /read-all} 是 PUT 且段数不同，不会与本接口的 /{id}/read 冲突。</p>
     */
    @PutMapping("/{id}/read")
    public Result<Void> markRead(@RequestHeader(AuthConstants.HEADER_USER_ID) Long userId,
                                 @PathVariable("id") Long id) {
        notificationService.markRead(userId, id);
        return Result.success();
    }

    /** 全部标记已读 */
    @PutMapping("/read-all")
    public Result<Integer> markAllRead(@RequestHeader(AuthConstants.HEADER_USER_ID) Long userId) {
        return Result.success(notificationService.markAllRead(userId));
    }

    /** 获取提醒设置（未自定义时返回服务端默认值，customized=false） */
    @GetMapping("/settings")
    public Result<ReminderSettingVO> getSettings(@RequestHeader(AuthConstants.HEADER_USER_ID) Long userId) {
        return Result.success(reminderSettingService.get(userId));
    }

    /** 更新提醒设置（仅覆盖传入的非空字段） */
    @PutMapping("/settings")
    public Result<ReminderSettingVO> updateSettings(@RequestHeader(AuthConstants.HEADER_USER_ID) Long userId,
                                                    @Valid @RequestBody ReminderSettingRequest request) {
        return Result.success(reminderSettingService.update(userId, request));
    }
}
