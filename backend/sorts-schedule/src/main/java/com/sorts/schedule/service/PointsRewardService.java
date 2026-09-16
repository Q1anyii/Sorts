package com.sorts.schedule.service;

import com.sorts.schedule.client.UserPointsClient;
import com.sorts.schedule.config.RewardProperties;
import com.sorts.schedule.dto.PointsChangeCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 光阴砂奖励发放。
 *
 * <p>设计取舍：当前采用「同步 Feign + 失败降级为告警」。
 * 主流程（落梭）不因奖励失败而回滚，后续 M5 引入 RabbitMQ 后，
 * 这里只需改为投递事件，调用方无需改动。</p>
 *
 * @author sorts
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PointsRewardService {

    private final UserPointsClient userPointsClient;

    private final RewardProperties rewardProperties;

    /** 日程落梭后发放奖励 */
    public void rewardOnComplete(Long userId, Long scheduleId) {
        if (!rewardProperties.isEnabled()) {
            return;
        }
        int points = rewardProperties.getCompletePoints();
        if (points <= 0) {
            return;
        }
        try {
            userPointsClient.changePoints(userId, new PointsChangeCommand(points, "落梭奖励", scheduleId));
            log.info("落梭奖励已发放 userId={}, scheduleId={}, points={}", userId, scheduleId, points);
        } catch (Exception e) {
            // 奖励失败不影响日程状态，记录告警待后续补偿
            log.warn("落梭奖励发放失败，待补偿 userId={}, scheduleId={}", userId, scheduleId, e);
        }
    }
}
