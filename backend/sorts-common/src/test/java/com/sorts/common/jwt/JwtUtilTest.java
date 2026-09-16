package com.sorts.common.jwt;

import com.sorts.common.constant.AuthConstants;
import com.sorts.common.exception.BizException;
import com.sorts.common.result.ErrorCode;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * JwtUtil 单元测试：覆盖双令牌签发、类型校验、过期与非法令牌等场景。
 *
 * @author sorts
 */
class JwtUtilTest {

    /** HS256 要求密钥 ≥ 32 字节 */
    private static final String SECRET = "sorts-unit-test-secret-key-0123456789abcdef";

    private JwtUtil jwtUtil() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(SECRET);
        properties.setAccessExpireMinutes(30L);
        properties.setRefreshExpireDays(7L);
        return new JwtUtil(properties);
    }

    @Test
    @DisplayName("access token 签发后可解析出用户 ID 与类型")
    void shouldParseAccessToken() {
        JwtUtil jwtUtil = jwtUtil();
        String token = jwtUtil.generateAccessToken(1001L, "织梭者");

        Claims claims = jwtUtil.parse(token, AuthConstants.TYPE_ACCESS);
        assertEquals("1001", claims.getSubject());
        assertEquals(AuthConstants.TYPE_ACCESS, claims.get(AuthConstants.CLAIM_TYPE, String.class));
        assertEquals("织梭者", claims.get(AuthConstants.CLAIM_USERNAME, String.class));
        assertEquals(Long.valueOf(1001L), jwtUtil.getUserId(token));
    }

    @Test
    @DisplayName("refresh token 不能被当作 access token 使用")
    void refreshTokenShouldNotBeAcceptedAsAccess() {
        JwtUtil jwtUtil = jwtUtil();
        String refreshToken = jwtUtil.generateRefreshToken(1002L);

        // 自身类型校验通过
        assertEquals("1002", jwtUtil.parse(refreshToken, AuthConstants.TYPE_REFRESH).getSubject());

        BizException exception = assertThrows(BizException.class,
                () -> jwtUtil.parse(refreshToken, AuthConstants.TYPE_ACCESS));
        assertEquals(ErrorCode.TOKEN_INVALID.getCode(), exception.getCode());
    }

    @Test
    @DisplayName("过期令牌解析时抛出 TOKEN_EXPIRED")
    void expiredTokenShouldThrowTokenExpired() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(SECRET);
        // 负有效期 → 立即过期
        properties.setAccessExpireMinutes(-1L);
        JwtUtil jwtUtil = new JwtUtil(properties);

        String expiredToken = jwtUtil.generateAccessToken(1003L, "tester");
        BizException exception = assertThrows(BizException.class, () -> jwtUtil.parse(expiredToken));
        assertEquals(ErrorCode.TOKEN_EXPIRED.getCode(), exception.getCode());
    }

    @Test
    @DisplayName("非法或空令牌解析时抛出 TOKEN_INVALID")
    void invalidTokenShouldThrowTokenInvalid() {
        JwtUtil jwtUtil = jwtUtil();

        assertThrows(BizException.class, () -> jwtUtil.parse(null));
        assertThrows(BizException.class, () -> jwtUtil.parse(""));
        assertThrows(BizException.class, () -> jwtUtil.parse("not-a-jwt"));

        // 使用不同密钥签发的令牌，应当校验失败
        JwtProperties otherProps = new JwtProperties();
        otherProps.setSecret("another-secret-key-for-negative-test-0123456789");
        String foreignToken = new JwtUtil(otherProps).generateAccessToken(1L, "other");
        assertThrows(BizException.class, () -> jwtUtil.parse(foreignToken));
    }

    @Test
    @DisplayName("双令牌有效期应不同：refresh 远长于 access")
    void refreshTokenShouldHaveLongerLifetime() {
        JwtUtil jwtUtil = jwtUtil();
        Claims access = jwtUtil.parse(jwtUtil.generateAccessToken(1L, "a"));
        Claims refresh = jwtUtil.parse(jwtUtil.generateRefreshToken(1L));

        assertNotNull(access.getExpiration());
        assertNotNull(refresh.getExpiration());
        assertEquals(true, refresh.getExpiration().after(access.getExpiration()));
    }

    @Test
    @DisplayName("密钥长度不足时构造 JwtUtil 应直接失败")
    void shortSecretShouldBeRejected() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("too-short");
        assertThrows(IllegalArgumentException.class, () -> new JwtUtil(properties));
    }
}
