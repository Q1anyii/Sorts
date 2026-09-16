package com.sorts.ai.support;

import com.sorts.ai.tool.support.ToolJsonCodec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * SSE 输出封装。
 *
 * <p>事件约定（前端按此解析）：</p>
 * <pre>
 * event: delta   data: {"content":"增量文本"}
 * event: done    data: { ...最终结构化结果... }
 * event: error   data: {"code":503,"message":"..."}
 * </pre>
 *
 * <p>为什么要包一层：{@link SseEmitter#send} 在客户端断开时会抛 IOException，
 * 而「用户点了停止生成」是完全正常的行为，不该被当成异常打满日志。
 * 统一在这里吞掉并标记终止，业务代码就只需关心发什么、不必关心发得出去发不出去。</p>
 *
 * @author sorts
 */
@Slf4j
public class SseStream {

    private final SseEmitter emitter;

    private final ToolJsonCodec jsonCodec;

    /** 客户端已断开：后续发送全部跳过，避免无意义的序列化与日志 */
    private volatile boolean closed;

    public SseStream(SseEmitter emitter, ToolJsonCodec jsonCodec) {
        this.emitter = emitter;
        this.jsonCodec = jsonCodec;
    }

    /** 推送一段正文增量 */
    public void delta(String content) {
        if (closed || content == null || content.isEmpty()) {
            return;
        }
        send("delta", Map.of("content", content));
    }

    /** 推送最终结果并正常结束 */
    public void done(Object payload) {
        if (!closed && payload != null) {
            send("done", payload);
        }
        complete();
    }

    /** 推送错误并结束 */
    public void error(int code, String message) {
        if (!closed) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("code", code);
            payload.put("message", message);
            send("error", payload);
        }
        complete();
    }

    /** 仅关闭连接（例如上游异常已由过滤器处理） */
    public void complete() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            emitter.complete();
        } catch (Exception e) {
            log.debug("SSE 关闭时连接已断开：{}", e.getMessage());
        }
    }

    private void send(String event, Object payload) {
        try {
            emitter.send(SseEmitter.event().name(event).data(jsonCodec.write(payload)));
        } catch (IOException | IllegalStateException e) {
            // 用户主动停止 / 网络断开：正常路径，不打 error 级别日志
            closed = true;
            log.debug("SSE 连接已断开，停止推送：{}", e.getMessage());
        }
    }
}
