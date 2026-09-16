package com.sorts.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * 梭灵（AI）配置，前缀 {@code sorts.ai}。
 *
 * <p>模型走 DeepSeek 的 OpenAI 兼容接口，因此 base-url / model 均可指向任意同协议服务
 * （例如本地 vLLM、One-API 网关），切换只改配置、不改代码。</p>
 *
 * @author sorts
 */
@Data
@Component
@ConfigurationProperties(prefix = "sorts.ai")
public class DeepSeekProperties {

    /** OpenAI 兼容入口，DeepSeek 官方为 https://api.deepseek.com/v1 */
    private String baseUrl = "https://api.deepseek.com/v1";

    /** API Key，生产环境通过环境变量 DEEPSEEK_API_KEY 注入 */
    private String apiKey;

    /** 模型名：deepseek-chat（通用）/ deepseek-reasoner（推理） */
    private String model = "deepseek-chat";

    private Double temperature = 0.7D;

    private Integer maxTokens = 2048;

    private Duration connectTimeout = Duration.ofSeconds(10);

    /** 单次对话请求超时：流式响应期间是「读间隔」上限 */
    private Duration requestTimeout = Duration.ofMinutes(2);

    /** 工具调用最多来回轮数，防止模型反复调工具打转 */
    private int maxToolRounds = 4;

    /** 多轮对话上下文保留的消息条数 */
    private int historyLimit = 20;

    private Tool tool = new Tool();

    /**
     * 是否已完成密钥配置。
     *
     * <p>未配置时不抛启动异常，而是由接口层返回可读的 503 提示——
     * 这样没有密钥也能启动服务、跑通单测与其它模块的联调。</p>
     */
    public boolean isConfigured() {
        return StringUtils.hasText(apiKey);
    }

    /** 工具集开关 */
    @Data
    public static class Tool {

        /**
         * 是否允许 AI 直接写入数据（创建日程）。
         *
         * <p>默认关闭：写操作是「双钥匙」——配置为 true 且单次请求显式带
         * {@code allowWrite=true}（前端弹确认框后置位）才真正执行。</p>
         */
        private boolean allowWrite = false;
    }
}
