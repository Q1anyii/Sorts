package com.sorts.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sorts.common.exception.BizException;
import com.sorts.common.jwt.JwtUtil;
import com.sorts.common.result.ErrorCode;
import com.sorts.user.dto.LoginRequest;
import com.sorts.user.dto.RegisterRequest;
import com.sorts.user.dto.TokenVO;
import com.sorts.user.dto.UserVO;
import com.sorts.user.entity.User;
import com.sorts.user.mapper.UserMapper;
import com.sorts.user.repository.RefreshTokenStore;
import com.sorts.user.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 认证服务实现。
 *
 * <p>双令牌无感续期流程：</p>
 * <ol>
 *   <li>登录 / 注册签发 access（30 分钟）+ refresh（7 天），refresh 存 Redis；</li>
 *   <li>access 过期后前端携 refresh 调 /auth/refresh；</li>
 *   <li>服务端校验类型 + Redis 留存值，通过后轮换令牌（旧 refresh 立即失效）。</li>
 * </ol>
 *
 * @author sorts
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;

    private final JwtUtil jwtUtil;

    private final RefreshTokenStore refreshTokenStore;

    private final PasswordEncoder passwordEncoder;

    @Override
    public TokenVO register(RegisterRequest request) {
        Long exists = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, request.getUsername()));
        if (exists != null && exists > 0) {
            throw new BizException(ErrorCode.CONFLICT, "该用户名已被占用");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        // 昵称缺省时回退为用户名，避免前端处处判空
        user.setNickname(StringUtils.hasText(request.getNickname())
                ? request.getNickname()
                : request.getUsername());
        user.setPoints(0);
        user.setStatus(1);
        user.setDeleted(0);
        userMapper.insert(user);

        log.info("用户注册成功 userId={}, username={}", user.getId(), user.getUsername());
        return issueTokens(user);
    }

    @Override
    public TokenVO login(LoginRequest request) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, request.getUsername()));
        // 用户不存在与密码错误返回同一提示，避免暴露用户名是否注册
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }
        if (Integer.valueOf(0).equals(user.getStatus())) {
            throw new BizException(ErrorCode.FORBIDDEN, "账号已被禁用");
        }
        return issueTokens(user);
    }

    @Override
    public TokenVO refresh(String refreshToken) {
        Long userId = jwtUtil.getUserId(refreshToken);
        // 服务端留存校验：防止令牌伪造，或已轮换的旧令牌被重复使用
        if (!refreshTokenStore.matches(userId, refreshToken)) {
            throw new BizException(ErrorCode.REFRESH_TOKEN_INVALID);
        }

        User user = userMapper.selectById(userId);
        if (user == null || Integer.valueOf(1).equals(user.getDeleted())) {
            throw new BizException(ErrorCode.REFRESH_TOKEN_INVALID);
        }

        // 令牌轮换：先失效旧的，再签发新的
        refreshTokenStore.delete(userId);
        return issueTokens(user);
    }

    @Override
    public void logout(Long userId) {
        refreshTokenStore.delete(userId);
    }

    /** 统一签发令牌对：access 访问用，refresh 续期用 */
    private TokenVO issueTokens(User user) {
        String accessToken = jwtUtil.generateAccessToken(user.getId(), user.getUsername());
        String refreshToken = jwtUtil.generateRefreshToken(user.getId());
        refreshTokenStore.save(user.getId(), refreshToken, jwtUtil.getProperties().getRefreshExpireDays());

        return TokenVO.builder()
                // 契约（api-spec.json）：accessToken 为裸 JWT，Bearer 前缀由调用方添加，
                // 若在此拼前缀，前端会二次拼接 "Bearer Bearer ..." 被网关按 40102 拒绝
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(jwtUtil.getProperties().getAccessExpireMinutes() * 60)
                .user(UserVO.from(user))
                .build();
    }
}
