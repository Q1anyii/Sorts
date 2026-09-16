package com.sorts.notification.dto;

import com.sorts.notification.entity.Notification;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通知视图对象（字段与 api-spec.json 的 Notification 对齐）。
 *
 * @author sorts
 */
@Data
public class NotificationVO {

    private Long id;

    private Long userId;

    private String type;

    private String title;

    private String content;

    /** 对外用布尔语义，库表用 TINYINT，转换收口在这里 */
    private Boolean isRead;

    private Long relatedId;

    private LocalDateTime createdAt;

    public static NotificationVO from(Notification notification) {
        if (notification == null) {
            return null;
        }
        NotificationVO vo = new NotificationVO();
        vo.setId(notification.getId());
        vo.setUserId(notification.getUserId());
        vo.setType(notification.getType());
        vo.setTitle(notification.getTitle());
        vo.setContent(notification.getContent());
        vo.setIsRead(Integer.valueOf(1).equals(notification.getIsRead()));
        vo.setRelatedId(notification.getRelatedId());
        vo.setCreatedAt(notification.getCreatedAt());
        return vo;
    }
}
