package com.sorts.ai.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.sorts.ai.client.dto.ScheduleSaveDto;
import com.sorts.ai.tool.support.AbstractAiTool;
import com.sorts.ai.tool.support.DateTimes;
import com.sorts.ai.tool.support.JsonArgs;
import com.sorts.ai.tool.support.Schemas;
import com.sorts.ai.tool.support.ToolJsonCodec;

import java.util.List;
import java.util.Map;

/**
 * 写日程类工具的公共部分：单个日程的参数 schema 与「参数 → 契约对象」转换。
 *
 * <p>单条创建与批量创建共用同一份定义，避免两处字段说明漂移
 * （一处加了 color、另一处忘了，模型就会出现「有时能填颜色、有时不能」的怪现象）。</p>
 *
 * @author sorts
 */
abstract class AbstractScheduleWriteTool extends AbstractAiTool {

    protected AbstractScheduleWriteTool(ToolJsonCodec jsonCodec) {
        super(jsonCodec);
    }

    /** 单个日程的 JSON Schema */
    protected static Map<String, Object> scheduleItemSchema() {
        return Schemas.nested(Schemas.props(
                "title", Schemas.string("日程标题，一句话说清要做什么"),
                "plannedStartTime", Schemas.string("计划开始时间，格式 yyyy-MM-dd HH:mm:ss，必须是具体到分钟的绝对时间"),
                "plannedDuration", Schemas.integer("计划时长（分钟），必须大于 0"),
                "description", Schemas.string("详细描述，可为空"),
                "priority", Schemas.enumString(Schemas.list("LOW", "MEDIUM", "HIGH", "URGENT"), "优先级，缺省 MEDIUM"),
                "tags", Schemas.array(Schemas.string("标签名"), "标签列表，例如 [\"学习\", \"Java\"]"),
                "color", Schemas.string("主题色，十六进制如 #4A90D9，可为空")
        ), "单个日程对象", "title", "plannedStartTime", "plannedDuration");
    }

    /** 模型给出的 JSON 对象 → 日程创建契约 */
    protected ScheduleSaveDto toSaveDto(JsonNode item) {
        if (item == null || !item.isObject()) {
            throw new IllegalArgumentException("日程必须是 JSON 对象");
        }
        ScheduleSaveDto dto = new ScheduleSaveDto();
        dto.setTitle(JsonArgs.requiredText(item, "title"));
        dto.setPlannedStartTime(DateTimes.parseDateTime(
                JsonArgs.requiredText(item, "plannedStartTime"), "plannedStartTime"));
        Integer duration = JsonArgs.integer(item, "plannedDuration");
        if (duration == null || duration <= 0) {
            throw new IllegalArgumentException("plannedDuration 必须是大于 0 的整数（单位：分钟）");
        }
        dto.setPlannedDuration(duration);
        dto.setDescription(JsonArgs.text(item, "description"));
        dto.setPriority(JsonArgs.text(item, "priority"));
        List<String> tags = JsonArgs.texts(item, "tags");
        dto.setTags(tags.isEmpty() ? null : tags);
        dto.setColor(JsonArgs.text(item, "color"));
        return dto;
    }
}
