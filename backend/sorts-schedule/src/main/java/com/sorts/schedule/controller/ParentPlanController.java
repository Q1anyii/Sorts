package com.sorts.schedule.controller;

import com.sorts.common.constant.AuthConstants;
import com.sorts.common.result.PageData;
import com.sorts.common.result.Result;
import com.sorts.schedule.dto.AttachChildrenRequest;
import com.sorts.schedule.dto.ParentPlanSaveRequest;
import com.sorts.schedule.dto.ParentPlanVO;
import com.sorts.schedule.service.ParentPlanService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 主计划接口：长时间计划容器，可挂载多个子计划（现有日程）。
 *
 * @author sorts
 */
@RestController
@RequestMapping("/api/v1/parent-plans")
@RequiredArgsConstructor
public class ParentPlanController {

    private final ParentPlanService parentPlanService;

    @PostMapping
    public Result<ParentPlanVO> create(@RequestHeader(AuthConstants.HEADER_USER_ID) Long userId,
                                       @Valid @RequestBody ParentPlanSaveRequest request) {
        return Result.success(parentPlanService.create(userId, request));
    }

    @GetMapping
    public Result<PageData<ParentPlanVO>> page(@RequestHeader(AuthConstants.HEADER_USER_ID) Long userId,
                                               @RequestParam(defaultValue = "1") int page,
                                               @RequestParam(defaultValue = "20") int pageSize) {
        int size = Math.min(Math.max(pageSize, 1), 100);
        List<ParentPlanVO> list = parentPlanService.page(userId, Math.max(page, 1), size);
        long total = parentPlanService.count(userId);
        return Result.success(new PageData<>(list, total, Math.max(page, 1), size));
    }

    @GetMapping("/{id}")
    public Result<ParentPlanVO> detail(@RequestHeader(AuthConstants.HEADER_USER_ID) Long userId,
                                       @PathVariable Long id) {
        return Result.success(parentPlanService.detail(userId, id));
    }

    @PutMapping("/{id}")
    public Result<ParentPlanVO> update(@RequestHeader(AuthConstants.HEADER_USER_ID) Long userId,
                                       @PathVariable Long id,
                                       @Valid @RequestBody ParentPlanSaveRequest request) {
        return Result.success(parentPlanService.update(userId, id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@RequestHeader(AuthConstants.HEADER_USER_ID) Long userId,
                               @PathVariable Long id) {
        parentPlanService.delete(userId, id);
        return Result.success();
    }

    /** 状态流转：start / pause / resume / complete */
    @PostMapping("/{id}/{action}")
    public Result<ParentPlanVO> changeStatus(@RequestHeader(AuthConstants.HEADER_USER_ID) Long userId,
                                             @PathVariable Long id,
                                             @PathVariable String action) {
        return Result.success(parentPlanService.changeStatus(userId, id, action));
    }

    /** 批量挂载子计划（现有日程） */
    @PostMapping("/{id}/children")
    public Result<ParentPlanVO> attachChildren(@RequestHeader(AuthConstants.HEADER_USER_ID) Long userId,
                                               @PathVariable Long id,
                                               @Valid @RequestBody AttachChildrenRequest request) {
        return Result.success(parentPlanService.attachChildren(userId, id, request.getScheduleIds()));
    }

    /** 移出单个子计划 */
    @DeleteMapping("/{id}/children/{scheduleId}")
    public Result<ParentPlanVO> detachChild(@RequestHeader(AuthConstants.HEADER_USER_ID) Long userId,
                                            @PathVariable Long id,
                                            @PathVariable Long scheduleId) {
        return Result.success(parentPlanService.detachChild(userId, id, scheduleId));
    }
}
