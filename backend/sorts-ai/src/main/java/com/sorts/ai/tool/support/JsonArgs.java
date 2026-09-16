package com.sorts.ai.tool.support;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 从模型给的参数树里安全取值。
 *
 * <p>模型的参数永远不能当成「已校验通过」来用：字段可能缺失、类型可能不符
 * （例如把数字写成字符串）。这里统一做「缺失即 null、类型不符即 null」的宽松读取，
 * 由各工具自己决定哪些是必填并给出可读的提示。</p>
 *
 * @author sorts
 */
public final class JsonArgs {

    private JsonArgs() {
    }

    public static boolean has(JsonNode args, String field) {
        return args != null && args.hasNonNull(field);
    }

    /** 取字符串；空串、全空白、显式 null 一律视为未提供 */
    public static String text(JsonNode args, String field) {
        if (args == null || !args.hasNonNull(field)) {
            return null;
        }
        JsonNode node = args.get(field);
        if (node.isTextual()) {
            String value = node.asText();
            return StringUtils.hasText(value) ? value.strip() : null;
        }
        // 模型把数字/布尔塞进字符串字段时兜一手，比直接判 null 更耐用
        if (node.isNumber() || node.isBoolean()) {
            return node.asText();
        }
        return null;
    }

    public static String requiredText(JsonNode args, String field) {
        String value = text(args, field);
        if (value == null) {
            throw new IllegalArgumentException("缺少必填参数 " + field);
        }
        return value;
    }

    public static Integer integer(JsonNode args, String field) {
        if (args == null || !args.hasNonNull(field)) {
            return null;
        }
        JsonNode node = args.get(field);
        if (node.isNumber()) {
            return node.asInt();
        }
        if (node.isTextual()) {
            try {
                return Integer.valueOf(node.asText().strip());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    public static int integerOr(JsonNode args, String field, int fallback) {
        Integer value = integer(args, field);
        return value == null ? fallback : value;
    }

    public static Boolean bool(JsonNode args, String field) {
        if (args == null || !args.hasNonNull(field)) {
            return null;
        }
        JsonNode node = args.get(field);
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        if (node.isTextual()) {
            String value = node.asText().strip();
            if ("true".equalsIgnoreCase(value)) {
                return Boolean.TRUE;
            }
            if ("false".equalsIgnoreCase(value)) {
                return Boolean.FALSE;
            }
        }
        return null;
    }

    /** 取字符串数组，同时容忍模型给出「逗号分隔的单字符串」 */
    public static List<String> texts(JsonNode args, String field) {
        if (args == null || !args.hasNonNull(field)) {
            return List.of();
        }
        JsonNode node = args.get(field);
        List<String> values = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode item : node) {
                if (item != null && item.isTextual() && StringUtils.hasText(item.asText())) {
                    values.add(item.asText().strip());
                }
            }
        } else if (node.isTextual() && StringUtils.hasText(node.asText())) {
            for (String part : node.asText().split(",")) {
                if (StringUtils.hasText(part)) {
                    values.add(part.strip());
                }
            }
        }
        return values;
    }

    public static JsonNode array(JsonNode args, String field) {
        if (args == null || !args.hasNonNull(field)) {
            return null;
        }
        JsonNode node = args.get(field);
        return node.isArray() ? node : null;
    }
}
