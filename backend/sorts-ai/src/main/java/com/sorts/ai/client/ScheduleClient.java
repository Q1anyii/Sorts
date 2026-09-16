package com.sorts.ai.client;

import com.sorts.ai.client.dto.BatchCreateDto;
import com.sorts.ai.client.dto.ScheduleDto;
import com.sorts.ai.client.dto.ScheduleQueryDto;
import com.sorts.ai.client.dto.ScheduleSaveDto;
import com.sorts.ai.client.dto.StatisticsSummaryDto;
import com.sorts.ai.client.dto.TagStatDto;
import com.sorts.ai.client.dto.TrendPointDto;
import com.sorts.common.constant.AuthConstants;
import com.sorts.common.result.PageData;
import com.sorts.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;

/**
 * 日程服务（sorts-schedule）内部调用客户端。
 *
 * <p>两点约定：</p>
 * <ul>
 *   <li>统一携带 {@code X-User-Id}——下游服务的属主校验全靠它，AI 服务只以「当前登录用户」的身份读写；</li>
 *   <li>返回值统一是 {@code Result&lt;T&gt;}：下游的业务错误走 HTTP 200 + code≠0，
 *       所以调用方必须检查 code，不能只看 HTTP 状态。</li>
 * </ul>
 *
 * @author sorts
 */
@FeignClient(name = "sorts-schedule", contextId = "aiScheduleClient", path = "/api/v1")
public interface ScheduleClient {

    @PostMapping("/schedules")
    Result<ScheduleDto> create(@RequestHeader(AuthConstants.HEADER_USER_ID) Long userId,
                               @RequestBody ScheduleSaveDto request);

    @GetMapping("/schedules")
    Result<PageData<ScheduleDto>> list(@RequestHeader(AuthConstants.HEADER_USER_ID) Long userId,
                                      @SpringQueryMap ScheduleQueryDto query);

    @PostMapping("/schedules/batch")
    Result<List<ScheduleDto>> batchCreate(@RequestHeader(AuthConstants.HEADER_USER_ID) Long userId,
                                          @RequestBody BatchCreateDto request);

    @GetMapping("/statistics/summary")
    Result<StatisticsSummaryDto> summary(@RequestHeader(AuthConstants.HEADER_USER_ID) Long userId,
                                         @RequestParam("period") String period,
                                         @RequestParam(value = "date", required = false) LocalDate date);

    @GetMapping("/statistics/tags")
    Result<List<TagStatDto>> tags(@RequestHeader(AuthConstants.HEADER_USER_ID) Long userId,
                                  @RequestParam("period") String period);

    @GetMapping("/statistics/trend")
    Result<List<TrendPointDto>> trend(@RequestHeader(AuthConstants.HEADER_USER_ID) Long userId,
                                      @RequestParam("days") Integer days);
}
