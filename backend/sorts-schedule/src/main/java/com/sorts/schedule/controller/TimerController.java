package com.sorts.schedule.controller;

import com.sorts.common.constant.AuthConstants;
import com.sorts.common.result.Result;
import com.sorts.schedule.dto.ScheduleVO;
import com.sorts.schedule.service.TimerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 计时状态机接口：穿梭（start）→ 停梭（pause）→ 续梭（resume）→ 落梭（end）。
 *
 * @author sorts
 */
@RestController
@RequestMapping("/api/v1/schedules")
@RequiredArgsConstructor
public class TimerController {

    private final TimerService timerService;

    @PostMapping("/{id}/start")
    public Result<ScheduleVO> start(@RequestHeader(AuthConstants.HEADER_USER_ID) Long userId,
                                    @PathVariable("id") Long id) {
        return Result.success(timerService.start(userId, id));
    }

    @PostMapping("/{id}/pause")
    public Result<ScheduleVO> pause(@RequestHeader(AuthConstants.HEADER_USER_ID) Long userId,
                                    @PathVariable("id") Long id) {
        return Result.success(timerService.pause(userId, id));
    }

    @PostMapping("/{id}/resume")
    public Result<ScheduleVO> resume(@RequestHeader(AuthConstants.HEADER_USER_ID) Long userId,
                                     @PathVariable("id") Long id) {
        return Result.success(timerService.resume(userId, id));
    }

    /** 落梭：结算时长并发放光阴砂 */
    @PostMapping("/{id}/end")
    public Result<ScheduleVO> end(@RequestHeader(AuthConstants.HEADER_USER_ID) Long userId,
                                  @PathVariable("id") Long id) {
        return Result.success(timerService.end(userId, id));
    }

    @PostMapping("/{id}/cancel")
    public Result<ScheduleVO> cancel(@RequestHeader(AuthConstants.HEADER_USER_ID) Long userId,
                                     @PathVariable("id") Long id) {
        return Result.success(timerService.cancel(userId, id));
    }
}
