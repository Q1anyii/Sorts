# 2026-09-17 · 品牌图标 + Favicon + 锦市皮肤补齐

## 背景
- 用户反馈：左上角图标（🚀 emoji）与「梭子」品牌意象不符，需要优化并设为网页 favicon。
- 用户疑问：前端有 7 套主题（织锦流光/星夜梭影/沧浪青岚/霞光织锦/竹影幽篁/墨韵流年/樱色入梦），锦市却只有 2 个皮肤。

## 根因
1. **锦市皮肤缺失**：`scripts/sql/sorts_mall.sql` 种子定义 12 个商品（7 个 SKIN），但运行库是早期初始化，仅种入 id 1-7（2 个 SKIN）。种子后来补的 id 8-12（5 个皮肤）未同步进运行库 → 前端 CSS/`SKIN_THEME_MAP` 有 7 套，锦市却只能买 2 个。
2. **favicon 未生效**：`docker/Dockerfile.frontend` 只 COPY index.html/css/js，favicon 文件没进镜像 → nginx try_files 把 `/favicon-*.png` 回退成 index.html（同长度 56632 字节即为证据）。

## 改动
| 文件 | 改动 |
|---|---|
| `frontend/index.html` | ① 左上角 `.brand-icon` 与登录页 `.icon` 由 🚀 换成「织梭」白色 SVG（梭身+纱线+梭心，26/40px）；② head 增加 3 个 `<link rel="icon">`（16/32/64 PNG，`?v=20260917-4`）；③ 资源版本号 v3→v4（CRLF 保留，python 脚本 patch_icon.py） |
| `frontend/favicon-16/32/64.png` | 新增：System.Drawing 生成，对角渐变 #4A6CF7→#7C9CF5 + 白色纺锤梭形 |
| `docker/Dockerfile.frontend` | 增加 `COPY frontend/favicon-*.png` |
| `scripts/sql/mall_seed_812.sql` | 新增：INSERT IGNORE 补插 id 8-12 皮肤（幂等可重跑），与种子一致 |
| `README.md` | 前端特性补充 favicon/品牌图标；锦市补充 7 款皮肤 |

## 验证（浏览器实测）
- 锦市接口 `GET /api/v1/mall/items` 返回 12 商品 / 7 SKIN（1,2,8-12）；锦市页面显示全部 7 款皮肤，价格与描述正确。
- favicon：`/favicon-16/32/64.png?v=20260917-4` 真实返回（436/709/1172 字节，非 SPA fallback）。
- 左上角 `.sidebar-brand .brand-icon` 渲染织梭 SVG；登录页 `.icon` 同源 SVG。
- 硬刷新后稳定，无 404。

## 未决
- favicon 未做多尺寸 ICO（.ico）；现代浏览器已支持 PNG 图标，如需 IE/老书签兼容可后续补充。
