package com.sorts.notification;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 通知服务启动类。
 *
 * <p>开启 {@code @EnableScheduling}：定时提醒依赖调度线程驱动，
 * 放在启动类上而非某个配置类，避免「本地跑单测时不知道谁开了定时器」。</p>
 *
 * @author sorts
 */
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.sorts.notification.client")
@EnableScheduling
@MapperScan("com.sorts.notification.mapper")
@SpringBootApplication
public class NotificationApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationApplication.class, args);
    }
}
