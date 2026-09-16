package com.sorts.user.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;

/**
 * refresh token 存储（Redis）。
 *
 * <p>把 Redis 操作收敛到此类，业务层只面向「保存 / 校验 / 删除」三个语义，
 * 便于单测时整体替换，也方便后续改为多端设备管理（key 追加设备维度）。</p>
 *
 * @author sorts
 */
@Repository
@RequiredArgsConstructor
public class RefreshTokenStore {

    private static final String KEY_PREFIX = "sorts:auth:refresh:";

    private final StringRedisTemplate stringRedisTemplate;

    /** 保存（覆盖式），并设置与 refresh token 一致的过期时间 */
    public void save(Long userId, String token, long expireDays) {
        stringRedisTemplate.opsForValue()
                .set(key(userId), token, Duration.ofDays(expireDays));
    }

    /** 校验服务端留存的令牌是否与传入一致（防止令牌伪造 / 已轮换的旧令牌复用） */
    public boolean matches(Long userId, String token) {
        if (userId == null || token == null || token.isBlank()) {
            return false;
        }
        return token.equals(stringRedisTemplate.opsForValue().get(key(userId)));
    }

    public void delete(Long userId) {
        stringRedisTemplate.delete(key(userId));
    }

    private String key(Long userId) {
        return KEY_PREFIX + userId;
    }
}
