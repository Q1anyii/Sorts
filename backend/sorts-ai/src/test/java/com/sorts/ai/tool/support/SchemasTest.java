package com.sorts.ai.tool.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JSON Schema 构造单元测试。
 *
 * <p>重点是「结构稳定」：模型对 schema 的解析依赖字段完整，
 * 丢 required 会让必填变可选，丢 additionalProperties=false 会让它乱塞字段。</p>
 *
 * @author sorts
 */
class SchemasTest {

    @Test
    @DisplayName("object：带 required 且显式禁止多余字段")
    void objectWithRequired() {
        Map<String, Object> schema = Schemas.object(
                Schemas.props("title", Schemas.string("标题")), "title");

        assertEquals("object", schema.get("type"));
        assertEquals(List.of("title"), schema.get("required"));
        assertEquals(Boolean.FALSE, schema.get("additionalProperties"));
        assertTrue(schema.get("properties") instanceof Map);
    }

    @Test
    @DisplayName("object：无 required 时不产生该键")
    void objectWithoutRequired() {
        Map<String, Object> schema = Schemas.object(Schemas.props());
        assertFalse(schema.containsKey("required"));
    }

    @Test
    @DisplayName("enumString：类型、说明、枚举值三者齐备")
    void enumString() {
        Map<String, Object> schema = Schemas.enumString(Schemas.list("LOW", "HIGH"), "优先级");
        assertEquals("string", schema.get("type"));
        assertEquals("优先级", schema.get("description"));
        assertEquals(List.of("LOW", "HIGH"), schema.get("enum"));
    }

    @Test
    @DisplayName("props：保持插入顺序，便于单测与阅读")
    void propsKeepsOrder() {
        Map<String, Object> props = Schemas.props("b", 1, "a", 2, "c", 3);
        assertEquals(List.of("b", "a", "c"), new ArrayList<>(props.keySet()));
    }

    @Test
    @DisplayName("props：键值不成对时立即失败（避免静默丢字段）")
    void propsRejectsOddArguments() {
        assertThrows(IllegalArgumentException.class, () -> Schemas.props("a", 1, "b"));
    }

    @Test
    @DisplayName("array：items 与 description 都落到 schema 上")
    void arraySchema() {
        Map<String, Object> schema = Schemas.array(Schemas.string("标签名"), "标签列表");
        assertEquals("array", schema.get("type"));
        assertEquals("标签列表", schema.get("description"));
        assertEquals("string", ((Map<?, ?>) schema.get("items")).get("type"));
    }

    @Test
    @DisplayName("nested：内层对象仍需带 required 与 additionalProperties")
    void nestedSchema() {
        Map<String, Object> schema = Schemas.nested(
                Schemas.props("title", Schemas.string("标题")), "单个日程", "title");
        assertEquals("object", schema.get("type"));
        assertEquals("单个日程", schema.get("description"));
        assertEquals(List.of("title"), schema.get("required"));
        assertEquals(Boolean.FALSE, schema.get("additionalProperties"));
    }
}
