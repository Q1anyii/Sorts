package com.sorts.mall.client.dto;

import lombok.Data;

/**
 * 内部创建通知指令（与 sorts-notification 的 /internal/notifications 契约对应）。
 *
 * @author sorts
 */
@Data
public class CreateNotificationCommand {

    /** 接收用户 */
    private Long userId;

    /** REMINDER/SUMMARY/SYSTEM/PROMOTION */
    private String type;

    private String title;

    private String content;

    /** 关联业务 ID（商城场景传商品 ID） */
    private Long relatedId;
}
