package com.sorts.ai.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.sorts.ai.client.ScheduleClient;
import com.sorts.ai.client.dto.ScheduleDto;
import com.sorts.ai.client.dto.ScheduleSaveDto;
import com.sorts.ai.tool.ToolLevel;
import com.sorts.ai.tool.support.ToolJsonCodec;
import com.sorts.ai.tool.support.Results;
import com.sorts.ai.tool.support.Schemas;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 创建单条日程（写工具）。
 *
 * <p>仅在「用户明确要求创建」时使用。工具本身不负责征询用户同意——
 * 授权由双钥匙机制在服务端强制把关，模型只负责表达意图。</p>
 *
 * @author sorts
 */
@Component
public class CreateScheduleTool extends AbstractScheduleWriteTool {

    private final ScheduleClient scheduleClient;

    public CreateScheduleTool(ToolJsonCodec jsonCodec, ScheduleClient scheduleClient) {
        super(jsonCodec);
        this.scheduleClient = scheduleClient;
    }

    @Override
    public String name() {
        return "createSchedule";
    }

    @Override
    public String description() {
        return "为当前用户创建一条日程。仅当用户明确要求「帮我加一个日程/安排/提醒」时才调用；"
                + "创建前必须已经确认标题与开始时间，不要凭猜测填写时间。此操作会真实写入用户数据。";
    }

    @Override
    public ToolLevel level() {
        return ToolLevel.WRITE;
    }

    @Override
    public Map<String, Object> parameters() {
        return Schemas.object(Schemas.props(
                "title", Schemas.string("日程标题"),
                "plannedStartTime", Schemas.string("计划开始时间，格式 yyyy-MM-dd HH:mm:ss"),
                "plannedDuration", Schemas.integer("计划时长（分钟），必须大于 0"),
                "description", Schemas.string("详细描述，可为空"),
                "priority", Schemas.enumString(Schemas.list("LOW", "MEDIUM", "HIGH", "URGENT"), "优先级，缺省 MEDIUM"),
                "tags", Schemas.array(Schemas.string("标签名"), "标签列表"),
                "color", Schemas.string("主题色，十六进制，可为空")
        ), "title", "plannedStartTime", "plannedDuration");
    }

    @Override
    public String execute(Long userId, JsonNode args) {
        ScheduleSaveDto request = toSaveDto(args);
        ScheduleDto created = Results.unwrap("创建日程", scheduleClient.create(userId, request));

        Map<String, Object> data = result();
        data.put("success", true);
        data.put("id", created.getId());
        data.put("title", created.getTitle());
        data.put("plannedStartTime", created.getPlannedStartTime());
        data.put("plannedDuration", created.getPlannedDuration());
        data.put("status", created.getStatus());
        data.put("priority", created.getPriority());
        data.put("tags", created.getTags());
        return json(data);
    }
}
