package com.sorts.ai.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.sorts.ai.client.ScheduleClient;
import com.sorts.ai.client.dto.ScheduleDto;
import com.sorts.ai.client.dto.ScheduleQueryDto;
import com.sorts.ai.tool.ToolLevel;
import com.sorts.ai.tool.support.AbstractAiTool;
import com.sorts.ai.tool.support.ToolJsonCodec;
import com.sorts.ai.tool.support.DateTimes;
import com.sorts.ai.tool.support.JsonArgs;
import com.sorts.ai.tool.support.Results;
import com.sorts.ai.tool.support.Schemas;
import com.sorts.common.result.PageData;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 查询日程列表。
 *
 * <p>只回灌「模型真正用得上」的字段，而不是把下游 VO 原样丢给模型：
 * 抖音式的全字段回灌会迅速吃掉上下文预算，且无用字段会诱导模型编造不存在的属性。</p>
 *
 * @author sorts
 */
@Component
public class QuerySchedulesTool extends AbstractAiTool {

    private static final int MAX_PAGE_SIZE = 50;

    private final ScheduleClient scheduleClient;

    public QuerySchedulesTool(ToolJsonCodec jsonCodec, ScheduleClient scheduleClient) {
        super(jsonCodec);
        this.scheduleClient = scheduleClient;
    }

    @Override
    public String name() {
        return "querySchedules";
    }

    @Override
    public String description() {
        return "查询当前用户的日程列表。可按日期、视图粒度（日/周/月）、状态、优先级、标签、关键词筛选。"
                + "当用户问「我今天/这周/这个月的安排」「有哪些没完成的日程」「帮我找一下关于 XX 的日程」时调用。";
    }

    @Override
    public ToolLevel level() {
        return ToolLevel.READ;
    }

    @Override
    public Map<String, Object> parameters() {
        return Schemas.object(Schemas.props(
                "date", Schemas.string("基准日期，格式 yyyy-MM-dd，配合 view 使用；缺省表示今天"),
                "view", Schemas.enumString(Schemas.list("day", "week", "month", "all"),
                        "视图粒度，缺省 all（全部时间范围）"),
                "status", Schemas.enumString(Schemas.list("PENDING", "IN_PROGRESS", "PAUSED", "COMPLETED", "CANCELLED"),
                        "按状态筛选"),
                "priority", Schemas.enumString(Schemas.list("LOW", "MEDIUM", "HIGH", "URGENT"), "按优先级筛选"),
                "tag", Schemas.string("按标签筛选，单个标签名"),
                "keyword", Schemas.string("关键词，对标题与描述做模糊匹配"),
                "page", Schemas.integer("页码，从 1 开始，缺省 1"),
                "pageSize", Schemas.integer("每页条数，缺省 20，最大 50"),
                "sort", Schemas.enumString(Schemas.list("plannedStartTime", "createdAt", "priority"),
                        "排序字段，缺省 plannedStartTime"),
                "order", Schemas.enumString(Schemas.list("asc", "desc"), "排序方向，缺省 desc")
        ));
    }

    @Override
    public String execute(Long userId, JsonNode args) {
        ScheduleQueryDto query = new ScheduleQueryDto();
        query.setView(JsonArgs.text(args, "view"));
        String dateText = JsonArgs.text(args, "date");
        if (dateText != null) {
            query.setDate(DateTimes.parseDate(dateText, "date"));
        }
        query.setStatus(JsonArgs.text(args, "status"));
        query.setPriority(JsonArgs.text(args, "priority"));
        query.setTag(JsonArgs.text(args, "tag"));
        query.setKeyword(JsonArgs.text(args, "keyword"));
        query.setPage(JsonArgs.integerOr(args, "page", 1));
        query.setPageSize(Math.min(JsonArgs.integerOr(args, "pageSize", 20), MAX_PAGE_SIZE));
        String sort = JsonArgs.text(args, "sort");
        if (sort != null) {
            query.setSort(sort);
        }
        String order = JsonArgs.text(args, "order");
        if (order != null) {
            query.setOrder(order);
        }

        PageData<ScheduleDto> page = Results.unwrap("查询日程列表", scheduleClient.list(userId, query));
        List<Map<String, Object>> items = new ArrayList<>();
        if (page != null && page.getList() != null) {
            for (ScheduleDto item : page.getList()) {
                items.add(brief(item));
            }
        }
        Map<String, Object> data = result();
        data.put("total", page == null ? 0 : page.getTotal());
        data.put("page", page == null ? 1 : page.getPage());
        data.put("pageSize", page == null ? 0 : page.getPageSize());
        data.put("count", items.size());
        data.put("schedules", items);
        return json(data);
    }

    /** 精简视图：字段名保持不变，模型无需二次映射 */
    private Map<String, Object> brief(ScheduleDto schedule) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", schedule.getId());
        item.put("title", schedule.getTitle());
        item.put("plannedStartTime", schedule.getPlannedStartTime());
        item.put("plannedDuration", schedule.getPlannedDuration());
        item.put("actualDuration", schedule.getActualDuration());
        item.put("status", schedule.getStatus());
        item.put("priority", schedule.getPriority());
        item.put("tags", schedule.getTags());
        return item;
    }
}
