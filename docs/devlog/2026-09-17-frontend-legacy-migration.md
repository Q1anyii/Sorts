# Devlog · 2026-09-17 · 前端主版切换：legacy-demo 提升 + 真实接口对接

## 背景

用户认为当前 Vite 工程前端（`frontend/src`，Vue3+TS）界面"太丑"，要求将 `frontend/legacy-demo`
（旧版单文件演示：index.html + css/style.css + js/app.js，Vue CDN）作为**主版前端页面**，
在**不改动原页面设计**的前提下完成真实后端接口对接。

## 结构变更

- `frontend/legacy-demo/*`（index.html / css/ / js/）提升至 `frontend/` 根目录 = 主版前端。
- 原 Vite 工程整体移入 `frontend/vite-app/`（git 保留，不再参与构建；`frontend/.gitignore` 已忽略其 node_modules/dist）。
- `docker/Dockerfile.frontend`：改为纯静态托管（nginx 直接 COPY，去掉 Node 构建阶段与 VITE_API_BASE 注入）；
  修正 Docker COPY 目录只拷内容、必须逐目录指定目标以保持 `css/`、`js/` 结构。
- `docker/compose.yml` frontend 段：移除已无意义的 `args.VITE_API_BASE`。
- `.dockerignore`：`frontend/legacy-demo` → `frontend/vite-app`。

## 接口对接（仅重写 js/app.js 数据层，index.html / style.css 设计未动）

| 模块 | 对接接口 | 说明 |
|---|---|---|
| 认证 | /auth/login、/auth/register、/auth/logout | 令牌存 localStorage（键名兼容 Vite 版 sorts.access/refresh）；40101/40102/401 → 无感续期（单飞）→ 重放一次，续期失败回登录页 |
| 日程 | /schedules CRUD、/schedules/active、start/pause/resume/end/cancel | 状态机全走服务端；落梭后以 /users/me 积分差值计算奖励提示 |
| 日历 | 本地由全量日程 computed（模板不改） | 月历/选中日/今日日程均来自真实数据 |
| 统计 | /statistics/summary、/trend、/tags | 7 日趋势与标签分布为真实数据；「连续打卡天数」后端未提供，用真实日程推算最长连续有日程天数（代码注明） |
| AI | /ai/chat?stream=false、/ai/plan、/ai/plan/{id}/adopt、/ai/summary/daily、monthly/yearly + 轮询 | 规划类消息走 /ai/plan（右侧建议面板 + 采纳/批量采纳），其余走对话；月/年报告异步轮询 |
| 商城 | /mall/items、/mall/purchase、/users/wardrobe、/users/wardrobe/active | 列表无 owned/isActive/color 字段，本地由 wardrobe 装饰 + 按类型映射颜色（不改模板） |
| 通知 | /notifications、/{id}/read、/read-all、/settings | 未读数 computed |
| 个人 | /users/me GET+PUT、/notifications/settings | 昵称/邮箱保存、提醒设置真实持久化 |

其余说明：
- 登录页提示文案由"演示环境，输入任意用户名密码即可进入"改为"还没有账号？点击上方「注册」创建账号"
  （仅文案，非设计；原文案与新行为不符）。
- 后端 ScheduleVO 无 timeRecords 字段，详情弹窗中计时记录区块 v-if 自动隐藏，模板未改。

## 验证（全部通过）

1. 静态资源：GET / → legacy 页面（title/文案/引用正确）；/css/style.css 200（19838B）、/js/app.js 200（38194B）。
2. 接口链路（curl 实测，11 个接口全部 code=0）：login / users/me / schedules / calendar /
   statistics(summary+trend+tags) / notifications / mall/items / wardrobe / ai/reports / notifications/settings。
3. AI 规划实测：/ai/plan 返回 6 条真实建议（DeepSeek key 已配置）。
4. 浏览器级（bu 实测）：legacy 登录页正常渲染 → probe_ok/pass12345 登录成功 →
   工作台显示真实数据（0 日程/0 积分）→ 新建日程「【验证】接口对接测试」→ 列表实时出现 →
   UI 删除 → 列表空 + 后端确认无残留（schedules total=0）。

## 待办/已知

- `frontend/node_modules`、`frontend/dist`（旧 Vite 构建缓存）仍留在 frontend/ 根目录（已被 .gitignore 忽略），可随时手动删除。
- 计时「已暂停」状态下刷新页面，秒数从 0 重新显示（后端未持久化 paused 秒数，非阻塞）。
