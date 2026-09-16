-- =====================================================================
-- 梭子 SORTS · 数据库初始化（容器首次启动时自动执行）
-- 微服务数据隔离：一服务一库，禁止跨库直连
-- 执行顺序：本文件先建库，各服务的建表脚本随后执行（按文件名排序）
-- =====================================================================

CREATE DATABASE IF NOT EXISTS sorts_user
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS sorts_schedule
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS sorts_ai
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS sorts_notification
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS sorts_mall
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
