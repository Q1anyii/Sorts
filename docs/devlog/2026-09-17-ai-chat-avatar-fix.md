# Devlog · 2026-09-17 · AI 对话用户头像不识别修复

## 问题
用户上传自定义头像后，梭灵对话界面用户消息仍显示默认 emoji 👤，识别不到自定义头像。

## 根因
`frontend/vite-app/index.html` AI 对话模板用户消息头像写死为 `'👤'`：
```html
<div class="ai-avatar" :class="msg.role">{{ msg.role === 'user' ? '👤' : '🤖' }}</div>
```
未绑定 `userInfo.avatarUrl`；且 Vue 实例中用户信息字段名为 `userInfo`（非 `user`）。

## 修复
- `index.html`：用户消息头像改为 `v-if="msg.role==='user' && userInfo.avatarUrl"` 渲染 `<img :src="userInfo.avatarUrl">`，否则回退 emoji 👤。
- `src/legacy/style.css`：`.ai-avatar` 增加 `overflow: hidden` 与 `.ai-avatar img { object-fit: cover }`，使头像图圆形裁剪显示。

## 验证（浏览器端到端，probe_ok）
1. 无头像时：用户消息显示 👤（正常回退）。
2. 调用 `/api/v1/users/avatar/upload` 上传头像（avatarUrl=`/api/v1/users/avatar/files/u5_*.png`）后刷新：侧边栏与 AI 对话用户消息头像均渲染 `<img>`（DOM 实测 `U:img=true`），机器人头像保持 🤖。

## 变更文件
- `frontend/vite-app/index.html`
- `frontend/vite-app/src/legacy/style.css`
