package com.sorts.ai.support;

import com.sorts.ai.tool.support.ToolJsonCodec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * SSE 输出封装单元测试：事件名/载荷格式、断连容错、关闭语义。
 *
 * <p>这里钉住的是「前端契约」：事件名与载荷结构一旦改动，前端解析就会静默失效，
 * 所以必须有测试守住。断言做在 SseEmitter 真正渲染出的文本行上
 * （{@code event:delta} / {@code data:{...}}），而不是停留在「send 被调用过」。</p>
 *
 * @author sorts
 */
class SseStreamTest {

    @Test
    @DisplayName("正文增量：事件名 delta，载荷为 {\"content\":\"...\"}")
    void deltaEventShape() throws Exception {
        SseEmitter emitter = mock(SseEmitter.class);
        SseStream stream = new SseStream(emitter, new ToolJsonCodec());

        stream.delta("你好");

        String raw = rendered(emitter, 1);
        assertTrue(raw.contains("event:delta"), raw);
        assertTrue(raw.contains("{\"content\":\"你好\"}"), raw);
    }

    @Test
    @DisplayName("done：先发结果事件再关闭连接")
    void doneSendsThenCompletes() throws Exception {
        SseEmitter emitter = mock(SseEmitter.class);
        SseStream stream = new SseStream(emitter, new ToolJsonCodec());

        stream.done(java.util.Map.of("reply", "ok"));

        String raw = rendered(emitter, 1);
        assertTrue(raw.contains("event:done"), raw);
        assertTrue(raw.contains("\"reply\":\"ok\""), raw);
        verify(emitter).complete();
    }

    @Test
    @DisplayName("error：事件名为 error，载荷含错误码与可读提示")
    void errorEventShape() throws Exception {
        SseEmitter emitter = mock(SseEmitter.class);
        SseStream stream = new SseStream(emitter, new ToolJsonCodec());

        stream.error(503, "梭灵尚未配置模型密钥");

        String raw = rendered(emitter, 1);
        assertTrue(raw.contains("event:error"), raw);
        assertTrue(raw.contains("\"code\":503"), raw);
        assertTrue(raw.contains("梭灵尚未配置模型密钥"), raw);
        verify(emitter).complete();
    }

    @Test
    @DisplayName("null 与空串不下发，避免前端收到空事件")
    void blankDeltaSkipped() throws Exception {
        SseEmitter emitter = mock(SseEmitter.class);
        SseStream stream = new SseStream(emitter, new ToolJsonCodec());

        stream.delta(null);
        stream.delta("");

        verify(emitter, never()).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    @DisplayName("纯空白增量照常下发：Markdown 流式渲染依赖空格与换行，丢弃会破坏排版")
    void whitespaceDeltaKept() throws Exception {
        SseEmitter emitter = mock(SseEmitter.class);
        SseStream stream = new SseStream(emitter, new ToolJsonCodec());

        stream.delta("  ");

        assertTrue(rendered(emitter, 1).contains("{\"content\":\"  \"}"));
    }

    @Test
    @DisplayName("客户端断开：异常不外抛，且后续不再尝试发送")
    void brokenPipeIsSwallowed() throws Exception {
        SseEmitter emitter = mock(SseEmitter.class);
        doThrow(new IOException("Broken pipe")).when(emitter).send(any(SseEmitter.SseEventBuilder.class));
        SseStream stream = new SseStream(emitter, new ToolJsonCodec());

        assertDoesNotThrow(() -> stream.delta("第一段"));
        assertDoesNotThrow(() -> stream.delta("第二段"));

        verify(emitter, times(1)).send(any(SseEmitter.SseEventBuilder.class));
    }

    /** 取出 SseEmitter 真正渲染的文本（把事件名行与数据行拼起来便于断言） */
    private String rendered(SseEmitter emitter, int sendCount) throws Exception {
        var captor = org.mockito.ArgumentCaptor.forClass(SseEmitter.SseEventBuilder.class);
        verify(emitter, times(sendCount)).send(captor.capture());
        StringBuilder builder = new StringBuilder();
        for (ResponseBodyEmitter.DataWithMediaType item : captor.getValue().build()) {
            if (item.getData() instanceof String text) {
                builder.append(text);
            }
        }
        return builder.toString();
    }
}
