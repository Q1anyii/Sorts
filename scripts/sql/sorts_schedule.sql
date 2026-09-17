-- =====================================================================
-- 梭子 SORTS · 日程库（sorts_schedule）初始化脚本
-- 主题：光阴似箭，日月如梭
-- 执行：mysql -u root -p sorts_schedule < scripts/sql/sorts_schedule.sql
--      或容器内：bash scripts/wsl-middleware.sh sql
-- =====================================================================

CREATE DATABASE IF NOT EXISTS sorts_schedule
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE sorts_schedule;

-- 日程表
CREATE TABLE IF NOT EXISTS t_schedule (
    id                 BIGINT       PRIMARY KEY AUTO_INCREMENT COMMENT '日程ID',
    user_id            BIGINT       NOT NULL COMMENT '所属用户ID',
    title              VARCHAR(128) NOT NULL COMMENT '日程标题',
    description        VARCHAR(1024) DEFAULT NULL COMMENT '详细描述',
    planned_start_time DATETIME     DEFAULT NULL COMMENT '计划开始时间',
    planned_duration   INT          DEFAULT NULL COMMENT '计划时长（分钟）',
    actual_start_time  DATETIME     DEFAULT NULL COMMENT '实际开始时间（首次穿梭时写入）',
    actual_end_time    DATETIME     DEFAULT NULL COMMENT '实际结束时间（落梭时写入）',
    actual_duration    INT          NOT NULL DEFAULT 0 COMMENT '实际累计时长（秒，由计时片段求和）',
    status             VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING/IN_PROGRESS/PAUSED/COMPLETED/CANCELLED/TIMEOUT',
    priority           VARCHAR(16)  NOT NULL DEFAULT 'MEDIUM' COMMENT '优先级：LOW/MEDIUM/HIGH/URGENT',
    tags               VARCHAR(255) DEFAULT NULL COMMENT '标签，逗号分隔（如：学习,Java）',
    color              VARCHAR(16)  DEFAULT NULL COMMENT '显示颜色（十六进制）',
    deleted            TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0否 1是',
    deleted_at         DATETIME     DEFAULT NULL COMMENT '逻辑删除时间（删除操作写入，配合 deleted 做审计）',
    last_operator_id   BIGINT       DEFAULT NULL COMMENT '最近一次计时状态变更的操作人（网关 X-User-Id）',
    created_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    -- 列表 / 日历 / 统计都以「用户 + 计划时间」为入口，覆盖索引避免回表排序
    KEY idx_user_planned (user_id, planned_start_time),
    KEY idx_user_status (user_id, status),
    KEY idx_user_created (user_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '日程表';

-- 计时片段表（分段计时：每次 穿梭/续梭 到 停梭/落梭 之间为一段）
CREATE TABLE IF NOT EXISTS t_time_record (
    id          BIGINT   PRIMARY KEY AUTO_INCREMENT COMMENT '片段ID',
    schedule_id BIGINT   NOT NULL COMMENT '所属日程ID',
    user_id     BIGINT   NOT NULL COMMENT '所属用户ID',
    start_time  DATETIME NOT NULL COMMENT '片段开始时间',
    end_time    DATETIME DEFAULT NULL COMMENT '片段结束时间，NULL 表示仍在计时',
    duration    INT      NOT NULL DEFAULT 0 COMMENT '片段时长（秒）',
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    -- 结算时需要「按日程找未闭合片段」，该索引直接命中
    KEY idx_schedule_open (schedule_id, end_time),
    KEY idx_user_created (user_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '计时片段表';
