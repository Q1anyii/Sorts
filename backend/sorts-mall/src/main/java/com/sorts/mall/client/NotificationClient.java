package com.sorts.mall.client;

import com.sorts.common.result.Result;
import com.sorts.mall.client.dto.CreateNotificationCommand;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 调用通知服务（跨模块走 OpenFeign）。
 *
 * @author sorts
 */
@FeignClient(name = "sorts-notification", contextId = "mallNotificationClient")
public interface NotificationClient {

    /** 投递一条通知；路径以 /internal 开头，凭证由拦截器自动附加 */
    @PostMapping("/internal/notifications")
    Result<Long> create(@RequestBody CreateNotificationCommand command);
}
