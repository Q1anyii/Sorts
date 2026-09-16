package com.sorts.schedule.controller;

import com.sorts.common.constant.AuthConstants;
import com.sorts.common.result.Result;
import com.sorts.schedule.dto.StatisticsSummaryVO;
import com.sorts.schedule.dto.TagStatVO;
import com.sorts.schedule.dto.TrendPointVO;
import com.sorts.schedule.service.StatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 统计接口（汇总 / 趋势 / 标签分布）。
 *
 * @author sorts
 */
@RestController
@RequestMapping("/api/v1/statistics")
@RequiredArgsConstructor
public class StatisticsController {

    private final StatisticsService statisticsService;

    /** 统计汇总，period 缺省 week，date 缺省今天 */
    @GetMapping("/summary")
    public Result<StatisticsSummaryVO> summary(@RequestHeader(AuthConstants.HEADER_USER_ID) Long userId,
                                               @RequestParam(value = "period", required = false) String period,
                                               @RequestParam(value = "date", required = false)
                                               @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.success(statisticsService.summary(userId, period, date));
    }

    /** 最近 N 天趋势，days 缺省 30 */
    @GetMapping("/trend")
    public Result<List<TrendPointVO>> trend(@RequestHeader(AuthConstants.HEADER_USER_ID) Long userId,
                                            @RequestParam(value = "days", required = false) Integer days) {
        return Result.success(statisticsService.trend(userId, days));
    }

    /** 标签分布，period 缺省 month（支持 all） */
    @GetMapping("/tags")
    public Result<List<TagStatVO>> tags(@RequestHeader(AuthConstants.HEADER_USER_ID) Long userId,
                                        @RequestParam(value = "period", required = false) String period) {
        return Result.success(statisticsService.tags(userId, period));
    }
}
