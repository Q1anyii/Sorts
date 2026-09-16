package com.sorts.ai.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步执行配置。
 *
 * <p>月度/年度总结是「用户等不起、但必须生成」的任务：模型一次调用要几十秒，
 * 同步返回会把浏览器连接拖到超时。因此接口先落一条 GENERATING 记录并立即返回
 * reportId，真正的生成交给这里的线程池。</p>
 *
 * <p>拒绝策略选 {@code CallerRunsPolicy}：队列满时在调用线程执行，
 * 表现为「这次请求慢一点」而不是「任务被悄悄丢弃、报告永远停在进行中」。
 * 对用户可见的降级，永远好过静默失败。</p>
 *
 * @author sorts
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig {

    public static final String AI_EXECUTOR = "aiTaskExecutor";

    @Bean(AI_EXECUTOR)
    public Executor aiTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(6);
        executor.setQueueCapacity(50);
        executor.setKeepAliveSeconds(120);
        executor.setThreadNamePrefix("sorts-ai-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 优雅停机：等在跑的生成任务收尾，避免留下永远 GENERATING 的记录
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
