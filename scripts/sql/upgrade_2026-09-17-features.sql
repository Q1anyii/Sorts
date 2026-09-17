-- =====================================================================
-- 梭子 SORTS · 2026-09-17 功能改造迁移脚本（针对已存在的数据卷）
-- 执行：docker exec -i sorts-mysql mysql -uroot -p<密码> < upgrade_2026-09-17-features.sql
-- 说明：
--   1. 已存在数据卷不会重跑 init 脚本，因此本脚本用 ALTER 补列；
--   2. 新建数据卷时由 scripts/sql/sorts_schedule.sql / sorts_ai.sql 直接建出新结构，
--      两处必须保持一致。
--   3. 全部使用 IF NOT EXISTS 语义（MySQL 8.0 无 ADD COLUMN IF NOT EXISTS，
--      用存储过程判列存在性），可重复执行。
-- =====================================================================

-- ---------- 日程表：删除审计时间 + 最近操作人 ----------
SET @sql := (SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE sorts_schedule.t_schedule ADD COLUMN deleted_at DATETIME DEFAULT NULL COMMENT ''逻辑删除时间（删除操作写入，配合 deleted 做审计）'' AFTER deleted',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = 'sorts_schedule' AND TABLE_NAME = 't_schedule' AND COLUMN_NAME = 'deleted_at');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE sorts_schedule.t_schedule ADD COLUMN last_operator_id BIGINT DEFAULT NULL COMMENT ''最近一次计时状态变更的操作人'' AFTER deleted_at',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = 'sorts_schedule' AND TABLE_NAME = 't_schedule' AND COLUMN_NAME = 'last_operator_id');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 织史（AI 报告）：删除审计时间 ----------
SET @sql := (SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE sorts_ai.t_ai_report ADD COLUMN deleted_at DATETIME DEFAULT NULL COMMENT ''逻辑删除时间'' AFTER deleted',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = 'sorts_ai' AND TABLE_NAME = 't_ai_report' AND COLUMN_NAME = 'deleted_at');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- AI 会话表 ----------
CREATE TABLE IF NOT EXISTS sorts_ai.t_ai_conversation (
    id                    BIGINT        PRIMARY KEY AUTO_INCREMENT COMMENT '会话ID',
    user_id               BIGINT        NOT NULL COMMENT '所属用户ID',
    title                 VARCHAR(100)  NOT NULL DEFAULT '新会话' COMMENT '会话标题',
    messages              MEDIUMTEXT    NOT NULL COMMENT '消息列表 JSON 数组：[{id,role,content,timestamp,structuredData}]',
    draft                 TEXT          DEFAULT NULL COMMENT '输入框草稿',
    selected_plan_items   MEDIUMTEXT    DEFAULT NULL COMMENT '已勾选规划项 JSON 数组',
    last_generated_range  VARCHAR(512)  DEFAULT NULL COMMENT '最近生成区间 JSON：{startDate,endDate}',
    plan_id               VARCHAR(64)   DEFAULT NULL COMMENT '最近一次规划快照 planId（恢复规划面板）',
    plan_suggestions      MEDIUMTEXT    DEFAULT NULL COMMENT '最近一次规划的建议快照 JSON 数组',
    deleted               TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除：0否 1是',
    created_at            DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at            DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    -- 会话列表按用户 + 更新时间倒序，容量清理按「最旧未更新」删除
    KEY idx_user_updated (user_id, updated_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'AI 会话表';
