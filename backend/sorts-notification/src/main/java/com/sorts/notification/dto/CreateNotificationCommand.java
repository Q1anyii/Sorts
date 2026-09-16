package com.sorts.notification.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 内部创建通知指令（服务间契约，与 sorts-mall 等调用方约定的 JSON 字段一致）。
 *
 * @author sorts
 */
@Data
public class CreateNotificationCommand {

    /** 接收用户；缺省表示由调用方通过 X-User-Id 指定 */
    private Long userId;

    /** 类型字符串，未知值按 SYSTEM 降级 */
    private String type;

    @NotBlank(message = "通知标题不能为空")
    private String title;

    private String content;

    /** 关联业务 ID（如日程 ID / 商品 ID / 报告 ID） */
    private Long relatedId;
}
