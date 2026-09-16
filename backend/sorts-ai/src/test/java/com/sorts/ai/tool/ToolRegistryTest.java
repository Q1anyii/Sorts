package com.sorts.ai.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.sorts.ai.config.DeepSeekProperties;
import com.sorts.ai.llm.protocol.ToolCall;
import com.sorts.ai.llm.protocol.ToolDefinition;
import com.sorts.ai.tool.support.ToolJsonCodec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 工具注册表单元测试：能力边界（声明过滤）、执行前复核、异常隔离。
 *
 * <p>权限模型是「双钥匙」，因此这里的用例必须成对覆盖
 * ——服务端开关 与 请求开关的四组组合，漏掉任何一组都可能出现越权写入。</p>
 *
 * @author sorts
 */
class ToolRegistryTest {

    private static final Long USER_ID = 7L;

    private final ToolJsonCodec jsonCodec = new ToolJsonCodec();

    private DeepSeekProperties properties;

    @BeforeEach
    void setUp() {
        properties = new DeepSeekProperties();
    }

    @Test
    @DisplayName("双钥匙都关闭：写工具既不下发声明，也拒绝执行")
    void writeToolHiddenAndRejectedWhenDisabled() throws Exception {
        ToolRegistry registry = new ToolRegistry(List.of(
                stub("readTool", ToolLevel.READ, args -> "{\"ok\":true}"),
                stub("createSchedule", ToolLevel.WRITE, args -> "{\"created\":1}")), properties, jsonCodec);

        assertEquals(List.of("readTool"), names(registry.definitions(false)));

        String denied = registry.invoke(USER_ID, call("createSchedule", "{}"), false);
        assertTrue(denied.contains("\"success\":false"), denied);
        assertTrue(denied.contains("未授权"), denied);
        assertFalse(denied.contains("created"));
    }

    @Test
    @DisplayName("仅服务端开关打开：请求未确认时写工具仍不可见")
    void serverSwitchAloneIsNotEnough() {
        properties.getTool().setAllowWrite(true);
        ToolRegistry registry = new ToolRegistry(List.of(
                stub("readTool", ToolLevel.READ, args -> "{}"),
                stub("createSchedule", ToolLevel.WRITE, args -> "{}")), properties, jsonCodec);

        assertEquals(List.of("readTool"), names(registry.definitions(false)));
    }

    @Test
    @DisplayName("双钥匙齐备：写工具下发声明并可执行")
    void bothKeysUnlockWrite() {
        properties.getTool().setAllowWrite(true);
        ToolRegistry registry = new ToolRegistry(List.of(
                stub("readTool", ToolLevel.READ, args -> "{}"),
                stub("createSchedule", ToolLevel.WRITE, args -> "{\"created\":1}")), properties, jsonCodec);

        List<String> declared = names(registry.definitions(true));
        assertEquals(2, declared.size(), declared.toString());
        assertTrue(declared.contains("createSchedule"));

        String result = registry.invoke(USER_ID, call("createSchedule", "{\"title\":\"x\"}"), true);
        assertEquals("{\"created\":1}", result);
    }

    @Test
    @DisplayName("模型猜出未注册的工具名：返回可读错误而不是抛异常")
    void unknownToolReturnsError() {
        ToolRegistry registry = new ToolRegistry(List.of(), properties, jsonCodec);

        String result = registry.invoke(USER_ID, call("dropDatabase", "{}"), false);
        assertTrue(result.contains("不存在名为 dropDatabase 的工具"), result);
    }

    @Test
    @DisplayName("函数名缺失：返回错误而非空指针")
    void missingFunctionNameReturnsError() {
        ToolRegistry registry = new ToolRegistry(List.of(), properties, jsonCodec);
        ToolCall broken = new ToolCall();
        broken.setFunction(new ToolCall.Function());

        assertTrue(registry.invoke(USER_ID, broken, false).contains("缺少函数名"));
    }

    @Test
    @DisplayName("参数不是合法 JSON：不执行工具，直接回灌解析错误")
    void malformedArgumentsAreRejected() {
        ToolRegistry registry = new ToolRegistry(List.of(
                stub("readTool", ToolLevel.READ, args -> "SHOULD_NOT_RUN")), properties, jsonCodec);

        String result = registry.invoke(USER_ID, call("readTool", "{not json"), false);
        assertTrue(result.contains("不是合法 JSON"), result);
        assertFalse(result.contains("SHOULD_NOT_RUN"));
    }

    @Test
    @DisplayName("无参数调用：空 arguments 视为空对象，工具照常执行")
    void blankArgumentsBecomeEmptyObject() {
        ToolRegistry registry = new ToolRegistry(List.of(
                stub("readTool", ToolLevel.READ, args -> "{\"empty\":" + args.isEmpty() + "}")),
                properties, jsonCodec);

        assertEquals("{\"empty\":true}", registry.invoke(USER_ID, call("readTool", ""), false));
    }

    @Test
    @DisplayName("工具抛异常：捕获后转为错误观察结果，不打断整轮对话")
    void toolFailureIsIsolated() {
        ToolRegistry registry = new ToolRegistry(List.of(
                stub("readTool", ToolLevel.READ, args -> {
                    throw new IllegalStateException("下游超时");
                })), properties, jsonCodec);

        String result = registry.invoke(USER_ID, call("readTool", "{}"), false);
        assertTrue(result.contains("\"success\":false"), result);
        assertTrue(result.contains("下游超时"), result);
    }

    @Test
    @DisplayName("工具名重复：启动期即失败，避免静默覆盖")
    void duplicateToolNameFailsFast() {
        List<AiTool> duplicated = List.of(
                stub("dup", ToolLevel.READ, args -> "{}"),
                stub("dup", ToolLevel.READ, args -> "{}"));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new ToolRegistry(duplicated, properties, jsonCodec));
        assertTrue(error.getMessage().contains("dup"));
    }

    @Test
    @DisplayName("isWriteDenied：写工具在未授权时为真，读工具永远为假")
    void writeDeniedPrediction() {
        ToolRegistry registry = new ToolRegistry(List.of(
                stub("readTool", ToolLevel.READ, args -> "{}"),
                stub("createSchedule", ToolLevel.WRITE, args -> "{}")), properties, jsonCodec);

        assertTrue(registry.isWriteDenied("createSchedule", false));
        assertFalse(registry.isWriteDenied("readTool", false));

        properties.getTool().setAllowWrite(true);
        assertFalse(registry.isWriteDenied("createSchedule", true));
        // 仅服务端开关打开、请求未确认：仍然算被拒
        assertTrue(registry.isWriteDenied("createSchedule", false));

        // 未注册的工具名不应被判定为「被拒」
        assertFalse(registry.isWriteDenied("unknown", false));
    }

    private List<String> names(List<ToolDefinition> definitions) {
        return definitions.stream().map(definition -> definition.getFunction().getName()).sorted().toList();
    }

    private ToolCall call(String name, String arguments) {
        ToolCall.Function function = new ToolCall.Function();
        function.setName(name);
        function.setArguments(arguments);
        ToolCall call = new ToolCall();
        call.setId("call_" + name);
        call.setType("function");
        call.setFunction(function);
        return call;
    }

    private AiTool stub(String name, ToolLevel level, Function<JsonNode, String> behaviour) {
        return new AiTool() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public String description() {
                return name + " 的说明";
            }

            @Override
            public ToolLevel level() {
                return level;
            }

            @Override
            public Map<String, Object> parameters() {
                return Map.of("type", "object");
            }

            @Override
            public String execute(Long userId, JsonNode args) {
                return behaviour.apply(args);
            }
        };
    }
}
