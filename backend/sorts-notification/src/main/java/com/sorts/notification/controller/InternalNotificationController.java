package com.sorts.notification.controller;

import com.sorts.common.constant.AuthConstants;
import com.sorts.common.internal.InternalApi;
import com.sorts.common.result.Result;
import com.sorts.notification.dto.CreateNotificationCommand;
import com.sorts.notification.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 通知服务内部接口（供商城、日程、AI 等服务投递通知）。
 *
 * <p>路径在 {@code /internal} 下：网关不路由该前缀，外部网络不可达；
 * 再叠加 {@link InternalApi} 的服务间凭证校验。</p>
 *
 * @author sorts
 */
@InternalApi("通知服务内部接口：不对外暴露")
@RestController
@RequestMapping("/internal/notifications")
@RequiredArgsConstructor
public class InternalNotificationController {

    private final NotificationService notificationService;

    /** 创建单条通知；未显式指定接收人时用调用方透传的用户身份 */
    @PostMapping
    public Result<Long> create(@RequestHeader(value = AuthConstants.HEADER_USER_ID, required = false) Long userId,
                               @Valid @RequestBody CreateNotificationCommand command) {
        if (command.getUserId() == null) {
            command.setUserId(userId);
        }
        return Result.success(notificationService.create(command));
    }

    /** 批量创建通知 */
    @PostMapping("/batch")
    public Result<Integer> createBatch(@RequestBody List<CreateNotificationCommand> commands) {
        return Result.success(notificationService.createBatch(commands));
    }
}
