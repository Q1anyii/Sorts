package com.sorts.notification.dto;

import lombok.Data;

/**
 * 通知列表查询条件（与 api-spec.json 的 GET /notifications 查询参数一致）。
 *
 * @author sorts
 */
@Data
public class NotificationQuery {

    /** 通知类型筛选，空表示全部 */
    private String type;

    /** 已读筛选：null 全部 / true 已读 / false 未读 */
    private Boolean isRead;

    private int page = 1;

    private int pageSize = 20;
}
