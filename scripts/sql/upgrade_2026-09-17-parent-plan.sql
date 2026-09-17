-- =====================================================================
-- 主计划（长时间计划容器，含子计划）· 增量变更 2026-09-17
-- 用法：docker cp 到 sorts-mysql:/tmp 后执行
-- =====================================================================
USE sorts_schedule;

CREATE TABLE IF NOT EXISTS t_parent_plan (
    id          BIGINT       PRIMARY KEY AUTO_INCREMENT COMMENT '主计划ID',
    user_id     BIGINT       NOT NULL COMMENT '用户ID',
    title       VARCHAR(200) NOT NULL COMMENT '主计划标题',
    description VARCHAR(2000) DEFAULT NULL COMMENT '描述',
    color       VARCHAR(16)  DEFAULT NULL COMMENT '主题色（十六进制）',
    priority    VARCHAR(16)  NOT NULL DEFAULT 'MEDIUM' COMMENT '优先级：LOW/MEDIUM/HIGH/URGENT',
    start_date  DATE         DEFAULT NULL COMMENT '计划开始日期',
    end_date    DATE         DEFAULT NULL COMMENT '计划结束日期',
    status      VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING/IN_PROGRESS/PAUSED/COMPLETED',
    started_at  DATETIME     DEFAULT NULL COMMENT '最近一次进入进行中的时间（用于计算已进行天数）',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted     INT          NOT NULL DEFAULT 0 COMMENT '软删除：0=正常 1=已删除',
    deleted_at  DATETIME     DEFAULT NULL,
    KEY idx_user (user_id, deleted),
    KEY idx_user_status (user_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='主计划：长时间计划容器，可挂载多个子计划（t_schedule）';

-- 子计划归属：t_schedule 增加 parent_id（NULL=独立日程）
ALTER TABLE t_schedule
    ADD COLUMN parent_id BIGINT DEFAULT NULL COMMENT '所属主计划ID（NULL=独立日程）' AFTER user_id;
ALTER TABLE t_schedule
    ADD KEY idx_parent (parent_id);
