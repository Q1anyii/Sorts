# 2026-09-17 锦市装扮实际运用：恢复默认、头像框、徽章

## 背景

用户反馈：「购买了皮肤后默认皮肤就没了？还有勋章、头像框等都没有被实际运用到」。

排查结论：

1. **后端购买不自动上身**（符合预期）：`PurchasePersister.persist()` 写 `t_wardrobe_item` 时 `setIsActive(0)`，购买从不自动激活；`WardrobeServiceImpl.activate()` 已具备同类型互斥（deactivateByType + activate）。
2. **前端 AVATAR / BADGE 完全零运用**：锦市/云裳阁只展示「使用中」标记，头像框不会渲染到头像上，徽章不会显示为角标/昵称标记。
3. **SKIN 有换肤但无恢复默认入口**：`applySkinFromWardrobe` + `localStorage 'sorts.skin'` 启动缓存，一旦启用皮肤就无法一键回默认，观感即「默认皮肤没了」。

## 修复

### 后端（sorts-mall）

- 新增 `PUT /api/v1/users/wardrobe/deactivate`，body `{ "type": "SKIN|AVATAR|BADGE" }`，type 为空表示全部卸下（含恢复默认皮肤）。
- `WardrobeService.deactivate` / `WardrobeServiceImpl.deactivate`：按类型 `deactivateByType`，空则 `deactivateAll`。
- `WardrobeItemMapper.deactivateAll`（复用 `deactivateByType` 的 UPDATE 写法）。
- 新增 DTO `DeactivateRequest`（type 可空）。
- mall 编译 BUILD SUCCESS。

### 前端（vite-app 主版）

- `app-logic.ts`：新增 `activeAvatar` / `activeBadge` computed（从 `wardrobe` 找 `isActive` 的 AVATAR / BADGE 项）、`deactivateWardrobe(type)` 调用后端接口后刷新商城数据，并补齐 export。
- `index.html`：
  - 侧边栏与设置页头像加 `has-frame frame-N`（按 activeAvatar 商品 id 取框样式）、`avatar-badge` 徽章角标、`mini-badge-name` 昵称旁徽章名。
  - 云裳阁图标区分 SKIN / AVATAR / BADGE，使用中行按类型显示「恢复默认（SKIN）/ 卸下（AVATAR/BADGE）」按钮。
- `style.css`：`.has-frame::before` 渐变环（frame-3 织梭蓝线性渐变、frame-4 鎏金渐变）、`::after` 内圈白边、`.avatar-badge` 角标、`.mini-badge-name` 标签。

### 顺手修掉的白屏 bug

模板里对象字面量计算键写法 `:class="{'has-frame': x, 'frame-'+id: y}"` 被 Vue 模板编译器判为非法表达式，运行时报 `Uncaught SyntaxError: Unexpected token '+'`，页面白屏（此前织历/锦市偶发空白即此因之一）。改为三元字符串拼接：

```html
:class="activeAvatar ? 'has-frame frame-' + activeAvatar.item.id : ''"
```

## 验证（浏览器实测 + DB 断言）

- 启用「梭影·头像框」→ 侧边栏头像 class 变为 `user-avatar has-frame frame-3`，锦市按钮「使用中」。
- 购买「掌灯人徽章」（50 光阴砂，余额 4925→4875）→ 使用 → DOM 确认 `avatar-badge`（title=徽章：掌灯人徽章）+ `mini-badge-name`（掌灯人徽章）。
- 卸下徽章：PUT /wardrobe/deactivate 200 → 角标/标签消失、按钮回「使用」；DB `t_wardrobe_item.is_active = 0` 确认。
- 启用「织锦流光·皮肤」→ `data-skin='brocade'` + localStorage 缓存；点「恢复默认」→ `data-skin` 移除、按钮回「使用」。
- 中间发现测试会话 token 被多次 reload 轮换耗尽导致 loadMallData 401 假阴性，重新登录干净会话后全部通过。

## 未做 / 说明

- 前端 vitest 未跑：本次不涉及 datetime/status/error/sse 等被测模块，跳过（已在交付说明中提及）。
- 「恢复默认」在锦市页无入口，统一收敛在云裳阁（设置页），符合「衣橱管理」的产品定位。
