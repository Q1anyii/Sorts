# Devlog · 2026-09-17 · 锦市商品文案乱码（双重 UTF-8 编码）修复

## 现象

锦市页面 7 个商品的名/描述全部显示乱码（如 `ç»‡é”¦æµå…‰Â·çš®è‚¤`），
界面其他中文（前端写死的文案、API 写入的日程等）均正常。

## 根因

- 数据在库里就是坏的：`t_mall_item` 的 name/description 是「UTF-8 → 按 latin1 解读 → 再按 utf8mb4 存储」的双重编码。
- 触发链路：`docker/compose.yml` 首次创建数据卷时把 `scripts/sql/*.sql` 挂到
  `/docker-entrypoint-initdb.d`，由容器内 mysql 客户端执行；容器默认 locale 为 C/POSIX，
  导致 mysql 客户端连接字符集退化为 **latin1**（`character_set_connection=latin1`，实测确认）。
  种子 SQL 里的 UTF-8 中文被按 latin1 逐字节解读后写入 utf8mb4 列 → 双重编码。
- MySQL 的 latin1 实为 cp1252 语义（0x80-0x9F 映射到 ” € … 等），未定义字节 0x81/0x8D/0x8F/0x90/0x9D
  存成 C1 控制符，因此不能直接用常见 cp1252→utf8 脚本恢复，需按 MySQL latin1 语义还原。
- 其余库未受影响：只有 sorts_mall 的种子含中文数据（其他库中文均在注释里，数据为 API 写入、UTF-8 正常，已逐表 HEX 核验）。

## 修复

1. **数据修复（一次性）**：7 行全部还原，核心 SQL（服务器端按 MySQL latin1 语义精确可逆）：
   ```sql
   UPDATE sorts_mall.t_mall_item
      SET name = CONVERT(CAST(CONVERT(name USING latin1) AS BINARY) USING utf8mb4),
          description = CONVERT(CAST(CONVERT(description USING latin1) AS BINARY) USING utf8mb4);
   ```
   修复前先用 SELECT 验证（织锦流光·皮肤 / 星夜梭影·皮肤 / 掌灯人徽章 … 全部还原正确）再执行。
2. **防复发**：`docker/compose.yml` 的 mysql 服务加 `LANG: C.UTF-8`，
   使 init 客户端默认连接字符集为 utf8mb4（仅影响将来重建数据卷；已存在的数据卷不受影响）。

## 验证

- 数据库：7 行 name/description 还原为正确中文
- API：`GET /api/v1/mall/items` 返回 7 个商品全部正确（织锦流光·皮肤、星夜梭影·皮肤、梭影·头像框、鎏金梭·头像框、掌灯人徽章、织造大师徽章、落梭贴纸包）
- 其余表 HEX 核验无同类问题

## 说明

- 前端无需改动（fetch 按 UTF-8 解码 JSON，数据修复后即正常显示）
- 遗留：库里现有 3 条 API 测试日程（【验证】接口对接测试 / 学习Java基础 / AA），编码正常，未清理
