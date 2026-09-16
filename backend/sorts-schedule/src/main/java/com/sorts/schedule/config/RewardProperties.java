package com.sorts.schedule.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 光阴砂奖励规则（前缀 sorts.reward）。
 *
 * @author sorts
 */
@Data
@Component
@ConfigurationProperties(prefix = "sorts.reward")
public class RewardProperties {

    /** 完成日程是否发放奖励 */
    private boolean enabled = true;

    /** 完成一个日程奖励的光阴砂数量 */
    private int completePoints = 5;
}
