package com.sorts.ai.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.sorts.ai.client.ScheduleClient;
import com.sorts.ai.client.dto.BatchCreateDto;
import com.sorts.ai.client.dto.ScheduleDto;
import com.sorts.ai.client.dto.ScheduleSaveDto;
import com.sorts.ai.tool.ToolLevel;
import com.sorts.ai.tool.support.ToolJsonCodec;
import com.sorts.ai.tool.support.JsonArgs;
import com.sorts.ai.tool.support.Results;
import com.sorts.ai.tool.support.Schemas;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 批量创建日程（写工具）。
 *
 * <p>与单条创建分开，是因为配额与语义不同：批量是「一次意图、多条数据」，
 * 下游有上限保护（sorts-schedule 会校验），这里也先做一次条数收敛。</p>
 *
 * @author sorts
 */
@Component
public class CreateSchedulesTool extends AbstractScheduleWriteTool {

    /** 与下游 batch 接口的容量上限保持一致 */
    private static final int MAX_BATCH = 20;

    private final ScheduleClient scheduleClient;

    public CreateSchedulesTool(ToolJsonCodec jsonCodec, ScheduleClient scheduleClient) {
        super(jsonCodec);
        this.scheduleClient = scheduleClient;
    }

    @Override
    public String name() {
        return "createSchedules";
    }

    @Override
    public String description() {
        return "为当前用户批量创建多条日程，适合「帮我把今天的计划都排上」这类一次性多条的需求。"
                + "单次最多 20 条。每条都需要独立的标题、开始时间与时长。此操作会真实写入用户数据。";
    }

    @Override
    public ToolLevel level() {
        return ToolLevel.WRITE;
    }

    @Override
    public Map<String, Object> parameters() {
        return Schemas.object(Schemas.props(
                "schedules", Schemas.array(scheduleItemSchema(), "要创建的日程数组，至少 1 条，最多 20 条")
        ), "schedules");
    }

    @Override
    public String execute(Long userId, JsonNode args) {
        JsonNode array = JsonArgs.array(args, "schedules");
        if (array == null || array.isEmpty()) {
            throw new IllegalArgumentException("schedules 必须是非空数组");
        }
        if (array.size() > MAX_BATCH) {
            throw new IllegalArgumentException("单次最多创建 " + MAX_BATCH + " 条日程，当前 " + array.size() + " 条");
        }
        List<ScheduleSaveDto> requests = new ArrayList<>();
        for (JsonNode item : array) {
            requests.add(toSaveDto(item));
        }

        List<ScheduleDto> created = Results.unwrap("批量创建日程",
                scheduleClient.batchCreate(userId, new BatchCreateDto(requests)));

        List<Map<String, Object>> items = new ArrayList<>();
        List<Long> ids = new ArrayList<>();
        if (created != null) {
            for (ScheduleDto schedule : created) {
                ids.add(schedule.getId());
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", schedule.getId());
                item.put("title", schedule.getTitle());
                item.put("plannedStartTime", schedule.getPlannedStartTime());
                item.put("plannedDuration", schedule.getPlannedDuration());
                items.add(item);
            }
        }
        Map<String, Object> data = result();
        data.put("success", true);
        data.put("createdCount", items.size());
        data.put("createdIds", ids);
        data.put("schedules", items);
        return json(data);
    }
}
