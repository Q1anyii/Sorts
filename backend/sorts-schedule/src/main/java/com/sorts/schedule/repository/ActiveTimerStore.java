package com.sorts.schedule.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

/**
 * 进行中计时状态存储（Redis）。
 *
 * <p>为什么需要它：日程的最终时长落在 MySQL，但「当前这一段时间从什么时候开始」
 * 属于高频变动的活跃状态。放 Redis 可避免频繁写库，并在服务重启后仍能恢复计时。</p>
 *
 * <p>存储结构：</p>
 * <ul>
 *   <li>{@code sorts:timer:segment:{scheduleId}} → 当前片段开始时间（epoch 毫秒）</li>
 *   <li>{@code sorts:timer:active:{userId}} → 当前进行中的日程 ID（每人同时只允许一个）</li>
 * </ul>
 *
 * @author sorts
 */
@Repository
@RequiredArgsConstructor
public class ActiveTimerStore {

    private static final String SEGMENT_KEY_PREFIX = "sorts:timer:segment:";

    private static final String ACTIVE_KEY_PREFIX = "sorts:timer:active:";

    /** 兜底过期时间：避免异常退出后残留脏键 */
    private static final Duration TTL = Duration.ofHours(24);

    private final StringRedisTemplate stringRedisTemplate;

    /** 标记某个日程开始计时段 */
    public void startSegment(Long userId, Long scheduleId, long startEpochMilli) {
        stringRedisTemplate.opsForValue()
                .set(segmentKey(scheduleId), String.valueOf(startEpochMilli), TTL);
        stringRedisTemplate.opsForValue()
                .set(activeKey(userId), String.valueOf(scheduleId), TTL);
    }

    /** 读取指定日程当前片段的开始时间 */
    public Optional<Long> segmentStart(Long scheduleId) {
        String value = stringRedisTemplate.opsForValue().get(segmentKey(scheduleId));
        return value == null || value.isBlank()
                ? Optional.empty()
                : Optional.of(Long.parseLong(value));
    }

    /** 读取用户当前进行中的日程 ID */
    public Optional<Long> activeScheduleId(Long userId) {
        String value = stringRedisTemplate.opsForValue().get(activeKey(userId));
        return value == null || value.isBlank()
                ? Optional.empty()
                : Optional.of(Long.parseLong(value));
    }

    /** 清除计时状态（暂停 / 结束 / 取消时调用） */
    public void clearSegment(Long userId, Long scheduleId) {
        stringRedisTemplate.delete(segmentKey(scheduleId));
        stringRedisTemplate.delete(activeKey(userId));
    }

    private String segmentKey(Long scheduleId) {
        return SEGMENT_KEY_PREFIX + scheduleId;
    }

    private String activeKey(Long userId) {
        return ACTIVE_KEY_PREFIX + userId;
    }
}
