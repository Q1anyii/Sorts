# Devlog · 2026-10-08 · 环境恢复：SQL 执行顺序中断 + Docker 代理残留

## 现象

WSL 冷启动后全套容器恢复（11 容器全绿），但出现三个依次暴露的问题：

1. **浏览器打不开 8088**：端口探测 ✓ 但浏览器 chrome-error。
2. **登录报「操作失败 · 系统繁忙」**：`sorts_user` 库只有库、没有表（`t_user` 不存在）。
3. **梭灵报「Remote host terminated the handshake」**：sorts-ai 容器无法访问 DeepSeek API。

## 定位过程

1. **8088 打不开**：WSL 内 `curl localhost:8088` = 000，但 `ss` 显示 `*:8088` 在监听 → `docker info` 显示引擎是 **Docker Desktop**（非 WSL 内 dockerd），端口映射在 Windows 宿主 → Windows 侧代理软件挡 localhost → 浏览器把 localhost 加入 no_proxy 后通。
2. **登录失败**：`docker logs sorts-user` → `Table 'sorts_user.t_user' doesn't exist` → `git ls-files scripts/sql/` 确认 9 个 SQL 文件全在 → 发现 `docker.sh sql` 按文件名顺序执行，`mall_seed_812.sql`（种子数据）排在 `sorts_*.sql`（建表）**之前** → INSERT 时表不存在 → FAILED → 脚本 `set -e` 直接退出 → 后面 7 个建表/升级脚本**全部未执行** → 5 个库全部只有库没有表。
3. **梭灵握手失败**：`docker logs sorts-ai` → 配置正常（`endpoint=https://api.deepseek.com/v1/chat/completions, configured=true`）→ 容器内 `wget https://api.deepseek.com` FAIL（`--no-proxy` 也 FAIL）→ 但 WSL/Windows `curl` 均返回 401（网络通）→ `docker info` 显示引擎代理 `http.docker.internal:3128` → Windows 系统代理已关（`ProxyEnable=0x0`）但 Docker Desktop 仍挂 manual 代理（继承自此前代理软件运行期）→ 转发链无上游 → 容器所有出站 TLS 握手被终止。

## 根因

1. **数据库表缺失**：`scripts/sql/` 文件名排序导致种子脚本先于建表脚本执行；`set -euo pipefail` 使首个 FAILED 即中断，后续建表全被跳过。
2. **容器出网失败**：Docker Desktop 残留 manual 代理（原指向已退出的代理软件端口），Windows 系统代理关闭后无上游，容器出站流量全部握手失败。

## 修复

1. **数据库**：
   - 临时移走种子文件：`mv scripts/sql/mall_seed_812.sql /tmp/`
   - 全量重跑：`bash scripts/docker.sh sql`（建库 → sorts_* 建表 → upgrade_* 全部 OK）
   - 移回种子文件，恢复 git 工作区
   - 手动补导种子：`docker exec -i sorts-mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD"' < scripts/sql/mall_seed_812.sql`
2. **容器出网**：Docker Desktop → Settings → Resources → Proxies → 关闭 Manual proxy configuration → Apply & Restart → 容器恢复后出网正常。

## 验证

- `SHOW TABLES FROM sorts_user` → `t_user` / `t_points_log` ✓
- `SHOW TABLES FROM sorts_mall` → `t_mall_item` / `t_purchase_record` / `t_wardrobe_item` ✓
- 种子导入无报错
- 容器内 `wget https://api.deepseek.com` → OK
- 浏览器登录 ✓、梭灵对话 ✓

## 影响面

- **无代码改动**（纯环境/运维恢复），SQL 文件均回原位，git 工作区干净。
- 经验沉淀：`docker.sh sql` 按文件名排序对种子脚本不友好（`mall_seed_812` 早于 `sorts_*` 建表），建议后续把种子脚本改名 `zz_*` 或调整 `apply_sql` 排序逻辑。
- 容器全部 `restart: unless-stopped`，WSL 冷启动后自动恢复。

## 后续

- ✅ **已处理（同日）**：种子脚本改名 `scripts/sql/zz_mall_seed_812.sql`（`git mv`），执行顺序固定为「建库 → 建表 → 升级 → 种子」，消除再次重置数据库时的中断隐患。
- 可选清理：`~/volumes`（Milvus etcd 数据，约 267M）确认无用后删除。
- 可选优化：Docker Desktop 登录自启项（`HKCU\...\Run`）已定位未删除；WSL `systemd=true` 自启改造未执行——本次恢复以可用为准，均未改动。
