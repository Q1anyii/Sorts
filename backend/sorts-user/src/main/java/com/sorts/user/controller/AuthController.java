package com.sorts.user.controller;

import com.sorts.common.constant.AuthConstants;
import com.sorts.common.result.Result;
import com.sorts.user.dto.LoginRequest;
import com.sorts.user.dto.RefreshRequest;
import com.sorts.user.dto.RegisterRequest;
import com.sorts.user.dto.TokenVO;
import com.sorts.user.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口（白名单路径，由网关直接放行）。
 *
 * @author sorts
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public Result<TokenVO> register(@Valid @RequestBody RegisterRequest request) {
        return Result.success(authService.register(request));
    }

    @PostMapping("/login")
    public Result<TokenVO> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(authService.login(request));
    }

    /** 无感续期：用 refresh token 换一对新令牌 */
    @PostMapping("/refresh")
    public Result<TokenVO> refresh(@Valid @RequestBody RefreshRequest request) {
        return Result.success(authService.refresh(request.getRefreshToken()));
    }

    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader(value = AuthConstants.HEADER_USER_ID, required = false) Long userId) {
        if (userId != null) {
            authService.logout(userId);
        }
        return Result.success();
    }
}
