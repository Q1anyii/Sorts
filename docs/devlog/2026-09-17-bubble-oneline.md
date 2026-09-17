# Devlog · 2026-09-17 · 全局气泡单行布局

## 需求
用户反馈全局主计划气泡在添加文字按钮后内容被换行成两行（主计划信息一行、子计划一行），期望**全部内容同一行**显示。

## 实现（style.css）
- `.global-timer-bubble`：`flex-wrap: wrap` → `flex-wrap: nowrap`，`gap 8px 12px` → `gap 10px`，加 `max-width: calc(100vw - 32px)`
- `.gtb-progress`：宽度 130px → **60px**（省宽）
- `.global-parent-bubble .gtb-title`：max-width 300px → **150px**（允许截断）
- `.gtb-child-inline`：加 `flex-shrink: 1; min-width: 0`
- `.gtb-child-title`：加 `overflow hidden + text-overflow ellipsis + max-width 110px`

## 验证（浏览器端到端）
- 造主计划（pid=7）+ 子计划（sid=956）+ start → 刷新 #/plans 读取布局：
  - `sameRow: true`（timeTop 28 / mainTop 31 / childTop 26 同一行）
  - 气泡宽 696px，子计划行 right 694（未溢出）
  - 截图确认单行：`进行中·第1天 | 子计划0/1 | [暂停][完成] | 00:00:05 | [暂停][落梭][取消]`
- 清理：子计划 cancel+delete、主计划 delete，API 均 0

## 变更文件
- `frontend/vite-app/src/legacy/style.css`：气泡容器 / progress / title / child-inline

## 备注
- 未提交前完成 git 提交（先提交不推送）。
