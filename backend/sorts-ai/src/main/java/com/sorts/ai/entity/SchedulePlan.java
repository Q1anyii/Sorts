package com.sorts.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * AI 日程规划。
 *
 * <p>为什么要落库而不是让前端拿着 JSON 再传回来：规划与采纳可能是两次请求
 * （用户看了建议、犹豫、第二天才点采纳），期间前端刷新就丢了。
 * 落库后只需带 planId，且能对「这个规划是基于当时的什么描述生成的」留痕。</p>
 *
 * @author sorts
 */
@Data
@TableName("t_schedule_plan")
public class SchedulePlan {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 对外规划ID（UUID），采纳时凭此定位 */
    private String planId;

    private Long userId;

    /** 规划目标日期 */
    private LocalDate targetDate;

    /** 用户原始自然语言描述 */
    private String userPrompt;

    /** 建议列表，JSON 数组字符串 */
    private String suggestions;

    /** DRAFT / ADOPTED / EXPIRED */
    private String status;

    private Integer adoptedCount;

    /** 采纳后创建的日程ID，逗号分隔 */
    private String createdScheduleIds;

    /** 过期时间：规划有「时效」，隔太久再采纳会与用户当前实际安排冲突 */
    private LocalDateTime expiresAt;

    /** 逻辑删除：0否 1是（全局配置生效，无需注解） */
    private Integer deleted;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
