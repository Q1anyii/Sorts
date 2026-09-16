package com.sorts.ai.tool.support;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sorts.common.exception.BizException;
import com.sorts.common.result.ErrorCode;
import org.springframework.stereotype.Component;

/**
 * 工具层的 JSON 编解码器（刻意与全局 ObjectMapper 分离）。
 *
 * <p>原因：回灌给模型的观察结果里带 {@code LocalDateTime}/{@code LocalDate}。
 * 若复用全局 mapper，序列化格式就取决于「当时谁改过 {@code spring.jackson.*}」——
 * 一旦有人打开 {@code write-dates-as-timestamps}，模型看到的会变成
 * {@code [2026,9,16,9,0]} 这种数组，它会当成普通数组照抄进回答里，而且没有任何报错。
 * 这是一类「不崩溃但结果变错」的故障，必须在源头掐掉：
 * 工具输出的时间<b>固定为 ISO-8601 字符串</b>，与模型训练数据里的写法一致。</p>
 *
 * @author sorts
 */
@Component
public final class ToolJsonCodec {

    private final ObjectMapper mapper;

    public ToolJsonCodec() {
        this.mapper = create();
    }

    /** 统一配置：ISO 时间、忽略 null、保留字段插入顺序 */
    public static ObjectMapper create() {
        return JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .serializationInclusion(JsonInclude.Include.NON_NULL)
                .build();
    }

    /** 序列化观察结果 */
    public String write(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "工具结果序列化失败");
        }
    }

    /** 解析模型给的参数 */
    public JsonNode readTree(String raw) throws JsonProcessingException {
        return mapper.readTree(raw);
    }

    public ObjectNode newObject() {
        return mapper.createObjectNode();
    }

    /** 底层 mapper，供单元测试读取输出树 */
    public ObjectMapper mapper() {
        return mapper;
    }
}
