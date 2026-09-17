# Devlog · 2026-09-17 · 全局气泡按钮样式优化

## 需求
用户反馈全局悬浮气泡（穿梭计时）右侧三个操作按钮（暂停/落梭/取消）样式一般，期望更精致、贴合项目「织锦流光」主题。

## 实现（style.css `.gtb-btn`）
- **尺寸**：26px → **30px**（更好点击）
- **渐变立体**：纯色 flat → **垂直渐变 + 内高光 + 外阴影 + 文字阴影**
  - 暂停（默认）：**琥珀渐变** `rgba(251,191,36) → rgba(217,119,6)`
  - 续梭 / 落梭：**绿系渐变**（resume 亮绿、end 深绿）
  - 取消：**红渐变** `rgba(248,113,113) → rgba(220,38,38)`
- **交互**：hover 提升（translateY + scale 1.06 + 亮度 + 阴影加深）、active 按压缩小（scale .94）
- 白边保留（1px rgba(255,255,255,.6)）

## 验证
浏览器端到端：切到 #/plans 页读取 computed style——
- ⏸ 30px / linear-gradient(rgba(251,191,36,.95), rgba(217,119,6,.92)) / shadow ✓
- ⏹ 30px / linear-gradient(rgba(52,211,153,.95), rgba(5,150,105,.94)) / shadow ✓
- ✕ 30px / linear-gradient(rgba(248,113,113,.95), rgba(220,38,38,.94)) / shadow ✓
- 截图确认渐变立体观感。

## 变更文件
- `frontend/vite-app/src/legacy/style.css`：`.gtb-btn` 系列

## 备注
- 全局气泡在 dashboard 页按既有需求隐藏（`currentPage!=='dashboard'`），验证需在其他页面。
- 未提交前完成 git 提交（先提交不推送）。
