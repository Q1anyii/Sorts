# Devlog · 2026-09-17 · 头像框覆盖头像修复 + 头像框落地 AI 对话

## 问题
1. 设置页 / 侧边栏启用头像框后，渐变圆**盖住头像**（只看到金色圆形，看不到头像图）；
2. 头像框未落地到梭灵对话界面（AI 页用户消息头像没有头像框）。

## 根因
`style.css` 头像框 `.has-frame::before` 是**实心渐变圆**（`inset:-5px; z-index:1`，背景 linear-gradient 铺满整个圆），绝对定位在头像 img 之上 → 头像被完全遮挡。AI 对话页用户头像模板仅 `:class="msg.role"`，未绑定 `activeAvatar`。

## 修复
- `style.css`：
  - `.has-frame::before` 增加 `-webkit-mask/mask: radial-gradient(farthest-side, transparent calc(100% - 7px), #000 calc(100% - 6px))` ——渐变圆被抠成**中空圆环**，中间透明、外圈 7px 渐变，头像在白圈内可见；
  - 头像框选择器组扩展 `.ai-avatar.has-frame`（overflow visible + img/span 圆角 + ::before/::after 环）。
- `index.html`：AI 页用户消息头像 `:class="[msg.role, msg.role==='user' && activeAvatar ? 'has-frame frame-' + activeAvatar.item.id : '']"`，启用中的头像框同步落地 AI 对话。

## 验证（浏览器端到端，probe_ok：frame-4 鎏金梭使用中 + 已上传头像）
1. 计算样式实测：`.ai-avatar` `position=relative overflow=visible`，`::before` `mask=radial-gradient(..., transparent calc(100% - 7px), #000 ...)`、`background=linear-gradient(135deg, #F5C26B, #E8A33D...)` ——环状中空生效，头像不被遮挡；
2. DOM 实测：侧边栏 `user-avatar has-frame frame-4`、AI 页用户消息 `ai-avatar user has-frame frame-4`、`img=true` ——头像框已落地 AI 对话；
3. 无头像框用户（activeAvatar=null）回退普通圆形头像，无回归。

## 变更文件
- `frontend/vite-app/index.html`
- `frontend/vite-app/src/legacy/style.css`
