package com.sorts.ai;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * AI 服务启动类（梭灵）。
 *
 * @author sorts
 */
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.sorts.ai.client")
@MapperScan("com.sorts.ai.mapper")
@SpringBootApplication
public class AiApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiApplication.class, args);
    }
}
