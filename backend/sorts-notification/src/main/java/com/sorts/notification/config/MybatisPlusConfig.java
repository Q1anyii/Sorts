package com.sorts.notification.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 与自定义配置装配。
 *
 * <p>分页插件必须显式注册，否则 {@code selectPage} 不拼 LIMIT 会退化为全表查询。</p>
 *
 * @author sorts
 */
@Configuration
@EnableConfigurationProperties(NotificationProperties.class)
public class MybatisPlusConfig {

    /** 单页上限，防止前端传入超大 pageSize 拖垮数据库 */
    private static final long MAX_LIMIT = 200L;

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        pagination.setMaxLimit(MAX_LIMIT);
        pagination.setOverflow(false);
        interceptor.addInnerInterceptor(pagination);
        return interceptor;
    }
}
