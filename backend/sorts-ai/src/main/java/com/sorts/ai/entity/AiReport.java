package com.sorts.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 周期总结报告（日 / 月 / 年）。
 *
 * <p>设计要点：报告是「快照」而不是「视图」——生成时的完成率与时长一并写入，
 * 之后用户补录了旧日程也不会改动历史报告。用户需要的是「当时 AI 说了什么」，
 * 而不是「用今天的口径重算昨天」。这也是它必须落库、不能实时计算的原因。</p>
 *
 * @author sorts
 */
@Data
@TableName("t_ai_report")
public class AiReport {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** DAILY / WEEKLY / MONTHLY / YEARLY */
    private String type;

    /** 周期标识：日 2026-09-16、月 2026-09、年 2026 */
    private String periodKey;

    private String title;

    /** Markdown 正文 */
    private String content;

    /** 完成率 0-1 */
    private Double completionRate;

    /** 总专注时长（秒） */
    private Integer totalFocusTime;

    /** 亮点，JSON 数组字符串 */
    private String highlights;

    /** 改进建议，JSON 数组字符串 */
    private String suggestions;

    /** GENERATING / COMPLETED / FAILED */
    private String status;

    private String errorMsg;

    private LocalDateTime generatedAt;

    /** 逻辑删除：0否 1是（全局配置生效，无需注解） */
    private Integer deleted;

    /** 逻辑删除时间（删除操作写入，配合 deleted 做审计） */
    private LocalDateTime deletedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
