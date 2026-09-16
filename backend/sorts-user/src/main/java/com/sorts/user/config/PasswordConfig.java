package com.sorts.user.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 密码加密配置。
 *
 * <p>只引入 Spring Security 的 crypto 模块做 BCrypt 哈希，
 * 鉴权全部收敛在网关，业务服务因此无需引入完整的 Security 过滤器链。</p>
 *
 * @author sorts
 */
@Configuration
public class PasswordConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
