package com.sorts.notification.client;

import com.sorts.common.result.Result;
import com.sorts.notification.client.dto.ReminderCandidateDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 调用日程服务（跨模块走 OpenFeign，禁止跨库直连）。
 *
 * <p>路径以 {@code /internal} 开头：既不会经过网关，又会被
 * {@code InternalFeignInterceptor} 自动附加服务间凭证。</p>
 *
 * @author sorts
 */
@FeignClient(name = "sorts-schedule", contextId = "notificationScheduleClient")
public interface ScheduleClient {

    /** 跨用户查询窗口内即将开始的待开始日程 */
    @GetMapping("/internal/schedules/upcoming")
    Result<List<ReminderCandidateDto>> upcoming(@RequestParam("withinMinutes") int withinMinutes,
                                                @RequestParam("limit") int limit);
}
