package com.sorts.schedule;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 日程服务启动类。
 *
 * @author sorts
 */
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.sorts.schedule.client")
@MapperScan("com.sorts.schedule.mapper")
@SpringBootApplication
public class ScheduleApplication {

    public static void main(String[] args) {
        SpringApplication.run(ScheduleApplication.class, args);
    }
}
