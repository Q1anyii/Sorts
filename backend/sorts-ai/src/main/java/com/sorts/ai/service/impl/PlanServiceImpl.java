package com.sorts.ai.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.sorts.ai.client.ScheduleClient;
import com.sorts.ai.client.dto.BatchCreateDto;
import com.sorts.ai.client.dto.ScheduleDto;
import com.sorts.ai.client.dto.ScheduleSaveDto;
import com.sorts.ai.config.DeepSeekProperties;
import com.sorts.ai.dto.AIPlanRequest;
import com.sorts.ai.dto.AIPlanResponse;
import com.sorts.ai.dto.AdoptPlanRequest;
import com.sorts.ai.dto.PlanAdjustments;
import com.sorts.ai.dto.PlanPreferences;
import com.sorts.ai.dto.PlanSuggestion;
import com.sorts.ai.entity.SchedulePlan;
import com.sorts.ai.enums.PlanStatus;
import com.sorts.ai.llm.ChatModelClient;
import com.sorts.ai.llm.protocol.ChatCompletionRequest;
import com.sorts.ai.llm.protocol.ChatMessage;
import com.sorts.ai.llm.protocol.LlmResult;
import com.sorts.ai.mapper.SchedulePlanMapper;
import com.sorts.ai.service.PlanService;
import com.sorts.ai.support.AiPrompts;
import com.sorts.ai.support.JsonPayloads;
import com.sorts.ai.tool.support.DateTimes;
import com.sorts.ai.tool.support.Results;
import com.sorts.ai.tool.support.ToolJsonCodec;
import com.sorts.common.exception.BizException;
import com.sorts.common.result.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 规划服务实现。
 *
 * <p>两条关键设计：</p>
 * <ul>
 *   <li><b>规划先落库再返回</b>——用户可能看完建议、过一会儿才点采纳，
 *       期间前端刷新就丢了；落库后采纳只需带 planId；</li>
 *   <li><b>采纳单次有效</b>——同一 planId 第二次采纳直接拒绝。
 *       否则用户双击「采纳」就会得到两份重复日程。</li>
 * </ul>
 *
 * @author sorts
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlanServiceImpl implements PlanService {

    /** 结构化输出场景用低温度：要的是稳定可解析，不是文采 */
    private static final double STRUCTURED_TEMPERATURE = 0.3D;

    /** 规划有效期：一天。日程规划有强时效，隔天采纳往往已与实际情况冲突 */
    private static final int VALID_HOURS = 24;

    /** 单次采纳上限，与下游批量接口一致 */
    private static final int MAX_ADOPT = 20;

    /** 用户未指定时间偏好时的默认可安排时段与时长 */
    private static final int DEFAULT_START_HOUR = 9;

    private static final int DEFAULT_END_HOUR = 22;

    private static final int DEFAULT_DURATION = 60;

    private final ChatModelClient chatModelClient;

    private final DeepSeekProperties properties;

    private final SchedulePlanMapper planMapper;

    private final ScheduleClient scheduleClient;

    private final ToolJsonCodec jsonCodec;

    @Override
    public AIPlanResponse generate(Long userId, AIPlanRequest request, boolean stream, Consumer<String> onDelta) {
        requireConfigured();
        LocalDate targetDate = request.getTargetDate() == null
                ? LocalDate.now().plusDays(1)
                : request.getTargetDate();
        PlanPreferences preferences = request.getPreferences();
        int startHour = preferences == null || preferences.getPreferredStartHour() == null
                ? DEFAULT_START_HOUR : preferences.getPreferredStartHour();
        int endHour = preferences == null || preferences.getPreferredEndHour() == null
                ? DEFAULT_END_HOUR : preferences.getPreferredEndHour();
        int defaultDuration = preferences == null || preferences.getDefaultDuration() == null
                ? DEFAULT_DURATION : preferences.getDefaultDuration();

        List<ChatMessage> messages = List.of(
                ChatMessage.system(AiPrompts.planSystem()),
                ChatMessage.user(AiPrompts.planPrompt(request.getUserPrompt(), targetDate,
                        startHour, endHour, defaultDuration)));

        ChatCompletionRequest completion = new ChatCompletionRequest();
        completion.setModel(properties.getModel());
        completion.setMessages(messages);
        completion.setTemperature(STRUCTURED_TEMPERATURE);
        completion.setMaxTokens(properties.getMaxTokens());

        LlmResult result;
        if (stream && onDelta != null) {
            completion.setStream(Boolean.TRUE);
            completion.enableStreamUsage();
            result = chatModelClient.stream(completion, onDelta);
        } else {
            result = chatModelClient.complete(completion);
        }

        List<PlanSuggestion> suggestions = parseSuggestions(result.getContent());
        if (suggestions.isEmpty()) {
            throw new BizException(ErrorCode.SERVICE_UNAVAILABLE,
                    "梭灵没能给出可用的规划建议，请把安排说得再具体一些（时间、事项、时长）");
        }

        SchedulePlan plan = new SchedulePlan();
        plan.setPlanId(UUID.randomUUID().toString());
        plan.setUserId(userId);
        plan.setTargetDate(targetDate);
        plan.setUserPrompt(request.getUserPrompt());
        plan.setSuggestions(jsonCodec.write(suggestions));
        plan.setStatus(PlanStatus.DRAFT);
        plan.setAdoptedCount(0);
        plan.setExpiresAt(LocalDateTime.now().plusHours(VALID_HOURS));
        planMapper.insert(plan);

        return AIPlanResponse.builder()
                .planId(plan.getPlanId())
                .suggestions(suggestions)
                .adopted(Boolean.FALSE)
                .createdScheduleIds(List.of())
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<ScheduleDto> adopt(Long userId, AdoptPlanRequest request) {
        SchedulePlan plan = planMapper.selectOne(Wrappers.<SchedulePlan>lambdaQuery()
                .eq(SchedulePlan::getPlanId, request.getPlanId())
                .eq(SchedulePlan::getUserId, userId));
        if (plan == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "规划不存在或已失效");
        }
        if (PlanStatus.ADOPTED.equals(plan.getStatus())) {
            throw new BizException(ErrorCode.CONFLICT, "该规划已采纳过，请重新生成后再试");
        }
        if (plan.getExpiresAt() != null && plan.getExpiresAt().isBefore(LocalDateTime.now())) {
            SchedulePlan expired = new SchedulePlan();
            expired.setId(plan.getId());
            expired.setStatus(PlanStatus.EXPIRED);
            planMapper.updateById(expired);
            throw new BizException(ErrorCode.CONFLICT, "规划已过期，请重新生成");
        }

        List<PlanSuggestion> suggestions = parseStoredSuggestions(plan.getSuggestions());
        List<PlanSuggestion> selected = select(suggestions, request.getSelectedIndices());
        if (selected.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "未选择任何要采纳的建议");
        }
        if (selected.size() > MAX_ADOPT) {
            throw new BizException(ErrorCode.PARAM_ERROR, "单次最多采纳 " + MAX_ADOPT + " 条建议");
        }

        PlanAdjustments adjustments = request.getAdjustments();
        LocalDate date = adjustments != null && adjustments.getDate() != null
                ? adjustments.getDate() : plan.getTargetDate();
        int offsetMinutes = adjustments != null && adjustments.getStartOffset() != null
                ? adjustments.getStartOffset() : 0;

        List<ScheduleSaveDto> payload = new ArrayList<>();
        for (PlanSuggestion suggestion : selected) {
            payload.add(toRequest(suggestion, date, offsetMinutes));
        }

        List<ScheduleDto> created = Results.unwrap("采纳规划",
                scheduleClient.batchCreate(userId, new BatchCreateDto(payload)));

        SchedulePlan adopted = new SchedulePlan();
        adopted.setId(plan.getId());
        adopted.setStatus(PlanStatus.ADOPTED);
        adopted.setAdoptedCount(created == null ? 0 : created.size());
        adopted.setCreatedScheduleIds(joinIds(created));
        planMapper.updateById(adopted);

        return created == null ? List.of() : created;
    }

    /**
     * 建议 → 日程创建请求。
     *
     * <p>{@code suggestedStart} 只有时分，日期由这里补上——模型不接触日期就不会算错日期。
     * 缺省开始时间兜到 09:00，避免因为模型漏字段就整批失败。</p>
     */
    private ScheduleSaveDto toRequest(PlanSuggestion suggestion, LocalDate date, int offsetMinutes) {
        ScheduleSaveDto dto = new ScheduleSaveDto();
        dto.setTitle(StringUtils.hasText(suggestion.getTitle()) ? suggestion.getTitle() : "未命名日程");
        dto.setDescription(suggestion.getDescription());
        LocalDateTime start = DateTimes.atTimeOn(date, suggestion.getSuggestedStart());
        if (start == null) {
            start = date.atTime(9, 0);
        }
        dto.setPlannedStartTime(start.plusMinutes(offsetMinutes));
        Integer duration = suggestion.getDuration();
        dto.setPlannedDuration(duration == null || duration <= 0 ? 60 : duration);
        dto.setPriority(normalizePriority(suggestion.getPriority()));
        dto.setTags(suggestion.getTags());
        return dto;
    }

    /** 规划只产出 LOW/MEDIUM/HIGH；落到日程侧统一映射到合法的四档枚举 */
    private String normalizePriority(String priority) {
        if (!StringUtils.hasText(priority)) {
            return "MEDIUM";
        }
        String upper = priority.strip().toUpperCase();
        return switch (upper) {
            case "LOW", "MEDIUM", "HIGH", "URGENT" -> upper;
            default -> "MEDIUM";
        };
    }

    /** 按用户勾选筛选；未传则全选。下标非法直接报错，避免静默少建 */
    private List<PlanSuggestion> select(List<PlanSuggestion> suggestions, List<Integer> indices) {
        if (indices == null || indices.isEmpty()) {
            return suggestions;
        }
        List<PlanSuggestion> selected = new ArrayList<>();
        for (Integer index : indices) {
            if (index == null || index < 0 || index >= suggestions.size()) {
                throw new BizException(ErrorCode.PARAM_ERROR, "建议下标超出范围：" + index);
            }
            selected.add(suggestions.get(index));
        }
        return selected;
    }

    private List<PlanSuggestion> parseSuggestions(String content) {
        String payload = JsonPayloads.extractObject(content);
        if (payload == null) {
            log.warn("模型未返回 JSON 结构，原文前 300 字：{}", abbreviate(content));
            return List.of();
        }
        try {
            JsonNode root = jsonCodec.readTree(payload);
            JsonNode node = root.get("suggestions");
            if (node == null || !node.isArray() || node.isEmpty()) {
                return List.of();
            }
            return jsonCodec.mapper().convertValue(node, new TypeReference<List<PlanSuggestion>>() {
            });
        } catch (Exception e) {
            log.warn("规划 JSON 解析失败：{}", abbreviate(payload), e);
            return List.of();
        }
    }

    private List<PlanSuggestion> parseStoredSuggestions(String stored) {
        if (!StringUtils.hasText(stored)) {
            return List.of();
        }
        try {
            return jsonCodec.mapper().readValue(stored, new TypeReference<List<PlanSuggestion>>() {
            });
        } catch (Exception e) {
            log.warn("已存规划解析失败，planId 相关记录可能已损坏", e);
            return List.of();
        }
    }

    private String joinIds(List<ScheduleDto> created) {
        if (created == null || created.isEmpty()) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        for (ScheduleDto dto : created) {
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(dto.getId());
        }
        return builder.toString();
    }

    private String abbreviate(String text) {
        if (text == null) {
            return "(null)";
        }
        return text.length() > 300 ? text.substring(0, 300) + "..." : text;
    }

    private void requireConfigured() {
        if (!properties.isConfigured()) {
            throw new BizException(ErrorCode.SERVICE_UNAVAILABLE,
                    "梭灵尚未配置模型密钥：请设置环境变量 DEEPSEEK_API_KEY 后重启 sorts-ai");
        }
    }
}
