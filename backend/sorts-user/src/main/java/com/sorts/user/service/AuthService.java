package com.sorts.user.service;

import com.sorts.user.dto.LoginRequest;
import com.sorts.user.dto.RegisterRequest;
import com.sorts.user.dto.TokenVO;

/**
 * 认证服务：注册、登录、令牌刷新与登出。
 *
 * @author sorts
 */
public interface AuthService {

    /** 注册并直接返回令牌，减少一次登录请求 */
    TokenVO register(RegisterRequest request);

    TokenVO login(LoginRequest request);

    /**
     * 使用 refresh token 换取新的令牌对（无感续期）。
     *
     * <p>校验服务端留存的 refresh token，通过后做令牌轮换：
     * 旧令牌立即失效，返回新的 access + refresh。</p>
     */
    TokenVO refresh(String refreshToken);

    /** 登出：失效服务端 refresh token */
    void logout(Long userId);
}
