-- =====================================================================
-- 梭子 SORTS · 通知库（sorts_notification）初始化脚本
-- 主题：光阴似箭，日月如梭
-- 执行：mysql -u root -p sorts_notification < scripts/sql/sorts_notification.sql
-- =====================================================================

CREATE DATABASE IF NOT EXISTS sorts_notification
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE sorts_notification;

-- 通知表
CREATE TABLE IF NOT EXISTS t_notification (
    id         BIGINT       PRIMARY KEY AUTO_INCREMENT COMMENT '通知ID',
    user_id    BIGINT       NOT NULL COMMENT '接收用户ID',
    type       VARCHAR(16)  NOT NULL COMMENT '类型：REMINDER/SUMMARY/SYSTEM/PROMOTION',
    title      VARCHAR(128) NOT NULL COMMENT '标题',
    content    VARCHAR(512) DEFAULT NULL COMMENT '正文',
    is_read    TINYINT      NOT NULL DEFAULT 0 COMMENT '是否已读：0否 1是',
    related_id BIGINT       DEFAULT NULL COMMENT '关联业务ID（如日程ID）',
    deleted    TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0否 1是',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    -- 列表查询按「用户 + 时间倒序」，未读角标按「用户 + 已读」过滤
    KEY idx_user_created (user_id, created_at),
    KEY idx_user_read (user_id, is_read)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '通知表';

-- 提醒设置表（每用户一行，缺省行不存在时由服务端返回默认值，不写空行）
CREATE TABLE IF NOT EXISTS t_reminder_setting (
    id                    BIGINT      PRIMARY KEY AUTO_INCREMENT COMMENT '设置ID',
    user_id               BIGINT      NOT NULL COMMENT '用户ID',
    default_advance_minutes INT       NOT NULL DEFAULT 15 COMMENT '默认提前提醒分钟数',
    channels              VARCHAR(64) NOT NULL DEFAULT 'APP' COMMENT '启用的提醒渠道，逗号分隔：APP,EMAIL,SMS',
    quiet_hours_enabled   TINYINT     NOT NULL DEFAULT 0 COMMENT '是否启用免打扰时段',
    quiet_start           VARCHAR(5)  DEFAULT NULL COMMENT '免打扰开始 HH:mm',
    quiet_end             VARCHAR(5)  DEFAULT NULL COMMENT '免打扰结束 HH:mm',
    deleted               TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除：0否 1是',
    created_at            DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at            DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '提醒设置表';

-- 提醒发送留痕表：唯一键即幂等键，同一日程的同一提醒时刻只会推送一次
CREATE TABLE IF NOT EXISTS t_reminder_log (
    id          BIGINT   PRIMARY KEY AUTO_INCREMENT COMMENT '留痕ID',
    user_id     BIGINT   NOT NULL COMMENT '用户ID',
    schedule_id BIGINT   NOT NULL COMMENT '日程ID',
    remind_at   DATETIME NOT NULL COMMENT '本次提醒的触发时刻（分钟取整）',
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_user_schedule_remind (user_id, schedule_id, remind_at),
    KEY idx_created_at (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '提醒发送留痕表（幂等去重）';
