# Devlog · 主版前端迁回 Vite 工程（CDN → 构建产物）

日期：2026-09-17
状态：已完成并浏览器验收

## 背景

此前主版前端为 Vue 3 CDN 单页（`frontend/index.html` + `js/app.js` + `css/style.css`，Nginx 免构建静态托管），而 Vite 6 + TS 组件化工程（`frontend/vite-app/`）仅作存档不参与构建。导致：
- 线上代码无构建 / 无打包 / 无压缩，1.1MB 原样传输；
- 主版与测试对象（存档工程）不是同一份代码，前端单测存在覆盖盲区。

用户要求：把当前使用的这一版（CDN 主版，含全部多轮打磨功能）迁回 Vite 工程。

## 方案：模板留 index.html，逻辑进 src/legacy

- 保持主版「模板内联 index.html + 单文件逻辑」的架构不变（避免组件化重构引入行为差异），迁入 `frontend/vite-app/`：
  - `index.html`：主版模板原样迁入（body 内 `<div id="app" v-cloak>` 全文），头部去除 CDN script / vendor script / css link；
  - `src/legacy/app-logic.ts`：`js/app.js` 全文迁入，顶部 `const {…} = Vue` 改为 `import … from 'vue/dist/vue.esm-bundler.js'`（**关键**：模板内联在 DOM，必须用完整版含运行时编译器，默认 npm `vue` 是 runtime-only 会渲染空白），marked / highlight.js 改为 npm 依赖 import（`marked@12` / `highlight.js@11`）；
  - `src/legacy/style.css` + `github.min.css`：原样复制；
  - `src/main.ts`：导入 legacy 样式 + app-logic，`app.mount('#app')`。

## 构建修复记录

1. **parse5 严格解析**：模板内 `{{ userInfo.points < item.price ? … }}` 的 `<` 被当作标签开始 → 改写为 `item.price > userInfo.points`；
2. **runtime-only 空白页**：默认 `import from 'vue'` 无模板编译器，`#app` 渲染为 `<!---->` → 改 `vue/dist/vue.esm-bundler.js`。

## 部署

`docker/Dockerfile.frontend` 改多阶段：
- build 阶段 `node:20-alpine`：`npm ci` → `npm run build`（产物 dist/）；
- 运行阶段 `nginx:1.27-alpine`：复制 dist + favicon，`nginx.conf` 不变（已有 `/assets/` 长缓存、SPA fallback、`/api` 反代）。

`.dockerignore` 放开 `frontend/vite-app`（构建上下文需要），仍忽略 node_modules / dist。

## 验证（浏览器实测）

1. 构建：`npm run build` 3.2s，211 模块，dist/index.html 64.6kB + JS 1.12MB（含 marked/hljs，未拆 chunk，后续可 manualChunks 优化）；
2. 测试：Vitest 4 文件 / 19 用例全过（旧组件化测试未受影响）；
3. 页面：登录 / 双令牌续期 / 今日经纬（今日日程+统计+即将到来）/ 织历调色板（radial-gradient 铺满）/ 梭灵三栏布局 + SSE 多日规划 / 全局穿梭气泡（动态任务色 + 呼吸 2.2s）全部正常。

## 后续可做

- `build.rollupOptions.output.manualChunks` 拆 vue / marked / hljs，消灭 500kB 警告；
- 渐进组件化：按页面把 legacy 模板/逻辑拆入 `src/views/*`（保留组件化源码路径已就绪）。
