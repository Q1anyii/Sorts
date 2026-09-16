package com.sorts.mall.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 商城服务配置项（前缀 sorts.mall）。
 *
 * @author sorts
 */
@Data
@ConfigurationProperties(prefix = "sorts.mall")
public class MallProperties {

    /** 购买时获取分布式锁的最长等待秒数 */
    private long lockWaitSeconds = 3;

    /** 分布式锁的持有时间（秒）；必须大于本地事务的预期耗时 */
    private long lockLeaseSeconds = 10;

    /** 购买成功后是否投递通知（联调时可关闭，避免刷屏） */
    private boolean purchaseNotify = true;
}
