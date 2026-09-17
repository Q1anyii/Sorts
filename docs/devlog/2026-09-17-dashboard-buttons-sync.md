# Devlog · 2026-09-17 · 今日经纬按钮风格统一

## 需求
用户要求把今日经纬（dashboard）里今日日程的按钮同步成主计划/织程页的现代化风格（ghost 胶囊 + 图标圆钮），保持全站一致。

## 实现
- **今日日程列表行**（`.schedule-actions`）：
  - `▶ 开梭` → `btn-ghost-success`
  - `⏸ 暂停` → `btn-ghost-warn`
  - `▶ 续梭` → `btn-ghost-success`
  - `⏹ 落梭` → `btn-ghost-brand`
  - `✎ 编辑` → `btn-icon-sm`（圆形图标钮）
  - 行按钮容器加 `display:flex; align-items:center; gap:6px`
- **顶部进行中卡片**（`.active-timer` 穿梭操作）：
  - `⏸ 暂停` → `btn-ghost-warn`；`▶ 续梭` → `btn-ghost-success`；`⏹ 落梭` → `btn-ghost-brand`；`✕ 取消` → `btn-ghost-danger`

## 验证
浏览器端到端截图核对：顶部卡片（⏸ 暂停 / ⏹ 落梭 / ✕ 取消）与列表行（红1 进行中：⏸ ⏹；灰1 待开始：▶ 开梭 ✎）均为 ghost 胶囊/图标圆钮，与主计划、织程页一致。

## 变更文件
- `frontend/vite-app/index.html`：dashboard 今日日程列表行 + 顶部活跃卡片按钮类名

## 备注
- 未提交前完成 git 提交（先提交不推送）。
