package com.sorts.ai.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置。
 *
 * <p>分页插件必须显式注册，否则 {@code selectPage} 不会拼 LIMIT，会退化成全表查询。</p>
 *
 * @author sorts
 */
@Configuration
public class MybatisPlusConfig {

    /** 单页最大条数，与业务层的归一逻辑构成双保险 */
    private static final long MAX_LIMIT = 200L;

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        pagination.setMaxLimit(MAX_LIMIT);
        interceptor.addInnerInterceptor(pagination);
        return interceptor;
    }
}
