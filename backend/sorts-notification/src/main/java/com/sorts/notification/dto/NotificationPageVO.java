package com.sorts.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 通知分页结果。
 *
 * <p>在 {@code PageData} 之外额外带 {@code unreadCount}：未读角标与列表查询
 * 天然同期发生，前端若为此再打一个请求，既多一次往返也可能与列表不一致。</p>
 *
 * @author sorts
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationPageVO {

    /** 当前页数据（沿用本项目分页字段名 list，api-spec 中的 records 见已知偏差说明） */
    private List<NotificationVO> list;

    private long total;

    private long page;

    private long pageSize;

    /** 未读总数（不受当前筛选条件影响，始终是全部未读） */
    private long unreadCount;
}
