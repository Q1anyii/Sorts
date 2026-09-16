package com.sorts.ai.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * JSON 载荷提取单元测试：模型总爱加代码块与寒暄，解析侧必须容错。
 *
 * @author sorts
 */
class JsonPayloadsTest {

    @Test
    @DisplayName("裸 JSON：原样返回")
    void plainJson() {
        assertEquals("{\"a\":1}", JsonPayloads.extractObject("{\"a\":1}"));
    }

    @Test
    @DisplayName("Markdown 代码块：剥掉围栏")
    void fencedJson() {
        String raw = "好的，以下是建议：\n```json\n{\"suggestions\":[]}\n```\n希望对你有帮助。";
        assertEquals("{\"suggestions\":[]}", JsonPayloads.extractObject(raw));
    }

    @Test
    @DisplayName("前后有闲聊文字：按首尾花括号截取")
    void surroundedByProse() {
        assertEquals("{\"title\":\"x\"}", JsonPayloads.extractObject("当然可以。{\"title\":\"x\"} 以上。"));
    }

    @Test
    @DisplayName("嵌套对象：取到最外层闭合")
    void nestedObject() {
        assertEquals("{\"a\":{\"b\":1}}", JsonPayloads.extractObject("前缀{\"a\":{\"b\":1}}后缀"));
    }

    @Test
    @DisplayName("没有 JSON：返回 null，由调用方给出可读错误")
    void noJson() {
        assertNull(JsonPayloads.extractObject("我想了一下，还是别排了吧。"));
        assertNull(JsonPayloads.extractObject(null));
        assertNull(JsonPayloads.extractObject("}反了{"));
    }
}
