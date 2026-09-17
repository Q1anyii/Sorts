# 2026-09-17 用户自定义头像 / 全局穿梭气泡 / AI 今日总结入织史 / 即将到来详情

## 一、用户自定义头像上传

### 变更
- **sorts-user**：`UserService` / `UserServiceImpl` / `UserController`
  - `POST /api/v1/users/avatar/upload`（鉴权 + multipart，参数 `file`）→ 返回 UserVO
  - `GET /api/v1/users/avatar/files/{filename}`（网关白名单免鉴权，扩展名映射 Content-Type，Cache-Control 7 天）
  - 落盘目录：容器内 `/app/data/avatar`（卷 `sorts-user-avatar`），URL 前缀常量 `AVATAR_URL_PREFIX=/api/v1/users/avatar/files/`
  - 校验：2MB 上限、PNG/JPG/GIF/WebP Content-Type 白名单、扩展名由 Content-Type 推导、路径穿越校验 `target.startsWith(dir)`；旧头像仅当 URL 为本站前缀且文件名正则白名单时才删除，失败不阻塞主流程
- **sorts-gateway**：白名单（application.yml + `SortsGatewayProperties.java`）放行 `/api/v1/users/avatar/files/**`（只放静态读，**不放上传**）
- **docker/compose.yml**：user 服务 `USER_AVATAR_DIR=/app/data/avatar` + 卷 `sorts-user-avatar`（顶层 volumes 段登记）
- **docker/Dockerfile.backend**：`mkdir -p /app/data/avatar && chown -R sorts:sorts /app/data`（非 root 运行需要）
- **frontend**：设置页「上传头像」UI（`.profile-avatar-box` + 隐藏 file input）、侧栏用户头像 img、`uploadAvatar/onAvatarFileChange`（独立 FormData fetch）

### 踩坑记录（重要）
1. **白名单路径过宽导致 500**：初版把整个 `/api/v1/users/avatar/**` 加进网关白名单，网关不解析 JWT → 不注入 `X-User-Id` → user 抛 `MissingRequestHeaderException` 500。修复：上传 `/avatar/upload` 走鉴权、静态 `/avatar/files/{filename}` 走白名单，路径拆分。
2. **compose YAML anchor merge 覆盖 environment**：user 服务用 `<<: *service` 锚点后自定义 `environment:` 只写新增项，会把锚点整组 environment 覆盖（NACOS_ADDR 等全丢）→ `serverAddr='null'` 注册失败 + `failFast=true` 启动失败，容器 Restarting 循环。修复：environment 写全锚点全部变量 + `USER_AVATAR_DIR`。
3. **命名卷属主**：新卷首次挂载时目录 root 所有，容器内 sorts 用户（uid 10001）落盘 `Permission denied`。修复：Dockerfile 建 `/app/data/avatar` 并 chown，删除旧卷 `sorts_sorts-user-avatar` 后重建让卷从镜像初始化属主。

## 二、全局穿梭气泡（今日经纬日程跨页面悬浮）

- `frontend/index.html` + `css/style.css`：aside 之后、main 之前插入 `.global-timer-bubble`（`v-if="currentPage!=='dashboard' && activeSchedule && (IN_PROGRESS||PAUSED)"`），含 gtb-time（formatTimer(elapsedSeconds)）、gtb-title、gtb-actions 四按钮（⏸暂停/▶续梭/⏹落梭/✕取消）
- 样式：`position:fixed; top:14px; left:50%; translateX(-50%); z-index:999` 渐变胶囊；`@keyframes gtb-breathe` 2.2s 呼吸光晕（仅 `.is-running` 播放，`prefers-reduced-motion` 降级）
- 交互：按钮 `@click.stop`；气泡本体 `@click="currentPage='dashboard'"`（点击任意非按钮区域跳回今日经纬）
- **今日经纬页隐藏**：主面板已有 active-timer，气泡在 dashboard 页不重复显示

### 计时跳变 Bug（2:16 → 8 分钟）修复
- **根因**：后端 `TimerServiceImpl.resume` 只置 status=IN_PROGRESS 并 openSegment，**未重置 `actualStartTime`**（仍是首次开梭时刻）；前端 `elapsedSeconds` 为自愈 computed = `actualDuration + (now - actualStartTime)`，续梭后把暂停时段也计入 → 显示暴涨。
- **修复**：
  - `resume`：`schedule.setActualStartTime(now)`（新片段从续梭时刻起算）
  - `pause`：`schedule.setActualStartTime(null)`（已结算片段，暂停期无活动片段，语义清晰）
- **验证**：暂停显示 00:01:05 静止 8 秒仍 00:01:05，续梭 3 秒后 00:01:06（不含暂停 8 秒）。

## 三、AI 今日总结自动入织史

- `frontend/js/app.js`：`sendAiMessage` 在 isPlanning 分支后、普通 SSE 前插入 **isSummary 正则分支**（今日/今天/当日/当天 × 总结/汇总），命中则 `POST /ai/summary/daily?stream=false`（body `{date:今日}`），成功后 bot 消息显示 title+content、挂 suggestedActions VIEW_REPORTS、调 `loadReports()` 刷新织史、`notifySuccess('今日总结已自动保存到织史')`
- 后端 `ReportServiceImpl.generateDaily` 本就生成即落库（织史即 /ai/reports 列表），无需改后端
- 验证：输入「生成今日总结」→ bot 输出完整日总结（整体评价/时间分配/亮点/改进建议，内容基于真实日程明细），织史列表实时出现「每日 2026-09-17 日总结」

## 四、即将到来点击详情

- `frontend/index.html`：today 页「即将到来」`schedule-item` 加 `@click="openScheduleDetail(s)"` + `cursor:pointer`，卡片展示 description（无则「点击查看详情」占位）
- 弹窗复用全局日程详情（状态/优先级/描述/计划时间/预计时长/标签 + 开梭/取消/编辑按钮），与织程列表行为一致
- 验证：点击卡片弹出详情，内容字段齐全

## 五、验证证据
- 头像：upload 200 → `/api/v1/users/avatar/files/u5_*.png`，静态 GET 无鉴权 200（1231 字节一致）；侧栏与设置页 img 均渲染
- 气泡：织历页置顶显示 00:00:11 + 呼吸（is-running）；dashboard 页隐藏；真实鼠标点击气泡空白 → #/dashboard + active-timer 出现
- AI 总结：织史列表出现对应日总结条目
- upcoming：点击卡片弹详情成功
