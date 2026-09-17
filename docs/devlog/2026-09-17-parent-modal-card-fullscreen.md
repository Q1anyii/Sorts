# Devlog · 2026-09-17 · 主计划弹窗卡片化 + 全屏遮罩（Teleport）

## 需求
1. 用户反馈主计划相关弹窗（新建/编辑主计划、添加子计划）**没有卡片白底**，要求对标「新建日程」弹窗的白色卡片样式。
2. 随后反馈主计划弹窗的遮罩**只覆盖主内容区、左侧导航栏未被屏蔽**，要求像日程弹窗一样**屏蔽整个页面**。

## 根因
1. 模板弹窗类名为 `class="modal"`，但 CSS 中只有 `.modal-mask` / `.modal-box` 的定义，**`.modal` 卡片样式缺失** → 弹窗无背景、透出遮罩后的页面文字。
2. 主计划弹窗位于 `.page-view`（带 `animation: page-in ... both`）内，动画填充期使祖先产生 transform 层，**`position: fixed` 的遮罩被约束在内容区**，无法覆盖左侧导航栏。

## 实现
### 卡片白底（style.css）
- `.modal-box, .modal` 共用卡片样式：`background: var(--bg-card)`、圆角 14px、阴影、max-height 85vh
- `.modal` 补充 `padding: 22px 24px`

### 全屏遮罩（index.html）
- 新建/编辑主计划弹窗、添加子计划弹窗分别用 `<teleport to="body">` 包裹，脱离 `.page-view` 的 transform 约束，遮罩恢复全视口

### 附带
- 主计划主题色改为「自动 + tagColors 8 色」圆形色块选择组（对标日程表单颜色标记），保存空色传 `null` 走后端默认
- 添加子计划列表项增加日程颜色标记点（`c.color || '#8FB8DE'`）
- 遮罩维持常规 55% + blur 4px

## 验证（浏览器端到端）
- 新建主计划弹窗：`cardBg rgb(255,255,255)`、radius 14px ✓
- 主题色选择组：8 个 tagColors 色块 + 「自动」，点击色块后自动态切换 ✓
- 添加子计划弹窗：9 个候选均带色点 ✓
- Teleport 后：遮罩 `left 0 / top 0 / right 801 / bottom 859`（全视口）、parentTag BODY ✓，截图背景整页（含导航栏）模糊变暗，与新建日程弹窗一致 ✓

## 变更文件
- `frontend/vite-app/index.html`：主计划两弹窗 Teleport、主题色选择组、子计划列表色点
- `frontend/vite-app/src/legacy/style.css`：`.modal` 卡片样式
- `frontend/vite-app/src/legacy/app-logic.ts`：编辑回填空色、保存 `color || null`

## 备注
- 未提交前完成 git 提交（先提交不推送）。
