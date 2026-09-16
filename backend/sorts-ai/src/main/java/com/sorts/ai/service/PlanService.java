package com.sorts.ai.service;

import com.sorts.ai.client.dto.ScheduleDto;
import com.sorts.ai.dto.AIPlanRequest;
import com.sorts.ai.dto.AIPlanResponse;
import com.sorts.ai.dto.AdoptPlanRequest;

import java.util.List;
import java.util.function.Consumer;

/**
 * AI 日程规划：生成建议 → 用户筛选 → 采纳为真实日程。
 *
 * @author sorts
 */
public interface PlanService {

    /**
     * 生成规划建议。
     *
     * @param onDelta 生成过程中的文本增量回调（用于 SSE 进度展示），可为 null
     */
    AIPlanResponse generate(Long userId, AIPlanRequest request, boolean stream, Consumer<String> onDelta);

    /**
     * 采纳规划，把选中的建议创建为日程。
     *
     * @return 实际创建的日程
     */
    List<ScheduleDto> adopt(Long userId, AdoptPlanRequest request);
}
