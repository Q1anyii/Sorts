package com.sorts.ai.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.sorts.ai.client.UserClient;
import com.sorts.ai.client.dto.PointsDto;
import com.sorts.ai.client.dto.PointsLogDto;
import com.sorts.ai.tool.ToolLevel;
import com.sorts.ai.tool.support.AbstractAiTool;
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
 * 查询光阴砂余额与流水。
 *
 * @author sorts
 */
@Component
public class QueryPointsTool extends AbstractAiTool {

    private static final int MAX_LIMIT = 50;

    private final UserClient userClient;

    public QueryPointsTool(ToolJsonCodec jsonCodec, UserClient userClient) {
        super(jsonCodec);
        this.userClient = userClient;
    }

    @Override
    public String name() {
        return "queryPoints";
    }

    @Override
    public String description() {
        return "查询当前用户的光阴砂余额与最近流水记录。"
                + "当用户问「我还有多少光阴砂」「最近光阴砂怎么变的」「积分明细」时调用。";
    }

    @Override
    public ToolLevel level() {
        return ToolLevel.READ;
    }

    @Override
    public Map<String, Object> parameters() {
        return Schemas.object(Schemas.props(
                "limit", Schemas.integer("返回的流水条数，缺省 10，最大 50")
        ));
    }

    @Override
    public String execute(Long userId, JsonNode args) {
        int limit = Math.min(Math.max(JsonArgs.integerOr(args, "limit", 10), 1), MAX_LIMIT);
        PointsDto points = Results.unwrap("查询光阴砂", userClient.points(userId, limit));

        Map<String, Object> data = result();
        data.put("balance", points == null ? null : points.getPoints());
        List<Map<String, Object>> logs = new ArrayList<>();
        if (points != null && points.getLogs() != null) {
            for (PointsLogDto log : points.getLogs()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("changeAmount", log.getChangeAmount());
                item.put("balance", log.getBalance());
                item.put("reason", log.getReason());
                item.put("createdAt", log.getCreatedAt());
                logs.add(item);
            }
        }
        data.put("logs", logs);
        return json(data);
    }
}
