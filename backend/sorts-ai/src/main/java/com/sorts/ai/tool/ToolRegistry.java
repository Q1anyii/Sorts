package com.sorts.ai.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.sorts.ai.config.DeepSeekProperties;
import com.sorts.ai.llm.protocol.ToolCall;
import com.sorts.ai.llm.protocol.ToolDefinition;
import com.sorts.ai.tool.support.ToolJsonCodec;
import com.sorts.common.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具注册表：模型能力的唯一出入口。
 *
 * <p>三件事都在这里收口，避免散落到各处：</p>
 * <ol>
 *   <li><b>声明过滤</b>——未获授权的工具不下发给模型。模型看不见的工具是不可能被调用的，
 *       这比「让它调、再拒绝」更省 token、也更难被提示词绕过；</li>
 *   <li><b>执行前复核</b>——下发时过滤过一次，执行时再校验一次。因为模型可能凭历史上下文
 *       猜出工具名，声明过滤不足以作为唯一防线；</li>
 *   <li><b>异常兜底</b>——工具失败不以异常打断整轮对话，而是把错误作为「观察结果」回灌，
 *       让模型有机会改参数重试或如实告知用户。</li>
 * </ol>
 *
 * @author sorts
 */
@Slf4j
@Component
public class ToolRegistry {

    private final Map<String, AiTool> tools;

    private final DeepSeekProperties properties;

    private final ToolJsonCodec jsonCodec;

    public ToolRegistry(List<AiTool> toolBeans, DeepSeekProperties properties, ToolJsonCodec jsonCodec) {
        this.properties = properties;
        this.jsonCodec = jsonCodec;
        Map<String, AiTool> registered = new LinkedHashMap<>();
        for (AiTool tool : toolBeans) {
            AiTool previous = registered.put(tool.name(), tool);
            if (previous != null) {
                throw new IllegalStateException("工具名重复：" + tool.name());
            }
        }
        this.tools = Map.copyOf(registered);
        log.info("梭灵工具集已装载：{}", this.tools.keySet());
    }

    /**
     * 是否放行写操作。
     *
     * <p>「双钥匙」：服务端总开关 与 本次请求的用户确认，二者同时为真才放行。</p>
     */
    public boolean writeEnabled(boolean requestAllowWrite) {
        return properties.getTool().isAllowWrite() && requestAllowWrite;
    }

    /** 下发给模型的工具声明（按写权限过滤） */
    public List<ToolDefinition> definitions(boolean requestAllowWrite) {
        boolean write = writeEnabled(requestAllowWrite);
        return tools.values().stream()
                .filter(tool -> !tool.level().isWrite() || write)
                .map(tool -> ToolDefinition.of(tool.name(), tool.description(), tool.parameters()))
                .toList();
    }

    public boolean contains(String name) {
        return tools.containsKey(name);
    }

    /**
     * 执行模型发起的一次工具调用。
     *
     * @return 回灌给模型的文本；无论成功失败都返回文本，不抛异常
     */
    public String invoke(Long userId, ToolCall call, boolean requestAllowWrite) {
        if (call == null || call.getFunction() == null || call.getFunction().getName() == null) {
            return error("工具调用缺少函数名");
        }
        String name = call.getFunction().getName();
        AiTool tool = tools.get(name);
        if (tool == null) {
            return error("不存在名为 " + name + " 的工具");
        }
        if (tool.level().isWrite() && !writeEnabled(requestAllowWrite)) {
            log.warn("拦截未授权的写工具调用 userId={}, tool={}", userId, name);
            return error("写操作未授权，无法执行 " + name
                    + "。请先向用户确认，再由用户在界面上开启「允许 AI 写入」后重试");
        }
        JsonNode args = parseArguments(name, call.getFunction().getArguments());
        if (args == null) {
            return error(name + " 的参数不是合法 JSON：" + call.getFunction().getArguments());
        }
        try {
            return tool.execute(userId, args);
        } catch (BizException e) {
            log.warn("工具 {} 业务失败 userId={}: {}", name, userId, e.getMessage());
            return error(e.getMessage());
        } catch (Exception e) {
            log.warn("工具 {} 执行异常 userId={}", name, userId, e);
            return error(name + " 执行失败：" + e.getMessage());
        }
    }

    private JsonNode parseArguments(String name, String raw) {
        String arguments = raw == null ? "" : raw.strip();
        if (arguments.isEmpty()) {
            return jsonCodec.newObject();
        }
        try {
            return jsonCodec.readTree(arguments);
        } catch (JsonProcessingException e) {
            log.warn("工具 {} 参数解析失败：{}", name, arguments);
            return null;
        }
    }

    private String error(String message) {
        return jsonCodec.write(Map.of("success", false, "error", message));
    }
}
