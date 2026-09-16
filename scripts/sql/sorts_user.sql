-- =====================================================================
-- 梭子 SORTS · 用户库（sorts_user）初始化脚本
-- 主题：光阴似箭，日月如梭
-- 执行：mysql -u root -p sorts_user < scripts/sql/sorts_user.sql
-- =====================================================================

CREATE DATABASE IF NOT EXISTS sorts_user
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE sorts_user;

-- 用户表
CREATE TABLE IF NOT EXISTS t_user (
    id                  BIGINT       PRIMARY KEY AUTO_INCREMENT COMMENT '用户ID',
    username            VARCHAR(64)  NOT NULL COMMENT '用户名',
    password            VARCHAR(128) NOT NULL COMMENT 'BCrypt 密码',
    nickname            VARCHAR(64)  DEFAULT NULL COMMENT '昵称',
    email               VARCHAR(128) DEFAULT NULL COMMENT '邮箱',
    phone               VARCHAR(32)  DEFAULT NULL COMMENT '手机号',
    avatar_url          VARCHAR(512) DEFAULT NULL COMMENT '头像地址',
    points              INT          NOT NULL DEFAULT 0 COMMENT '光阴砂余额',
    active_skin         VARCHAR(64)  DEFAULT NULL COMMENT '当前皮肤',
    active_avatar_frame VARCHAR(64)  DEFAULT NULL COMMENT '当前头像挂件',
    status              TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1正常 0禁用',
    deleted             TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0否 1是',
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_username (username),
    KEY idx_created_at (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户表';

-- 积分流水表（光阴砂）
CREATE TABLE IF NOT EXISTS t_points_log (
    id            BIGINT      PRIMARY KEY AUTO_INCREMENT COMMENT '流水ID',
    user_id       BIGINT      NOT NULL COMMENT '用户ID',
    change_amount INT         NOT NULL COMMENT '变动值：正增负减',
    balance       INT         NOT NULL COMMENT '变动后余额',
    reason        VARCHAR(128) DEFAULT NULL COMMENT '变动原因',
    related_id    BIGINT      DEFAULT NULL COMMENT '关联业务ID',
    created_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY idx_user_created (user_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '积分流水表';
