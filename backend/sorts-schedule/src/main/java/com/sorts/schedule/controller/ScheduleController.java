package com.sorts.schedule.controller;

import com.sorts.common.constant.AuthConstants;
import com.sorts.common.result.PageData;
import com.sorts.common.result.Result;
import com.sorts.schedule.dto.BatchCreateRequest;
import com.sorts.schedule.dto.BatchDeleteRequest;
import com.sorts.schedule.dto.ScheduleQuery;
import com.sorts.schedule.dto.ScheduleSaveRequest;
import com.sorts.schedule.dto.ScheduleVO;
import com.sorts.schedule.service.ScheduleService;
import com.sorts.schedule.service.TimerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 日程接口（api-spec.json 的 日程接口 分组）。
 *
 * <p>用户身份来自网关透传的 X-User-Id；服务内所有读写都会做属主校验。</p>
 *
 * @author sorts
 */
@RestController
@RequestMapping("/api/v1/schedules")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;

    private final TimerService timerService;

    @PostMapping
    public Result<ScheduleVO> create(@RequestHeader(AuthConstants.HEADER_USER_ID) Long userId,
                                     @Valid @RequestBody ScheduleSaveRequest request) {
        return Result.success(scheduleService.create(userId, request));
    }

    @GetMapping
    public Result<PageData<ScheduleVO>> list(@RequestHeader(AuthConstants.HEADER_USER_ID) Long userId,
                                             ScheduleQuery query) {
        return Result.success(scheduleService.list(userId, query));
    }

    /**
     * 当前穿梭中的日程。
     *
     * <p>注意与 {@code /{id}} 的路径优先级：字面量路径由 Spring 优先匹配，不会被当成 id。</p>
     */
    @GetMapping("/active")
    public Result<ScheduleVO> active(@RequestHeader(AuthConstants.HEADER_USER_ID) Long userId) {
        return Result.success(timerService.getActive(userId));
    }

    /** 批量创建：AI 规划采纳 / 导入场景 */
    @PostMapping("/batch")
    public Result<List<ScheduleVO>> batchCreate(@RequestHeader(AuthConstants.HEADER_USER_ID) Long userId,
                                                @Valid @RequestBody BatchCreateRequest request) {
        return Result.success(scheduleService.batchCreate(userId, request.getSchedules()));
    }

    @GetMapping("/{id}")
    public Result<ScheduleVO> get(@RequestHeader(AuthConstants.HEADER_USER_ID) Long userId,
                                  @PathVariable("id") Long id) {
        return Result.success(scheduleService.get(userId, id));
    }

    @PutMapping("/{id}")
    public Result<ScheduleVO> update(@RequestHeader(AuthConstants.HEADER_USER_ID) Long userId,
                                     @PathVariable("id") Long id,
                                     @Valid @RequestBody ScheduleSaveRequest request) {
        return Result.success(scheduleService.update(userId, id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@RequestHeader(AuthConstants.HEADER_USER_ID) Long userId,
                               @PathVariable("id") Long id) {
        scheduleService.delete(userId, id);
        return Result.success();
    }

    /**
     * 批量删除（织程多选）：事务内全部成功或全部失败。
     *
     * <p>用 POST + body 避免 DELETE 带 body 的兼容问题，与 {@code POST /batch} 风格一致。</p>
     */
    @PostMapping("/batch-delete")
    public Result<Void> batchDelete(@RequestHeader(AuthConstants.HEADER_USER_ID) Long userId,
                                    @Valid @RequestBody BatchDeleteRequest request) {
        scheduleService.deleteBatch(userId, request.getIds());
        return Result.success();
    }
}
