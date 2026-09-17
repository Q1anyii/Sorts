package com.sorts.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 会话（t_ai_conversation）。
 *
 * <p>登录用户的会话由后端持久化，刷新 / 换设备不丢；消息、草稿、勾选项、
 * 最近生成区间均以 JSON 快照整体保存（个人场景数据量小，快照式最简单可靠）。</p>
 *
 * @author sorts
 */
@Data
@TableName("t_ai_conversation")
public class AiConversation {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 会话标题（默认取首条用户消息前 20 字） */
    private String title;

    /** 消息列表 JSON 数组：[{id, role, content, timestamp, structuredData}] */
    private String messages;

    /** 输入框草稿 */
    private String draft;

    /** 已勾选的规划项 JSON 数组 */
    private String selectedPlanItems;

    /** 最近一次生成的规划区间 JSON：{startDate, endDate} */
    private String lastGeneratedRange;

    /** 最近一次规划的快照 planId（用于恢复规划面板与采纳） */
    private String planId;

    /** 最近一次规划的建议快照 JSON 数组（刷新后无需重调模型即可恢复勾选面板） */
    private String planSuggestions;

    private Integer deleted;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
