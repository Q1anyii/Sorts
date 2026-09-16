-- =====================================================================
-- 梭子 SORTS · 商城库（sorts_mall）初始化脚本
-- 主题：光阴似箭，日月如梭
-- 执行：mysql -u root -p sorts_mall < scripts/sql/sorts_mall.sql
-- 说明：种子商品使用显式主键 + INSERT IGNORE，脚本可重复执行且 ID 稳定
-- =====================================================================

CREATE DATABASE IF NOT EXISTS sorts_mall
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE sorts_mall;

-- 虚拟商品表
CREATE TABLE IF NOT EXISTS t_mall_item (
    id          BIGINT       PRIMARY KEY AUTO_INCREMENT COMMENT '商品ID',
    name        VARCHAR(64)  NOT NULL COMMENT '商品名称',
    type        VARCHAR(16)  NOT NULL COMMENT '类型：SKIN/AVATAR/BADGE/STICKER',
    description VARCHAR(255) DEFAULT NULL COMMENT '商品描述',
    image_url   VARCHAR(512) DEFAULT NULL COMMENT '商品图',
    preview_url VARCHAR(512) DEFAULT NULL COMMENT '预览图',
    price       INT          NOT NULL DEFAULT 0 COMMENT '所需光阴砂',
    stock       INT          NOT NULL DEFAULT -1 COMMENT '库存，-1 表示无限',
    status      VARCHAR(16)  NOT NULL DEFAULT 'ON_SALE' COMMENT '状态：ON_SALE/OFF_SHELF',
    sort_order  INT          NOT NULL DEFAULT 0 COMMENT '排序权重，越大越靠前',
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0否 1是',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY idx_type_status (type, status),
    KEY idx_status_sort (status, sort_order)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '虚拟商品表';

-- 购买记录表（商品名与成交价做快照，商品改价/下架后历史记录仍可读）
CREATE TABLE IF NOT EXISTS t_purchase_record (
    id           BIGINT      PRIMARY KEY AUTO_INCREMENT COMMENT '购买记录ID',
    user_id      BIGINT      NOT NULL COMMENT '买家ID',
    item_id      BIGINT      NOT NULL COMMENT '商品ID',
    item_name    VARCHAR(64) NOT NULL COMMENT '商品名快照',
    price        INT         NOT NULL COMMENT '成交价快照（光阴砂）',
    points_after INT         DEFAULT NULL COMMENT '成交后余额快照',
    created_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '购买时间',
    KEY idx_user_created (user_id, created_at),
    KEY idx_item (item_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '购买记录表';

-- 装扮仓库：唯一键 (user_id, item_id) 是「同一装扮只能拥有一份」的最终防线
CREATE TABLE IF NOT EXISTS t_wardrobe_item (
    id          BIGINT      PRIMARY KEY AUTO_INCREMENT COMMENT '仓库条目ID',
    user_id     BIGINT      NOT NULL COMMENT '用户ID',
    item_id     BIGINT      NOT NULL COMMENT '商品ID',
    item_type   VARCHAR(16) NOT NULL COMMENT '商品类型（冗余，切换装扮时按类型互斥）',
    is_active   TINYINT     NOT NULL DEFAULT 0 COMMENT '是否当前使用中',
    purchase_id BIGINT      DEFAULT NULL COMMENT '来源购买记录ID',
    deleted     TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除：0否 1是',
    purchased_at DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '获得时间',
    UNIQUE KEY uk_user_item (user_id, item_id),
    KEY idx_user_active (user_id, is_active)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '装扮仓库表';

-- ========== 种子商品 ==========
-- 无限库存用于皮肤/徽章等「虚拟无限量」商品；限量库存用于验证防超卖
INSERT IGNORE INTO t_mall_item (id, name, type, description, image_url, preview_url, price, stock, status, sort_order) VALUES
  (1, '织锦流光·皮肤', 'SKIN',   '以织锦纹样为底的主题皮肤，光阴流动间色序渐变。', '/assets/mall/skin-brocade.png',  '/assets/mall/skin-brocade-preview.png',  200, -1, 'ON_SALE', 100),
  (2, '星夜梭影·皮肤', 'SKIN',   '深夜配色的穿梭主题，适合夜间专注。',        '/assets/mall/skin-night.png',    '/assets/mall/skin-night-preview.png',    300, -1, 'ON_SALE',  90),
  (3, '梭影·头像框',   'AVATAR', '简约织梭线条头像框。',                     '/assets/mall/frame-shuttle.png', '/assets/mall/frame-shuttle-preview.png',  80, -1, 'ON_SALE',  80),
  (4, '鎏金梭·头像框', 'AVATAR', '鎏金质感的限定头像框。',                   '/assets/mall/frame-gold.png',    '/assets/mall/frame-gold-preview.png',    150, 50, 'ON_SALE',  70),
  (5, '掌灯人徽章',    'BADGE',  '在深夜仍坚持落梭的人。',                   '/assets/mall/badge-lamp.png',    '/assets/mall/badge-lamp-preview.png',     50, -1, 'ON_SALE',  60),
  (6, '织造大师徽章',  'BADGE',  '累计专注时长达标后佩戴的荣誉徽章。',        '/assets/mall/badge-master.png',  '/assets/mall/badge-master-preview.png',  120, -1, 'ON_SALE',  50),
  (7, '落梭贴纸包',    'STICKER','一组日程状态贴纸，可直接用于日程标记。',    '/assets/mall/sticker-pack.png',  '/assets/mall/sticker-pack-preview.png',   30, -1, 'ON_SALE',  40);
