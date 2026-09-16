package com.sorts.ai.tool.support;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON Schema 片段构造器。
 *
 * <p>工具的参数声明是「给模型看的接口文档」，手写 {@code Map.of(...)} 嵌套可读性极差、
 * 也容易漏字段；这里用极简静态工厂把结构表达清楚，同时保持输出顺序稳定
 * （LinkedHashMap），便于单测直接断言序列化结果。</p>
 *
 * @author sorts
 */
public final class Schemas {

    private Schemas() {
    }

    public static Map<String, Object> object(Map<String, Object> properties, String... required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties == null ? new LinkedHashMap<>() : properties);
        if (required != null && required.length > 0) {
            schema.put("required", List.of(required));
        }
        // 模型有时会多塞字段，明确禁止可减少无效参数
        schema.put("additionalProperties", false);
        return schema;
    }

    public static Map<String, Object> string(String description) {
        return typed("string", description);
    }

    /** 带取值约束的字符串（例如 status 只允许若干枚举值） */
    public static Map<String, Object> enumString(List<String> values, String description) {
        Map<String, Object> schema = typed("string", description);
        schema.put("enum", values);
        return schema;
    }

    public static Map<String, Object> integer(String description) {
        return typed("integer", description);
    }

    public static Map<String, Object> bool(String description) {
        return typed("boolean", description);
    }

    public static Map<String, Object> array(Map<String, Object> items, String description) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "array");
        schema.put("items", items);
        schema.put("description", description);
        return schema;
    }

    public static Map<String, Object> nested(Map<String, Object> properties, String description, String... required) {
        Map<String, Object> schema = object(properties, required);
        schema.put("description", description);
        return schema;
    }

    /** 按「键1, 值1, 键2, 值2...」组装有序属性表，省掉一层临时变量 */
    public static Map<String, Object> props(Object... keyValuePairs) {
        if (keyValuePairs.length % 2 != 0) {
            throw new IllegalArgumentException("props 需要成对的键值：" + keyValuePairs.length);
        }
        Map<String, Object> properties = new LinkedHashMap<>();
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            properties.put(String.valueOf(keyValuePairs[i]), keyValuePairs[i + 1]);
        }
        return properties;
    }

    public static List<String> list(String... values) {
        return new ArrayList<>(List.of(values));
    }

    private static Map<String, Object> typed(String type, String description) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", type);
        schema.put("description", description);
        return schema;
    }
}
