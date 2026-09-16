package com.sorts.ai.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sorts.ai.config.DeepSeekProperties;
import com.sorts.ai.llm.protocol.ChatChunk;
import com.sorts.ai.llm.protocol.ChatCompletionRequest;
import com.sorts.ai.llm.protocol.ChatCompletionResponse;
import com.sorts.ai.llm.protocol.ChatMessage;
import com.sorts.ai.llm.protocol.LlmResult;
import com.sorts.ai.llm.protocol.ToolCall;
import com.sorts.common.exception.BizException;
import com.sorts.common.result.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * DeepSeek（OpenAI 兼容协议）对话客户端。
 *
 * <p>为什么手写而不是用 Spring AI：见 {@code sorts-ai/pom.xml} 顶部注释——
 * spring-ai 1.0.0 锁定 Boot 3.4.5，与本项目的 Boot 3.3.4 不兼容。本类只用 JDK 自带
 * {@link HttpClient}，零额外依赖，协议细节可控可测。</p>
 *
 * <p>职责划分：本类只做「发请求 / 解析响应」；参数归并交给
 * {@link ToolCallAccumulator}，SSE 行解析交给 {@link SseDataParser}，
 * 两者都是纯逻辑、可单独测。</p>
 *
 * @author sorts
 */
@Slf4j
@Component
public class DeepSeekChatClient implements ChatModelClient {

    private static final String CHAT_COMPLETIONS_PATH = "/chat/completions";

    private static final int ERROR_BODY_PREVIEW = 500;

    private final DeepSeekProperties properties;

    private final ObjectMapper objectMapper;

    private final SseDataParser sseDataParser;

    private final HttpClient httpClient;

    private final URI endpoint;

    public DeepSeekChatClient(DeepSeekProperties properties, ObjectMapper objectMapper, SseDataParser sseDataParser) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.sseDataParser = sseDataParser;
        this.endpoint = URI.create(stripTrailingSlash(properties.getBaseUrl()) + CHAT_COMPLETIONS_PATH);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        log.info("梭灵已就绪：endpoint={}, model={}, configured={}",
                this.endpoint, properties.getModel(), properties.isConfigured());
    }

    @Override
    public LlmResult complete(ChatCompletionRequest request) {
        requireConfigured();
        prepare(request);
        request.setStream(Boolean.FALSE);

        HttpRequest httpRequest = baseRequest()
                .header("Accept", "application/json")
                .timeout(properties.getRequestTimeout())
                .POST(HttpRequest.BodyPublishers.ofString(write(request), StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(
                    httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (!isSuccess(response.statusCode())) {
                throw apiError(response.statusCode(), response.body());
            }
            ChatCompletionResponse parsed = objectMapper.readValue(response.body(), ChatCompletionResponse.class);
            return toResult(parsed);
        } catch (IOException e) {
            throw new BizException(ErrorCode.SERVICE_UNAVAILABLE, "梭灵请求失败：" + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException(ErrorCode.SERVICE_UNAVAILABLE, "梭灵请求被中断");
        }
    }

    @Override
    public LlmResult stream(ChatCompletionRequest request, Consumer<String> onContentDelta) {
        requireConfigured();
        prepare(request);
        request.setStream(Boolean.TRUE);
        request.enableStreamUsage();

        HttpRequest httpRequest = baseRequest()
                .header("Accept", "text/event-stream")
                .timeout(properties.getRequestTimeout())
                .POST(HttpRequest.BodyPublishers.ofString(write(request), StandardCharsets.UTF_8))
                .build();

        StringBuilder content = new StringBuilder();
        ToolCallAccumulator accumulator = new ToolCallAccumulator();
        Map<String, Integer> usage = new HashMap<>();
        String[] finishReason = new String[1];

        try {
            HttpResponse<Stream<String>> response = httpClient.send(
                    httpRequest, HttpResponse.BodyHandlers.ofLines());
            if (!isSuccess(response.statusCode())) {
                // 错误响应体也是行流，取前若干行拼起来用于定位
                String preview = response.body().limit(20).collect(Collectors.joining("\n"));
                throw apiError(response.statusCode(), preview);
            }
            try (Stream<String> lines = response.body()) {
                for (String line : (Iterable<String>) lines::iterator) {
                    if (sseDataParser.isDone(line)) {
                        break;
                    }
                    Optional<ChatChunk> parsed = sseDataParser.parseLine(line);
                    if (parsed.isEmpty()) {
                        continue;
                    }
                    consume(parsed.get(), content, accumulator, usage, finishReason, onContentDelta);
                }
            }
        } catch (IOException e) {
            throw new BizException(ErrorCode.SERVICE_UNAVAILABLE, "梭灵流式请求失败：" + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException(ErrorCode.SERVICE_UNAVAILABLE, "梭灵流式请求被中断");
        }

        LlmResult result = LlmResult.builder()
                .content(content.length() == 0 ? null : content.toString())
                .toolCalls(accumulator.toolCalls())
                .finishReason(finishReason[0])
                .usage(usage.isEmpty() ? null : usage)
                .build();
        warnIfTruncated(result);
        return result;
    }

    /** 消费一个 chunk：累积文本（并回调）、累积工具调用、记录结束原因与用量 */
    private void consume(ChatChunk chunk,
                         StringBuilder content,
                         ToolCallAccumulator accumulator,
                         Map<String, Integer> usage,
                         String[] finishReason,
                         Consumer<String> onContentDelta) {
        if (chunk.getUsage() != null) {
            ChatCompletionResponse.Usage u = chunk.getUsage();
            usage.put("promptTokens", u.getPromptTokens() == null ? 0 : u.getPromptTokens());
            usage.put("completionTokens", u.getCompletionTokens() == null ? 0 : u.getCompletionTokens());
            usage.put("totalTokens", u.getTotalTokens() == null ? 0 : u.getTotalTokens());
        }
        ChatChunk.Choice choice = chunk.firstChoice();
        if (choice == null) {
            return;
        }
        ChatChunk.Delta delta = choice.getDelta();
        if (delta != null) {
            if (StringUtils.hasText(delta.getContent())) {
                content.append(delta.getContent());
                if (onContentDelta != null) {
                    onContentDelta.accept(delta.getContent());
                }
            }
            accumulator.accept(delta.getToolCalls());
        }
        if (choice.getFinishReason() != null) {
            finishReason[0] = choice.getFinishReason();
        }
    }

    private LlmResult toResult(ChatCompletionResponse response) {
        ChatCompletionResponse.Choice choice = response.firstChoice();
        if (choice == null) {
            log.warn("梭灵返回了空候选列表 id={}", response.getId());
            return LlmResult.builder().content(null).toolCalls(null).build();
        }
        ChatMessage message = choice.getMessage();
        Map<String, Integer> usage = null;
        if (response.getUsage() != null) {
            usage = new HashMap<>();
            usage.put("promptTokens", nullSafe(response.getUsage().getPromptTokens()));
            usage.put("completionTokens", nullSafe(response.getUsage().getCompletionTokens()));
            usage.put("totalTokens", nullSafe(response.getUsage().getTotalTokens()));
        }
        LlmResult result = LlmResult.builder()
                .content(message == null ? null : message.getContent())
                .toolCalls(message == null ? null : message.getToolCalls())
                .finishReason(choice.getFinishReason())
                .usage(usage)
                .build();
        warnIfTruncated(result);
        return result;
    }

    /** 补齐缺省参数：调用方不传时用配置值 */
    private void prepare(ChatCompletionRequest request) {
        if (!StringUtils.hasText(request.getModel())) {
            request.setModel(properties.getModel());
        }
        if (request.getTemperature() == null) {
            request.setTemperature(properties.getTemperature());
        }
        if (request.getMaxTokens() == null) {
            request.setMaxTokens(properties.getMaxTokens());
        }
    }

    private HttpRequest.Builder baseRequest() {
        return HttpRequest.newBuilder(endpoint)
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Authorization", "Bearer " + properties.getApiKey());
    }

    /** 未配置密钥时给出可读提示，而不是抛 401 让上层猜 */
    private void requireConfigured() {
        if (!properties.isConfigured()) {
            throw new BizException(ErrorCode.SERVICE_UNAVAILABLE,
                    "梭灵尚未配置模型密钥，请设置环境变量 DEEPSEEK_API_KEY 后重启服务");
        }
    }

    private String write(ChatCompletionRequest request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "请求序列化失败：" + e.getMessage());
        }
    }

    private BizException apiError(int status, String body) {
        log.error("梭灵接口异常 status={}, body={}", status, preview(body));
        String hint = switch (status) {
            case 401, 403 -> "模型密钥无效或已过期";
            case 429 -> "模型调用频率超限，请稍后重试";
            case 400 -> "模型请求参数不合法";
            default -> "模型服务返回异常（HTTP " + status + "）";
        };
        return new BizException(ErrorCode.SERVICE_UNAVAILABLE, hint);
    }

    private void warnIfTruncated(LlmResult result) {
        if (result.truncated()) {
            log.warn("梭灵输出被 max_tokens 截断，建议调大 sorts.ai.max-tokens");
        }
    }

    private boolean isSuccess(int status) {
        return status / 100 == 2;
    }

    private int nullSafe(Integer value) {
        return value == null ? 0 : value;
    }

    private String preview(String body) {
        if (body == null) {
            return "";
        }
        return body.length() > ERROR_BODY_PREVIEW ? body.substring(0, ERROR_BODY_PREVIEW) + "..." : body;
    }

    private static String stripTrailingSlash(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        String trimmed = url.strip();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    /** 工具调用参数补全（供上层记录日志用，避免出现 undefined） */
    public static String argumentsOf(ToolCall call) {
        return call.getFunction() == null || call.getFunction().getArguments() == null
                ? "{}"
                : call.getFunction().getArguments();
    }
}
