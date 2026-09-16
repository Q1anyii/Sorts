package com.sorts.schedule.controller;

import com.sorts.common.internal.InternalApi;
import com.sorts.common.result.Result;
import com.sorts.schedule.dto.ReminderCandidateVO;
import com.sorts.schedule.service.ScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 日程服务内部接口（仅供服务间调用）。
 *
 * <p>路径刻意放在 {@code /internal} 下：网关只路由 {@code /api/v1/**}，
 * 因此这些接口在外部网络<b>根本不存在</b>；再叠加 {@link InternalApi} 的凭证校验，
 * 构成「不可达 + 需凭证」两道防线。</p>
 *
 * @author sorts
 */
@InternalApi("日程服务内部接口：不对外暴露")
@RestController
@RequestMapping("/internal/schedules")
@RequiredArgsConstructor
public class InternalScheduleController {

    /** 默认扫描窗口（分钟） */
    private static final int DEFAULT_WITHIN_MINUTES = 60;

    private final ScheduleService scheduleService;

    /**
     * 查询窗口内即将开始的待开始日程（跨用户）。
     *
     * <p>通知服务按「当前时间 → 当前时间 + withinMinutes」拉取一次，
     * 再按各用户自己的提前量决定哪些该发提醒。</p>
     */
    @GetMapping("/upcoming")
    public Result<List<ReminderCandidateVO>> upcoming(
            @RequestParam(value = "withinMinutes", defaultValue = "" + DEFAULT_WITHIN_MINUTES) int withinMinutes,
            @RequestParam(value = "limit", defaultValue = "200") int limit) {

        int minutes = Math.max(1, Math.min(withinMinutes, 24 * 60));
        LocalDateTime from = LocalDateTime.now();
        return Result.success(scheduleService.listReminderCandidates(from, from.plusMinutes(minutes), limit));
    }
}
