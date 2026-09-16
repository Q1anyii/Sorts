package com.sorts.ai.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.sorts.ai.llm.protocol.ChatMessage;
import com.sorts.ai.tool.support.ToolJsonCodec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 多轮对话上下文存储（Redis）。
 *
 * <p>三个刻意的取舍：</p>
 * <ol>
 *   <li><b>不落 MySQL</b>——对话是可丢弃的临时状态，写库会给「AI 库」带来无意义的写放大，
 *       也让删除请求变得尴尬（用户要求清空历史 vs 审计留痕）；</li>
 *   <li><b>整存整取</b>——一个会话一个键、存 JSON 数组，而不是用 Redis List 逐条 rpush。
 *       会话只有几十条消息，读写都是整体操作，简单且不会出现半截状态；</li>
 *   <li><b>Redis 故障不阻断对话</b>——读不到历史就当作新会话，写不进去也不报错。
 *       「记不住上文」是可以接受的降级，「不能聊天」不是。</li>
 * </ol>
 *
 * @author sorts
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConversationStore {

    private static final String KEY_PREFIX = "sorts:ai:chat";

    /** 会话有效期：半天。再久用户自己也不记得上下文了，留着只占内存 */
    private static final Duration TTL = Duration.ofHours(12);

    private final StringRedisTemplate redisTemplate;

    private final ToolJsonCodec jsonCodec;

    /** 读取历史（按时间正序）；无历史或 Redis 异常时返回空列表 */
    public List<ChatMessage> load(Long userId, String conversationId) {
        String key = key(userId, conversationId);
        try {
            String payload = redisTemplate.opsForValue().get(key);
            if (payload == null || payload.isBlank()) {
                return new ArrayList<>();
            }
            ChatMessage[] messages = jsonCodec.mapper().readValue(payload, ChatMessage[].class);
            return new ArrayList<>(List.of(messages));
        } catch (JsonProcessingException e) {
            // 数据损坏（例如协议升级导致结构不兼容）：丢弃旧上下文而不是让整个会话报错
            log.warn("对话上下文反序列化失败，已丢弃。key={}", key, e);
            return new ArrayList<>();
        } catch (Exception e) {
            log.warn("读取对话上下文失败，按新会话处理。key={}", key, e);
            return new ArrayList<>();
        }
    }

    /**
     * 覆盖写入历史。
     *
     * @param messages 完整消息序列（调用方负责裁剪到 historyLimit 之内）
     */
    public void save(Long userId, String conversationId, List<ChatMessage> messages) {
        String key = key(userId, conversationId);
        try {
            redisTemplate.opsForValue().set(key, jsonCodec.write(messages), TTL);
        } catch (Exception e) {
            log.warn("写入对话上下文失败，本轮对话不受影响。key={}", key, e);
        }
    }

    /** 清空会话 */
    public void clear(Long userId, String conversationId) {
        try {
            redisTemplate.delete(key(userId, conversationId));
        } catch (Exception e) {
            log.warn("清除对话上下文失败。key={}", key(userId, conversationId), e);
        }
    }

    private String key(Long userId, String conversationId) {
        return KEY_PREFIX + ":" + userId + ":" + conversationId;
    }
}
