package com.sorts.notification.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通知实体（对应 sorts_notification 库 t_notification 表）。
 *
 * @author sorts
 */
@Data
@TableName("t_notification")
public class Notification {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 见 NotificationType */
    private String type;

    private String title;

    private String content;

    /** 是否已读：0 未读 / 1 已读（库表用 TINYINT，避免 Boolean 映射歧义） */
    private Integer isRead;

    /** 关联业务 ID（如日程 ID），便于前端跳转 */
    private Long relatedId;

    private Integer deleted;

    private LocalDateTime createdAt;
}
