package com.sorts.common.jwt;

import com.sorts.common.constant.AuthConstants;
import com.sorts.common.exception.BizException;
import com.sorts.common.result.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT 签发与校验工具（基于 jjwt 0.12.x）。
 *
 * <p>双令牌机制：</p>
 * <ul>
 *   <li>access token —— 短时效（默认 30 分钟），访问业务接口时携带；</li>
 *   <li>refresh token —— 长时效（默认 7 天），仅用于换取新的 access token，实现无感续期。</li>
 * </ul>
 *
 * @author sorts
 */
public class JwtUtil {

    private final JwtProperties properties;

    private final SecretKey secretKey;

    public JwtUtil(JwtProperties properties) {
        if (properties == null || properties.getSecret() == null
                || properties.getSecret().getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("sorts.jwt.secret 长度不足 32 字节，无法满足 HS256 签名要求");
        }
        this.properties = properties;
        this.secretKey = Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    /** 签发 access token（载荷含用户 ID、用户名、类型） */
    public String generateAccessToken(Object userId, String username) {
        return buildToken(userId, username, AuthConstants.TYPE_ACCESS,
                properties.getAccessExpireMinutes() * 60 * 1000L);
    }

    /** 签发 refresh token（载荷仅含用户 ID 与类型，不携带业务信息） */
    public String generateRefreshToken(Object userId) {
        return buildToken(userId, null, AuthConstants.TYPE_REFRESH,
                properties.getRefreshExpireDays() * 24 * 60 * 60 * 1000L);
    }

    private String buildToken(Object userId, String username, String type, long ttlMillis) {
        Map<String, Object> claims = new HashMap<>(4);
        claims.put(AuthConstants.CLAIM_TYPE, type);
        if (username != null) {
            claims.put(AuthConstants.CLAIM_USERNAME, username);
        }
        Date now = new Date();
        return Jwts.builder()
                .claims(claims)
                .subject(String.valueOf(userId))
                .issuer(properties.getIssuer())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttlMillis))
                .signWith(secretKey)
                .compact();
    }

    /**
     * 解析并校验令牌。
     *
     * @throws BizException 令牌过期抛 TOKEN_EXPIRED，其它非法情况抛 TOKEN_INVALID
     */
    public Claims parse(String token) {
        if (token == null || token.isBlank()) {
            throw new BizException(ErrorCode.TOKEN_INVALID);
        }
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            throw new BizException(ErrorCode.TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BizException(ErrorCode.TOKEN_INVALID);
        }
    }

    /** 校验令牌类型是否匹配（防止拿 refresh token 直接调业务接口） */
    public Claims parse(String token, String expectedType) {
        Claims claims = parse(token);
        String type = claims.get(AuthConstants.CLAIM_TYPE, String.class);
        if (!expectedType.equals(type)) {
            throw new BizException(ErrorCode.TOKEN_INVALID, "令牌类型不匹配");
        }
        return claims;
    }

    /** 提取用户 ID */
    public Long getUserId(String token) {
        return Long.valueOf(parse(token).getSubject());
    }

    public JwtProperties getProperties() {
        return properties;
    }
}
