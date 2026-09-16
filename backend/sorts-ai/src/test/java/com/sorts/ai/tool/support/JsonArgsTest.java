package com.sorts.ai.tool.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 参数安全取值单元测试（模型给的参数一律不可信）。
 *
 * @author sorts
 */
class JsonArgsTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private JsonNode tree(String json) throws Exception {
        return objectMapper.readTree(json);
    }

    @Test
    @DisplayName("字符串：缺失、null、空白一律归一为 null")
    void textNullish() throws Exception {
        JsonNode args = tree("{\"a\":\"  \",\"b\":null,\"c\":\"x\"}");
        assertNull(JsonArgs.text(args, "a"));
        assertNull(JsonArgs.text(args, "b"));
        assertNull(JsonArgs.text(args, "missing"));
        assertEquals("x", JsonArgs.text(args, "c"));
    }

    @Test
    @DisplayName("字符串：模型把数字写进字符串字段时兜底转换")
    void textFromNumber() throws Exception {
        assertEquals("9", JsonArgs.text(tree("{\"hour\":9}"), "hour"));
        assertEquals("true", JsonArgs.text(tree("{\"flag\":true}"), "flag"));
    }

    @Test
    @DisplayName("必填字符串缺失：抛出含字段名的异常")
    void requiredTextMissing() throws Exception {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> JsonArgs.requiredText(tree("{}"), "title"));
        assertTrue(error.getMessage().contains("title"));
    }

    @Test
    @DisplayName("整数：数字与数字字符串都可，非数字返回 null")
    void integerVariants() throws Exception {
        assertEquals(30, JsonArgs.integer(tree("{\"n\":30}"), "n"));
        assertEquals(30, JsonArgs.integer(tree("{\"n\":\"30\"}"), "n"));
        assertNull(JsonArgs.integer(tree("{\"n\":\"三十\"}"), "n"));
        assertNull(JsonArgs.integer(tree("{}"), "n"));
    }

    @Test
    @DisplayName("整数缺省值：缺失时回落到默认值")
    void integerOrFallback() throws Exception {
        assertEquals(10, JsonArgs.integerOr(tree("{}"), "limit", 10));
        assertEquals(5, JsonArgs.integerOr(tree("{\"limit\":5}"), "limit", 10));
    }

    @Test
    @DisplayName("布尔：字符串 true/false 也识别")
    void boolVariants() throws Exception {
        assertEquals(Boolean.TRUE, JsonArgs.bool(tree("{\"f\":true}"), "f"));
        assertEquals(Boolean.TRUE, JsonArgs.bool(tree("{\"f\":\"true\"}"), "f"));
        assertEquals(Boolean.FALSE, JsonArgs.bool(tree("{\"f\":\"False\"}"), "f"));
        assertNull(JsonArgs.bool(tree("{\"f\":\"是\"}"), "f"));
    }

    @Test
    @DisplayName("字符串数组：数组与逗号分隔串都吃")
    void textsVariants() throws Exception {
        assertEquals(List.of("学习", "Java"), JsonArgs.texts(tree("{\"tags\":[\"学习\",\"Java\"]}"), "tags"));
        assertEquals(List.of("学习", "Java"), JsonArgs.texts(tree("{\"tags\":\"学习, Java\"}"), "tags"));
        assertEquals(List.of(), JsonArgs.texts(tree("{\"tags\":[]}"), "tags"));
        assertEquals(List.of(), JsonArgs.texts(tree("{}"), "tags"));
    }

    @Test
    @DisplayName("数组取值：非数组返回 null")
    void arrayGuard() throws Exception {
        assertNull(JsonArgs.array(tree("{\"schedules\":\"x\"}"), "schedules"));
        assertEquals(1, JsonArgs.array(tree("{\"schedules\":[{}]}"), "schedules").size());
    }
}
