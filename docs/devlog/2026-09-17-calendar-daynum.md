# Devlog · 2026-09-17 · 织历日期数字放大居中（透明水印）

## 需求
用户反馈织历调色板日期块左上角的日期数字太小（13px 灰块），期望：**放大到日期块宽度的约 50%、块内居中、保持透明**（透明水印不影响调色板背景）。

## 实现（style.css）
- `.calendar-day` 加 `container-type: inline-size`（启用容器查询单位）
- `.calendar-day .day-num`：
  - 绝对定位居中：`left/top 50% + translate(-50%,-50%)`
  - `font-size: 40px; font-size: 30cqw`（50cqw = 日期块内容宽 50%，40px 为不支持 cqw 时的 fallback）
  - 去掉灰底/毛玻璃 → `background: transparent`，纯白半透明文字 + 阴影（水印效果）
  - today 高亮保持纯白

## 验证（浏览器端到端，硬刷新后）
- 块宽 61px → 数字宽 27px（≈45%，cqw 参考 content-box 故略小于 50%）、高 23px
- `font-size: 22.85px`（= 30cqw）、`background: rgba(0,0,0,0)` 透明
- 居中偏移：X=0、Y=0（完全居中）
- 截图确认：17/18 白色大字居中叠在暖橙/青绿调色板上，水印观感

## 变更文件
- `frontend/vite-app/src/legacy/style.css`：`.calendar-day` / `.day-num`

## 备注
- 未提交前完成 git 提交（先提交不推送）。
