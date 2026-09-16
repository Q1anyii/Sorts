-- =====================================================================
-- 梭子 SORTS · AI 库（sorts_ai）初始化脚本
-- 主题：光阴似箭，日月如梭
-- 执行：mysql -u root -p sorts_ai < scripts/sql/sorts_ai.sql
--      或容器内：bash scripts/wsl-middleware.sh sql
--
-- 说明：对话上下文不落库，存 Redis（短生命周期、按会话键过期）；
--      只有「用户想留存的产物」才落库——周期总结报告 与 AI 规划。
-- =====================================================================

CREATE DATABASE IF NOT EXISTS sorts_ai
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE sorts_ai;

-- 周期总结报告表
CREATE TABLE IF NOT EXISTS t_ai_report (
    id               BIGINT        PRIMARY KEY AUTO_INCREMENT COMMENT '报告ID',
    user_id          BIGINT        NOT NULL COMMENT '所属用户ID',
    type             VARCHAR(16)   NOT NULL COMMENT '报告类型：DAILY/WEEKLY/MONTHLY/YEARLY',
    period_key       VARCHAR(16)   NOT NULL COMMENT '周期标识：2026-09-16 / 2026-09 / 2026',
    title            VARCHAR(128)  NOT NULL COMMENT '报告标题',
    content          MEDIUMTEXT    DEFAULT NULL COMMENT 'Markdown 正文',
    completion_rate  DECIMAL(5, 4) DEFAULT NULL COMMENT '完成率 0-1',
    total_focus_time INT           DEFAULT NULL COMMENT '总专注时长（秒）',
    highlights       VARCHAR(1024) DEFAULT NULL COMMENT '亮点，JSON 数组字符串',
    suggestions      VARCHAR(1024) DEFAULT NULL COMMENT '改进建议，JSON 数组字符串',
    status           VARCHAR(16)   NOT NULL DEFAULT 'GENERATING' COMMENT '状态：GENERATING/COMPLETED/FAILED',
    error_msg        VARCHAR(512)  DEFAULT NULL COMMENT '失败原因（status=FAILED 时填写）',
    generated_at     DATETIME      DEFAULT NULL COMMENT '生成完成时间',
    deleted          TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除：0否 1是',
    created_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    -- 报告列表按「用户 + 类型 + 时间倒序」翻页，该索引覆盖
    KEY idx_user_type_created (user_id, type, created_at),
    -- 月度/年度为异步生成，需按「用户 + 类型 + 周期」定位同周期是否已生成
    KEY idx_user_period (user_id, type, period_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '周期总结报告表';

-- AI 日程规划表
CREATE TABLE IF NOT EXISTS t_schedule_plan (
    id                   BIGINT        PRIMARY KEY AUTO_INCREMENT COMMENT '自增主键',
    plan_id              VARCHAR(64)   NOT NULL COMMENT '对外规划ID（UUID），采纳时凭此定位',
    user_id              BIGINT        NOT NULL COMMENT '所属用户ID',
    target_date          DATE          NOT NULL COMMENT '规划目标日期',
    user_prompt          VARCHAR(2048) NOT NULL COMMENT '用户原始自然语言描述',
    suggestions          MEDIUMTEXT    DEFAULT NULL COMMENT '建议列表，JSON 数组',
    status               VARCHAR(16)   NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT/ADOPTED/EXPIRED',
    adopted_count        INT           NOT NULL DEFAULT 0 COMMENT '已采纳条数',
    created_schedule_ids VARCHAR(512)  DEFAULT NULL COMMENT '采纳后创建的日程ID，逗号分隔',
    expires_at           DATETIME      DEFAULT NULL COMMENT '过期时间（过期后不可采纳）',
    deleted              TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除：0否 1是',
    created_at           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_plan_id (plan_id),
    KEY idx_user_created (user_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'AI 日程规划表';
