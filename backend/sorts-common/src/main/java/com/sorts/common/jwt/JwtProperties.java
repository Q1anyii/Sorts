package com.sorts.common.jwt;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 配置项（前缀 sorts.jwt）。
 *
 * <p>密钥建议通过环境变量 JWT_SECRET 注入，禁止把生产密钥写进仓库。</p>
 *
 * @author sorts
 */
@Data
@ConfigurationProperties(prefix = "sorts.jwt")
public class JwtProperties {

    /** HMAC 签名密钥，长度需 ≥ 32 字节（HS256 要求） */
    private String secret = "sorts-dev-secret-please-override-by-env-jwt-secret";

    /** access token 有效期（分钟），默认 30 分钟 */
    private long accessExpireMinutes = 30L;

    /** refresh token 有效期（天），默认 7 天 */
    private long refreshExpireDays = 7L;

    /** 签发者标识 */
    private String issuer = "sorts";
}
