package com.sorts.ai.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.sorts.ai.client.ScheduleClient;
import com.sorts.ai.client.dto.ScheduleDto;
import com.sorts.ai.client.dto.ScheduleSaveDto;
import com.sorts.ai.tool.ToolLevel;
import com.sorts.ai.tool.support.JsonArgs;
import com.sorts.ai.tool.support.Results;
import com.sorts.ai.tool.support.Schemas;
import com.sorts.ai.tool.support.ToolJsonCodec;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 更新已有日程（写工具）。
 *
 * <p>用于「把某条日程改成 X 点 / 改时长 / 改标题 / 改优先级」类需求。
 * 语义约定：先按 id 读取原日程，仅覆盖模型显式给出的字段（局部更新），
 * 未给出的字段保持不变——避免「改了个时间、标题却丢了」的乌龙。</p>
 *
 * @author sorts
 */
@Component
public class UpdateScheduleTool extends AbstractScheduleWriteTool {

    private final ScheduleClient scheduleClient;

    public UpdateScheduleTool(ToolJsonCodec jsonCodec, ScheduleClient scheduleClient) {
        super(jsonCodec);
        this.scheduleClient = scheduleClient;
    }

    @Override
    public String name() {
        return "updateSchedule";
    }

    @Override
    public String description() {
        return "更新当前用户的一条已有日程：可按 id 修改标题、开始时间、时长、描述、优先级、标签或颜色。"
                + "只修改显式给出的字段，其余保持不变。此操作会真实修改用户数据。"
                + "注意：修改时间时若用户给出的是时间段（如 19:00-21:00），plannedStartTime 取起始时刻、"
                + "plannedDuration 取完整时段时长（如 120 分钟）。";
    }

    @Override
    public ToolLevel level() {
        return ToolLevel.WRITE;
    }

    @Override
    public Map<String, Object> parameters() {
        return Schemas.object(Schemas.props(
                "id", Schemas.integer("要修改的日程 ID（必填，可通过查询日程工具获得）"),
                "title", Schemas.string("新的标题，不修改则省略"),
                "plannedStartTime", Schemas.string("新的计划开始时间，格式 yyyy-MM-dd HH:mm:ss，不修改则省略"),
                "plannedDuration", Schemas.integer("新的计划时长（分钟），必须大于 0，不修改则省略"),
                "description", Schemas.string("新的详细描述，可为空，不修改则省略"),
                "priority", Schemas.enumString(Schemas.list("LOW", "MEDIUM", "HIGH", "URGENT"), "新的优先级，不修改则省略"),
                "tags", Schemas.array(Schemas.string("标签名"), "新的标签列表，不修改则省略"),
                "color", Schemas.string("新的主题色，十六进制如 #4A90D9，不修改则省略")
        ), "要修改的日程 ID", "id");
    }

    @Override
    public String execute(Long userId, JsonNode args) {
        Long id = JsonArgs.longValue(args, "id");
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("id 必须是大于 0 的日程 ID");
        }
        // 先读原日程，做局部更新
        ScheduleDto current = Results.unwrap("读取日程",
                scheduleClient.get(userId, id));
        if (current == null) {
            throw new IllegalArgumentException("未找到 ID=" + id + " 的日程，可能已被删除");
        }
        ScheduleSaveDto dto = toUpdateDto(args, current);
        ScheduleDto updated = Results.unwrap("更新日程",
                scheduleClient.update(userId, id, dto));

        Map<String, Object> data = result();
        data.put("success", true);
        data.put("id", updated.getId());
        data.put("title", updated.getTitle());
        data.put("plannedStartTime", updated.getPlannedStartTime());
        data.put("plannedDuration", updated.getPlannedDuration());
        data.put("priority", updated.getPriority());
        data.put("status", updated.getStatus());
        return json(data);
    }

    /** 参数 → 契约：仅在参数显式出现时覆盖原值 */
    private ScheduleSaveDto toUpdateDto(JsonNode args, ScheduleDto current) {
        ScheduleSaveDto dto = new ScheduleSaveDto();
        dto.setTitle(JsonArgs.has(args, "title") ? JsonArgs.requiredText(args, "title") : current.getTitle());
        if (JsonArgs.has(args, "plannedStartTime")) {
            dto.setPlannedStartTime(com.sorts.ai.tool.support.DateTimes.parseDateTime(
                    JsonArgs.requiredText(args, "plannedStartTime"), "plannedStartTime"));
        } else {
            dto.setPlannedStartTime(current.getPlannedStartTime());
        }
        if (JsonArgs.has(args, "plannedDuration")) {
            Integer duration = JsonArgs.integer(args, "plannedDuration");
            if (duration == null || duration <= 0) {
                throw new IllegalArgumentException("plannedDuration 必须是大于 0 的整数（单位：分钟）");
            }
            dto.setPlannedDuration(duration);
        } else {
            dto.setPlannedDuration(current.getPlannedDuration());
        }
        dto.setDescription(JsonArgs.has(args, "description") ? JsonArgs.text(args, "description") : current.getDescription());
        dto.setPriority(JsonArgs.has(args, "priority") ? JsonArgs.text(args, "priority") : current.getPriority());
        if (JsonArgs.has(args, "tags")) {
            List<String> tags = JsonArgs.texts(args, "tags");
            dto.setTags(tags.isEmpty() ? null : tags);
        } else {
            dto.setTags(current.getTags());
        }
        dto.setColor(JsonArgs.has(args, "color") ? JsonArgs.text(args, "color") : current.getColor());
        return dto;
    }
}
