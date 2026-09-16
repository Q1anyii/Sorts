package com.sorts.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sorts.common.exception.BizException;
import com.sorts.common.jwt.JwtProperties;
import com.sorts.common.jwt.JwtUtil;
import com.sorts.common.result.ErrorCode;
import com.sorts.user.dto.LoginRequest;
import com.sorts.user.dto.RegisterRequest;
import com.sorts.user.dto.TokenVO;
import com.sorts.user.entity.User;
import com.sorts.user.mapper.UserMapper;
import com.sorts.user.repository.RefreshTokenStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 认证服务单元测试（不依赖 Spring 容器与中间件）。
 *
 * @author sorts
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceImplTest {

    private static final String SECRET = "sorts-user-test-secret-key-0123456789abcdef";

    @Mock
    private UserMapper userMapper;

    @Mock
    private RefreshTokenStore refreshTokenStore;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @InjectMocks
    private AuthServiceImpl authService;

    /** JwtUtil 为具体类，使用真实实现 + 自注入（避免 mock 令牌逻辑） */
    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(SECRET);
        properties.setAccessExpireMinutes(30L);
        properties.setRefreshExpireDays(7L);
        jwtUtil = new JwtUtil(properties);
        authService = new AuthServiceImpl(userMapper, jwtUtil, refreshTokenStore, passwordEncoder);
    }

    private User buildUser(Long id, String username, String rawPassword) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setNickname(username);
        user.setPoints(10);
        user.setStatus(1);
        user.setDeleted(0);
        return user;
    }

    @Test
    @DisplayName("注册成功：密码加密落库并返回令牌对")
    void registerShouldReturnTokens() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("织梭者");
        request.setPassword("secret123");
        request.setEmail("weaver@sorts.com");

        when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        // 用 AtomicReference 捕获落库实体，规避 insert(T) / insert(Collection<T>) 重载歧义
        AtomicReference<User> savedRef = new AtomicReference<>();
        when(userMapper.insert(ArgumentMatchers.<User>any())).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(100L);
            savedRef.set(u);
            return 1;
        });

        TokenVO token = authService.register(request);

        assertNotNull(token.getAccessToken());
        assertNotNull(token.getRefreshToken());
        assertEquals(1800L, token.getExpiresIn());
        assertEquals(100L, token.getUser().getId());
        // 落库密码必须是密文，不能等于明文
        assertNotNull(savedRef.get());
        assertNotNull(savedRef.get().getPassword());
        assertTrue(!"secret123".equals(savedRef.get().getPassword()));
        verify(refreshTokenStore).save(eq(100L), any(), eq(7L));
    }

    @Test
    @DisplayName("用户名重复时注册失败")
    void registerWithDuplicateUsernameShouldFail() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("duplicate");
        request.setPassword("secret123");

        when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        BizException exception = assertThrows(BizException.class, () -> authService.register(request));
        assertEquals(ErrorCode.CONFLICT.getCode(), exception.getCode());
        verify(userMapper, never()).insert(ArgumentMatchers.<User>any());
    }

    @Test
    @DisplayName("登录成功返回令牌，密码正确才通过")
    void loginShouldSucceedWithCorrectPassword() {
        User user = buildUser(7L, "weaver", "pass1234");
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);

        LoginRequest request = new LoginRequest();
        request.setUsername("weaver");
        request.setPassword("pass1234");

        TokenVO token = authService.login(request);
        assertEquals("weaver", token.getUser().getUsername());
        assertTrue(token.getAccessToken().startsWith("Bearer "));
    }

    @Test
    @DisplayName("密码错误/用户不存在时返回相同提示（避免用户名枚举）")
    void loginShouldFailWithWrongPassword() {
        LoginRequest request = new LoginRequest();
        request.setUsername("weaver");
        request.setPassword("wrong-pass");

        when(userMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(buildUser(7L, "weaver", "pass1234"));

        BizException exception = assertThrows(BizException.class, () -> authService.login(request));
        assertEquals(ErrorCode.UNAUTHORIZED.getCode(), exception.getCode());
        assertEquals("用户名或密码错误", exception.getMessage());
    }

    @Test
    @DisplayName("刷新令牌：校验通过后轮换，旧 refresh 失效")
    void refreshShouldRotateToken() {
        User user = buildUser(7L, "weaver", "pass1234");
        String oldRefresh = jwtUtil.generateRefreshToken(7L);

        when(refreshTokenStore.matches(7L, oldRefresh)).thenReturn(true);
        when(userMapper.selectById(7L)).thenReturn(user);
        doNothing().when(refreshTokenStore).delete(7L);

        TokenVO token = authService.refresh(oldRefresh);

        assertNotNull(token.getAccessToken());
        assertNotNull(token.getRefreshToken());
        // 轮换：先删旧令牌，再保存新令牌
        verify(refreshTokenStore).delete(7L);
        verify(refreshTokenStore).save(eq(7L), any(), eq(7L));
    }

    @Test
    @DisplayName("refresh token 与服务端留存不一致时拒绝续期（防重放）")
    void refreshShouldRejectMismatchedToken() {
        String refreshToken = jwtUtil.generateRefreshToken(7L);
        when(refreshTokenStore.matches(7L, refreshToken)).thenReturn(false);

        BizException exception = assertThrows(BizException.class, () -> authService.refresh(refreshToken));
        assertEquals(ErrorCode.REFRESH_TOKEN_INVALID.getCode(), exception.getCode());
        verify(refreshTokenStore, never()).delete(anyLong());
    }

    @Test
    @DisplayName("使用 access token 冒充 refresh token 续期应被拒绝")
    void refreshShouldRejectAccessToken() {
        String accessToken = jwtUtil.generateAccessToken(7L, "weaver");
        assertThrows(BizException.class, () -> authService.refresh(accessToken));
    }

    @Test
    @DisplayName("登出：清除服务端 refresh token")
    void logoutShouldDeleteRefreshToken() {
        doNothing().when(refreshTokenStore).delete(7L);
        authService.logout(7L);
        verify(refreshTokenStore).delete(7L);
    }
}
