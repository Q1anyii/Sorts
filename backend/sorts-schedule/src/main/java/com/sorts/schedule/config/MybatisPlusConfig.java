package com.sorts.schedule.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置。
 *
 * <p>分页插件必须显式注册，否则 {@code selectPage} 不会拼 LIMIT，
 * 会退化为全表查询——这是最常见的隐藏性能坑。</p>
 *
 * @author sorts
 */
@Configuration
public class MybatisPlusConfig {

    /** 单页上限，防止前端传入超大 pageSize 拖垮数据库 */
    private static final long MAX_LIMIT = 200L;

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        pagination.setMaxLimit(MAX_LIMIT);
        // 超出最大页后返回空列表而非首页数据，避免翻页逻辑出现「鬼打墙」
        pagination.setOverflow(false);
        interceptor.addInnerInterceptor(pagination);
        return interceptor;
    }
}
