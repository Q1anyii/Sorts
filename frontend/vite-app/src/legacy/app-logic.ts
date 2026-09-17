/**
 * 梭子 SORTS · 主版前端（legacy-demo 提升）数据层
 * ----------------------------------------------------------------
 * 设计（index.html / css/style.css）保持不动，本文件仅替换数据来源：
 *   - 所有数据均来自真实后端接口（网关 8080 / 经 nginx /api 反代）
 *   - 令牌：localStorage 复用 Vite 版键名 sorts.access / sorts.refresh
 *   - 40101/40102/HTTP 401 → 无感续期（单飞）→ 重放一次；续期失败回登录页
 * 契约真源：frontend/vite-app/src/types/index.ts 与 api-spec.json
 */
// @ts-nocheck
/* 主版前端逻辑（由 frontend/js/app.js 迁入 vite-app，作为 legacy 模块） */
// 模板内联在 index.html #app，需完整版（含运行时编译器）
import { createApp, ref, reactive, computed, onMounted, onUnmounted, watch, nextTick } from 'vue/dist/vue.esm-bundler.js';
import { marked } from 'marked';
import hljs from 'highlight.js';

/* ================================================================
 * 基础设施：API 客户端 + 令牌
 * ================================================================ */
const API_BASE = '/api/v1';
const TOKEN_KEYS = { access: 'sorts.access', refresh: 'sorts.refresh' };

function getTokens() {
  return { access: localStorage.getItem(TOKEN_KEYS.access), refresh: localStorage.getItem(TOKEN_KEYS.refresh) };
}
function saveTokens(t) {
  localStorage.setItem(TOKEN_KEYS.access, t.accessToken);
  localStorage.setItem(TOKEN_KEYS.refresh, t.refreshToken);
}
function clearTokens() {
  localStorage.removeItem(TOKEN_KEYS.access);
  localStorage.removeItem(TOKEN_KEYS.refresh);
}

/* ================================================================
 * 统一提示：NotifyModal（错误/确认）+ Toast（成功/警告）
 * ----------------------------------------------------------------
 * 约定：用户可见的报错/成功/警告一律走本组件，不再依赖 alert / confirm /
 * 裸 console。生产环境（非 localhost）不展示堆栈等敏感信息。
 * ================================================================ */
const IS_DEV = !location.hostname || location.hostname === 'localhost' || location.hostname === '127.0.0.1';

const notify = reactive({
  visible: false, type: 'info', title: '', summary: '', detail: '', detailOpen: false,
  confirmText: '知道了', cancelText: '取消', _resolve: null, _autoClose: null
});
const toasts = ref([]);
let toastSeq = 0;

function dismissToast(id) {
  const i = toasts.value.findIndex(t => t.id === id);
  if (i >= 0) toasts.value.splice(i, 1);
}
function toast(text, type = 'success') {
  const icons = { success: '✅', warning: '⚠️', error: '❌', info: '💬' };
  const id = ++toastSeq;
  toasts.value.push({ id, text, type, icon: icons[type] || '💬' });
  setTimeout(() => dismissToast(id), 3200);
}
function notifyOk() {
  const r = notify._resolve;
  notify.visible = false;
  if (notify._autoClose) { clearTimeout(notify._autoClose); notify._autoClose = null; }
  notify._resolve = null;
  if (r) r(true);
}
function notifyCancel() {
  const r = notify._resolve;
  notify.visible = false;
  if (notify._autoClose) { clearTimeout(notify._autoClose); notify._autoClose = null; }
  notify._resolve = null;
  if (r) r(false);
}
function openNotify({ type = 'info', title = '提示', summary = '', detail = '', confirmText = '知道了', cancelText = '取消', autoClose = false, ms = 2500 }) {
  notify.type = type; notify.title = title; notify.summary = summary;
  notify.detail = detail; notify.detailOpen = false;
  notify.confirmText = confirmText; notify.cancelText = cancelText;
  notify.visible = true;
  if (notify._autoClose) { clearTimeout(notify._autoClose); notify._autoClose = null; }
  if (autoClose) notify._autoClose = setTimeout(() => notifyCancel(), ms);
  return new Promise(resolve => { notify._resolve = resolve; });
}
function notifySuccess(text) { toast(text, 'success'); }
function notifyWarning(text) { toast(text, 'warning'); }
/** 错误弹窗：摘要 + 详情（生产隐藏堆栈），开发环境保留 console 便于排障 */
function notifyError(err, fallback = '操作失败，请稍后再试') {
  const e = err || {};
  const message = (e && e.message) ? e.message : fallback;
  let detail = '';
  if (e && e.detail) detail = e.detail;
  else if (e && e.stack && IS_DEV) detail = e.stack;
  else if (IS_DEV && typeof e === 'object' && e.code !== undefined) detail = JSON.stringify(e, null, 2);
  if (IS_DEV) console.warn('[notify]', message, e);
  openNotify({ type: 'error', title: '操作失败', summary: message, detail });
}
/** 确认弹窗：resolve(true/false) */
function notifyConfirm({ title = '确认操作', message = '', confirmText = '确认', cancelText = '取消' }) {
  return openNotify({ type: 'confirm', title, summary: message, confirmText, cancelText });
}
function copyNotifyDetail() {
  if (navigator.clipboard && notify.detail) {
    navigator.clipboard.writeText(notify.detail).then(() => toast('已复制错误详情', 'info')).catch(() => {});
  }
}

let refreshPromise = null;
/** 无感续期：并发 401 只刷一次，其余请求挂同一 Promise */
function refreshTokens() {
  if (refreshPromise) return refreshPromise;
  const { refresh } = getTokens();
  if (!refresh) return Promise.reject({ code: 40103, message: '登录状态已失效，请重新登录' });
  refreshPromise = fetch(`${API_BASE}/auth/refresh`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ refreshToken: refresh })
  })
    .then(parseResult)
    .then((t) => { saveTokens(t); return t.accessToken; })
    .catch((e) => { clearTokens(); throw e; })
    .finally(() => { refreshPromise = null; });
  return refreshPromise;
}

/** 统一响应解析：code===0 解包 data，否则抛 {code,message,status} */
async function parseResult(resp) {
  let body;
  try { body = await resp.json(); } catch (e) { throw { code: -1, message: '网络异常，请检查连接后重试', status: resp.status }; }
  if (body && typeof body === 'object' && typeof body.code === 'number') {
    if (body.code === 0) return body.data;
    throw { code: body.code, message: body.message || '请求失败', status: resp.status };
  }
  throw { code: -1, message: '响应格式异常', status: resp.status };
}

async function apiFetch(path, opts = {}) {
  const { method = 'GET', body, params, skipAuth = false, retried = false, silent = false } = opts;
  const url = new URL(API_BASE + path, location.origin);
  if (params) {
    for (const k in params) {
      const v = params[k];
      if (v === undefined || v === null || v === '') continue;
      url.searchParams.set(k, v);
    }
  }
  const headers = { 'Content-Type': 'application/json' };
  const access = getTokens().access;
  if (access && !skipAuth) {
    // 幂等：存量令牌可能已带 "Bearer " 前缀（旧版后端签发），二次拼接会被网关按 40102 拒绝
    headers['Authorization'] = access.startsWith('Bearer ') ? access : 'Bearer ' + access;
  }
  // 请求超时：15s 未响应按网络异常统一提示（AbortController 中止底层 fetch）
  const controller = new AbortController();
  const timeoutTimer = setTimeout(() => controller.abort(), 15000);
  let resp;
  try {
    resp = await fetch(url, { method, headers, body: body ? JSON.stringify(body) : undefined, signal: controller.signal });
  } catch (e) {
    if (e && e.name === 'AbortError') {
      if (!silent) notifyError({ message: '请求超时，请检查网络后重试' });
      throw { code: -1, message: '请求超时，请检查网络后重试' };
    }
    if (!silent) notifyError({ message: '网络异常，请检查连接后重试' });
    throw { code: -1, message: '网络异常，请检查连接后重试' };
  } finally {
    clearTimeout(timeoutTimer);
  }
  let parsed;
  try { parsed = await parseResult(resp); } catch (e) { parsed = e; }
  if (parsed && typeof parsed === 'object' && parsed.code !== undefined && parsed.code !== 0) {
    // 令牌失效 → 无感续期后重放一次（白名单路径不再重试）
    if (!skipAuth && !retried && (parsed.code === 40101 || parsed.code === 40102 || resp.status === 401)) {
      try {
        await refreshTokens();
        return apiFetch(path, { ...opts, retried: true });
      } catch (e2) {
        sessionExpired();
        throw e2;
      }
    }
    if (!silent) notifyError(parsed);
    throw parsed;
  }
  return parsed;
}

/* ================================================================
 * 基础设施：SSE 客户端（fetch + ReadableStream 手写解析）
 * ----------------------------------------------------------------
 * 契约（README §AI 流式）：/ai/chat 默认 SSE（?stream=false 走 JSON），事件为
 *   delta（正文增量 {"content":"..."}）/ done（最终结果）/ error（{"code","message"}）。
 * EventSource 不支持 POST 与自定义鉴权头，故用 fetch 手写解析；
 * splitFrames 兼容「一包多帧 / 跨包半帧 / \r\n」三种情况（与 Vite 存档版 sse.ts 同源）。
 * ================================================================ */
function splitFrames(buffer) {
  const frames = [];
  let rest = buffer;
  for (;;) {
    const idx = rest.search(/\r?\n\r?\n/);
    if (idx === -1) break;
    const raw = rest.slice(0, idx);
    const sepLen = rest.slice(idx).match(/^\r?\n\r?\n/)[0].length;
    rest = rest.slice(idx + sepLen);
    let event = 'message';
    const dataLines = [];
    for (const line of raw.split(/\r?\n/)) {
      if (line.startsWith('event:')) event = line.slice(6).trim();
      else if (line.startsWith('data:')) dataLines.push(line.slice(5).replace(/^ /, ''));
    }
    if (dataLines.length > 0) frames.push({ event, data: dataLines.join('\n') });
  }
  return { frames, rest };
}

function dispatchSseFrame(frame, handlers) {
  if (!frame || frame.data === undefined) return;
  let payload;
  try { payload = JSON.parse(frame.data); } catch (e) { return; } // 坏帧跳过，不中断整个流
  if (frame.event === 'delta' && payload.content != null) {
    if (handlers.onDelta) handlers.onDelta(payload.content);
  } else if (frame.event === 'done') {
    if (handlers.onDone) handlers.onDone(payload);
  } else if (frame.event === 'error') {
    if (handlers.onError) handlers.onError({ code: payload.code, message: payload.message || 'AI 服务异常' });
  }
}

/**
 * POST + SSE 流式对话（带 401 无感续期重放，与 apiFetch 同一套令牌策略）
 * @param {string} path API 路径（不含 /api/v1 前缀）
 * @param {object} body 请求体
 * @param {{onDelta?:Function, onDone?:Function, onError?:Function, onAuthExpired?:Function}} handlers 事件回调
 * @param {AbortSignal} [signal] 用户主动停止生成
 */
async function sseChat(path, body, handlers, signal) {
  const url = API_BASE + path;
  const doFetch = (token) => fetch(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
      // 幂等：存量令牌可能已带 "Bearer " 前缀（旧版后端签发），二次拼接会被网关按 40102 拒绝
      ...(token ? { Authorization: token.startsWith('Bearer ') ? token : 'Bearer ' + token } : {})
    },
    body: JSON.stringify(body),
    signal
  });
  let resp;
  try { resp = await doFetch(getTokens().access); }
  catch (e) {
    if (e.name !== 'AbortError' && handlers.onError) handlers.onError({ message: '网络异常，请检查连接后重试' });
    return;
  }
  // 令牌失效：单飞续期后重放一次；续期失败交由调用方处理（回登录页）
  if (resp.status === 401 && getTokens().refresh) {
    try {
      await refreshTokens();
      resp = await doFetch(getTokens().access);
    } catch (e) {
      if (handlers.onAuthExpired) handlers.onAuthExpired();
      return;
    }
  }
  if (!resp.ok || !resp.body) {
    let message = '请求失败，请稍后再试';
    try { const j = await resp.json(); if (j && j.message) message = j.message; } catch (e) { /* 非 JSON 保留兜底文案 */ }
    if (handlers.onError) handlers.onError({ message, code: resp.status });
    return;
  }
  const reader = resp.body.getReader();
  const decoder = new TextDecoder('utf-8');
  let buffer = '';
  try {
    for (;;) {
      const { done, value } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });
      const { frames, rest } = splitFrames(buffer);
      buffer = rest;
      for (const frame of frames) dispatchSseFrame(frame, handlers);
    }
    // 流末尾可能有没有空行结尾的残帧，兜底冲一次
    if (buffer.trim()) {
      const { frames } = splitFrames(buffer + '\n\n');
      for (const frame of frames) dispatchSseFrame(frame, handlers);
    }
  } catch (e) {
    if (e.name !== 'AbortError' && handlers.onError) handlers.onError({ message: '连接中断，请重试' });
  }
}

/* ================================================================
 * 基础设施：Markdown 渲染（marked + highlight.js 本地化，无 CDN 依赖）
 * ----------------------------------------------------------------
 * 安全约束：AI 输出不可信——
 *   1. 原始 HTML 一律转义（renderer.html 兜底），防止模型输出注入脚本；
 *   2. 链接/图片仅放行 http/https/mailto/tel/#，拦截 javascript: / data: 等伪协议；
 *   3. 代码块经 escapeHtml 转义后进入 <pre>，highlight.js 只做语法着色。
 * ================================================================ */
function escapeHtml(text) {
  const div = document.createElement('div');
  div.textContent = text == null ? '' : String(text);
  return div.innerHTML;
}
function sanitizeLink(url) {
  const u = String(url || '').trim().replace(/[\u0000-\u001F\u007F]/g, '');
  if (/^(javascript|data|vbscript):/i.test(u)) return '#';
  return u;
}
function renderMarkdown(text) {
  if (!text) return '';
  if (typeof marked === 'undefined') return escapeHtml(text); // 依赖加载失败：退化为纯文本
  // 预处理：压缩连续空行（3 个以上换行→2 个）、去除行尾空格，避免 AI 输出大量空行
  const cleaned = text
    .replace(/\r\n/g, '\n')
    .replace(/[ \t]+\n/g, '\n')
    .replace(/\n{3,}/g, '\n\n');
  const renderer = new marked.Renderer();
  // 原始 HTML 一律转义（防 XSS）
  renderer.html = function (html) { return escapeHtml(html); };
  renderer.link = function (href, title, content) {
    const safe = sanitizeLink(href);
    const t = title ? ' title="' + escapeHtml(title) + '"' : '';
    return '<a href="' + safe + '" target="_blank" rel="noopener noreferrer"' + t + '>' + content + '</a>';
  };
  renderer.image = function (href, title, text2) {
    const safe = sanitizeLink(href);
    const t = title ? ' title="' + escapeHtml(title) + '"' : '';
    return '<img src="' + safe + '" alt="' + escapeHtml(text2) + '"' + t + ' loading="lazy">';
  };
  // 自定义代码块：带语言标题栏 + 复制按钮的窗口（类名与 style.css 严格对齐）
  renderer.code = function (codeOrObj, langOrUndef) {
    // 兼容 marked v12 新版对象形态（{text, lang}）与旧式三参数形态
    let codeText, lang;
    if (codeOrObj !== null && typeof codeOrObj === 'object') { codeText = codeOrObj.text || ''; lang = codeOrObj.lang; }
    else { codeText = codeOrObj || ''; lang = langOrUndef; }
    const language = lang || 'code';
    return '<div class="code-block-wrapper">' +
      '<div class="code-block-header">' +
      '<span class="code-block-lang">' + escapeHtml(language) + '</span>' +
      '<button type="button" class="code-block-copy" data-copy-code title="复制代码">' +
      '<svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path></svg>' +
      '</button></div>' +
      '<pre><code class="language-' + escapeHtml(language) + '">' + escapeHtml(codeText) + '</code></pre>' +
      '</div>';
  };
  marked.setOptions({ gfm: true, breaks: false, renderer: renderer });
  let html = marked.parse(cleaned);
  // highlight.js 代码高亮（渲染后处理；单块失败不影响正文）
  if (typeof hljs !== 'undefined') {
    const tmp = document.createElement('div');
    tmp.innerHTML = html;
    tmp.querySelectorAll('pre code').forEach(block => { try { hljs.highlightElement(block); } catch (e) { /* 忽略 */ } });
    html = tmp.innerHTML;
  }
  return html;
}
// 代码块复制：全局事件委托（不依赖内联 onclick 全局函数，与 Mitta 参考同思路但更稳）
document.addEventListener('click', function (e) {
  const btn = e.target && e.target.closest ? e.target.closest('[data-copy-code]') : null;
  if (!btn) return;
  const wrapper = btn.closest('.code-block-wrapper');
  const code = wrapper && wrapper.querySelector('code');
  if (!code) return;
  navigator.clipboard.writeText(code.innerText).then(() => {
    const original = btn.innerHTML;
    btn.innerHTML = '✓ 已复制';
    setTimeout(() => { btn.innerHTML = original; }, 1500);
  }).catch(() => { /* 剪贴板不可用时静默失败 */ });
});

/* ================================================================
 * 基础设施：主题皮肤（锦市 SKIN 商品 → 前端换肤）
 * ----------------------------------------------------------------
 * 键与 scripts/sql/sorts_mall.sql 种子商品 ID 一一对应（种子用显式主键
 * + INSERT IGNORE，ID 稳定可依赖）；CSS 侧由 html[data-skin] 驱动变量覆盖。
 * 皮肤清单：brocade 织锦流光 / night 星夜梭影 / ocean 沧浪青岚 /
 *           sunset 霞光织锦 / forest 竹影幽篁 / ink 墨韵流年 / blossom 樱色入梦
 * ================================================================ */
const SKIN_THEME_MAP = { 1: 'brocade', 2: 'night', 8: 'ocean', 9: 'sunset', 10: 'forest', 11: 'ink', 12: 'blossom' };
const SKIN_KEYS = Object.keys(SKIN_THEME_MAP).map(k => SKIN_THEME_MAP[k]);
const SKIN_STORAGE_KEY = 'sorts.skin';

/** 应用皮肤：html[data-skin="key"] 驱动 CSS 变量覆盖；非内置 key 一律回退默认皮肤 */
function applySkin(key) {
  const safe = SKIN_KEYS.indexOf(key) >= 0 ? key : '';
  if (safe) {
    localStorage.setItem(SKIN_STORAGE_KEY, safe);
    document.documentElement.setAttribute('data-skin', safe);
  } else {
    localStorage.removeItem(SKIN_STORAGE_KEY);
    document.documentElement.removeAttribute('data-skin');
  }
}

/** 从云裳阁找到「使用中」的皮肤商品并应用（云裳阁 = 服务端真源） */
function applySkinFromWardrobe(wardrobe) {
  const list = wardrobe || [];
  let key = '';
  for (const w of list) {
    if (!w || !w.isActive || !w.item || w.item.type !== 'SKIN') continue;
    key = SKIN_THEME_MAP[w.item.id] || '';
    break;
  }
  applySkin(key);
  return key;
}

const app = createApp({
  setup() {
    /* ============================================================
     * Auth State
     * ============================================================ */
    const authMode = ref('login');
    const authForm = reactive({ username: '', password: '', email: '', nickname: '' });
    const authLoading = ref(false);
    const isLoggedIn = ref(false);
    const userInfo = reactive({
      id: 0, username: '', nickname: '', email: '', phone: '',
      avatarUrl: '', points: 0, createdAt: ''
    });

    // ============ Navigation（hash 路由 + 刷新保持） ============
    const currentPage = ref('dashboard');

    const VALID_PAGES = ['dashboard', 'calendar', 'schedules', 'ai', 'reports', 'stats', 'mall', 'notifications', 'profile'];
    const SS_PREFIX = 'sorts.ss.';
    function parseHash() {
      const h = location.hash.replace(/^#\/?/, '');
      const [pagePart, queryPart] = h.split('?');
      const page = VALID_PAGES.indexOf(pagePart) >= 0 ? pagePart : '';
      const params = {};
      if (queryPart) new URLSearchParams(queryPart).forEach((v, k) => { params[k] = v; });
      return { page, params };
    }
    /** 页面跳转：设置当前页 + 写入 hash（刷新后由 parseHash 恢复，天然避免刷新 404） */
    function navigateTo(page, params = {}) {
      currentPage.value = page;
      const q = new URLSearchParams(params);
      location.hash = q.toString() ? `#/${page}?${q}` : `#/${page}`;
    }
    /** 应用路由参数：织历 → 织程跳转带 date、织历深链带 year/month */
    function applyRouteParams(params) {
      if (params.date) {
        scheduleFilter.preset = 'custom';
        scheduleFilter.startDate = params.date;
        scheduleFilter.endDate = params.date;
        selectedDayLabel.value = params.date;
        selectedDayDate.value = params.date;
      }
      if (params.year) calYear.value = parseInt(params.year, 10) || new Date().getFullYear();
      if (params.month) calMonth.value = Math.min(12, Math.max(1, parseInt(params.month, 10) || 1));
    }
    function savePageState(key, state) {
      try { sessionStorage.setItem(SS_PREFIX + key, JSON.stringify(state)); } catch (e) { /* 隐私模式等场景忽略 */ }
    }
    function loadPageState(key) {
      try { return JSON.parse(sessionStorage.getItem(SS_PREFIX + key) || 'null'); } catch (e) { return null; }
    }
    // 导航点击只改 currentPage → 同步 hash（带参数跳转走 navigateTo）
    function syncHashFromPage() {
      const { page } = parseHash();
      if (page !== currentPage.value) location.hash = `#/${currentPage.value}`;
    }

    // ============ Timer State ============
    // 注意：elapsedSeconds / timerInterval 定义在下方「计时状态机」段（自愈秒表）
    const activeSchedule = ref(null);

    // ============ Data Stores ============
    const schedules = ref([]);
    // 织历专用全量日程（不受织程日期筛选影响，保证日历任务点始终完整）
    const allSchedules = ref([]);
    const notifications = ref([]);
    const mallItems = ref([]);
    const wardrobe = ref([]);
    const aiReports = ref([]);

    // ============ Calendar State ============
    const calYear = ref(new Date().getFullYear());
    const calMonth = ref(new Date().getMonth() + 1);
    const monthPickerOpen = ref(false);      // 织历月份自定义选择器浮层
    const pickerYear = ref(new Date().getFullYear()); // 选择面板内预览年份（箭头切换不触发日历跳转）
    const selectedDaySchedules = ref(null);
    const selectedDayLabel = ref('');
    const selectedDayDate = ref('');

    // ============ AI State ============
    const aiInput = ref('');
    const aiMessages = ref([
      { role: 'bot', content: '你好！我是梭灵 🤖 我可以帮你：\n\n📅 **规划日程**：告诉我你的需求，我会生成结构化的日程安排\n📊 **效率分析**：分析你的时间使用情况\n📝 **生成总结**：每日/月度/年度智能总结\n\n试试对我说："明天上午学习Java 2小时，下午运动1小时"' }
    ]);
    const aiLoading = ref(false);
    const aiSuggestions = ref([]);
    let currentPlanId = null; // /ai/plan 返回的 planId，采纳时回传

    // ============ AI 流式状态（v2：SSE 打字机 + Markdown） ============
    const aiStreaming = ref(false);        // SSE 流进行中（驱动停止按钮与光标）
    const aiConversationId = ref('');      // 多轮上下文：done 事件回传，下轮请求带上（Redis 12h）
    const aiWriteEnabled = ref(false);     // 双钥匙第二把：用户确认后才置位 allowWrite=true
    let aiAbortController = null;          // 用户主动停止生成（abort 静默结束）
    let aiRenderTimer = null;              // 流式渲染节流器（~90ms 合并一次 Markdown 渲染）
    let aiLatestText = '';                 // 流式累计文本（与 botMsg.content 分离，节流写入）

    // ============ Stats ============
    const stats = reactive({
      totalSchedules: 0, completionRate: 0, totalFocusTime: 0, streakDays: 0
    });

    // ============ Filter State ============
    const scheduleFilter = reactive({
      status: '', priority: '', keyword: '',
      preset: '', startDate: '', endDate: ''   // 日期范围：预设 + 自定义
    });
    const mallTab = ref('all');

    // ============ 织程多选 / 批量删除 ============
    const selectedScheduleIds = ref([]);
    const deletingSchedules = ref(false);

    // ============ 织史多选 / 删除 ============
    const selectedReportIds = ref([]);
    const deletingReports = ref(false);

    // ============ AI 会话持久化 ============
    const conversations = ref([]);
    const currentConversationId = ref(null);
    const aiSaving = ref(false);
    let aiDraftTimer = null;
    // 规划面板：勾选 + 编辑
    const aiSelectedIndices = ref([]);
    const aiPlanRange = ref(null);
    const aiEditIndex = ref(-1);
    const aiEditForm = reactive({ title: '', suggestedStart: '', duration: 60 });
    const convSelectedIds = ref([]);

    // ============ AI 三栏面板：隐藏 / 拖拽调宽（适配窗口不留白不溢出） ============
    const aiPanels = reactive({ sidebar: true, chat: true, plans: true });
    const aiWidths = reactive({ sidebar: 250, plans: 340 });
    const AI_MIN_W = { sidebar: 180, plans: 260 };
    const AI_MAX_W = { sidebar: 420, plans: 640 };
    function toggleAiPanel(k) { if (k === 'chat') return; aiPanels[k] = !aiPanels[k]; } // 对话窗口始终显示，不可隐藏
    function showAllAiPanels() { aiPanels.sidebar = true; aiPanels.chat = true; aiPanels.plans = true; }
    /** 拖拽分隔条：sidebar 的条在面板右侧（向右拖变宽）；plans 的条在面板左侧（向右拖变窄） */
    function startAiResize(e, key) {
      if (e.button !== undefined && e.button !== 0) return;
      const startX = e.clientX;
      const startW = aiWidths[key];
      const neg = key === 'plans';
      const move = (ev) => {
        const delta = ev.clientX - startX;
        aiWidths[key] = Math.round(Math.min(AI_MAX_W[key], Math.max(AI_MIN_W[key], neg ? startW - delta : startW + delta)));
      };
      const up = () => {
        window.removeEventListener('mousemove', move);
        window.removeEventListener('mouseup', up);
        document.body.style.cursor = '';
        document.body.style.userSelect = '';
      };
      window.addEventListener('mousemove', move);
      window.addEventListener('mouseup', up);
      document.body.style.cursor = 'col-resize';
      document.body.style.userSelect = 'none';
    }

    // ============ Modal State ============
    const showScheduleModal = ref(false);
    const editingSchedule = ref(null);
    const scheduleDetail = ref(null);
    const purchaseConfirm = ref(null);
    const viewingReport = ref(null);
    const generatingReport = ref(false);
    const profileSaved = ref(false);

    const scheduleForm = reactive({
      title: '', description: '', plannedStartTime: '', plannedDuration: 60,
      priority: 'MEDIUM', tagsStr: '', color: '#4A6CF7'
    });

    const reminderSettings = reactive({
      defaultAdvanceMinutes: 15, quietHoursEnabled: false
    });

    /* ============================================================
     * Computed（与设计模板一一对应，数据源已切到真实 schedules）
     * ============================================================ */
    const todayStr = computed(() => new Date().toLocaleDateString('zh-CN', { year: 'numeric', month: 'long', day: 'numeric', weekday: 'long' }));

    // 今日经纬依赖「全量列表」而非织程筛选列表：织程/织历联动设置非今日筛选后，
    // 落梭等刷新若按筛选拉取会导致今日日程被清空（需手动刷新才恢复）——读 allSchedules 解耦
    const todaySchedules = computed(() => {
      const today = getTodayStr();
      return allSchedules.value.filter(s => s.plannedStartTime && s.plannedStartTime.startsWith(today));
    });

    const todayStats = computed(() => {
      const list = todaySchedules.value;
      return {
        total: list.length,
        completed: list.filter(s => s.status === 'COMPLETED').length,
        focusMinutes: Math.floor(list.reduce((sum, s) => sum + (s.actualDuration || 0), 0) / 60)
      };
    });

    const upcomingSchedules = computed(() => {
      const today = getTodayStr();
      return allSchedules.value
        .filter(s => s.plannedStartTime > today + 'T23:59' && s.status === 'PENDING')
        .slice(0, 5);
    });

    const filteredSchedules = computed(() => {
      let list = schedules.value;
      if (scheduleFilter.status) list = list.filter(s => s.status === scheduleFilter.status);
      if (scheduleFilter.priority) list = list.filter(s => s.priority === scheduleFilter.priority);
      if (scheduleFilter.keyword) {
        const kw = scheduleFilter.keyword.toLowerCase();
        list = list.filter(s => s.title.toLowerCase().includes(kw) || (s.description || '').toLowerCase().includes(kw));
      }
      // 日期范围（闭区间）：按 plannedStartTime 的日期部分比较，杜绝跨日/其他日期混入
      const start = scheduleFilter.startDate;
      const end = scheduleFilter.endDate;
      if (start || end) {
        list = list.filter(s => {
          const d = (s.plannedStartTime || '').slice(0, 10);
          if (!d) return false;
          if (start && d < start) return false;
          if (end && d > end) return false;
          return true;
        });
      }
      return list.sort((a, b) => (a.plannedStartTime || '').localeCompare(b.plannedStartTime || ''));
    });

    /** 按日期分组展示（织历→织程跳转 / 日期范围查询的落地形态） */
    const groupedSchedules = computed(() => {
      const map = new Map();
      filteredSchedules.value.forEach(s => {
        const d = (s.plannedStartTime || '').slice(0, 10);
        if (!map.has(d)) map.set(d, []);
        map.get(d).push(s);
      });
      return [...map.entries()].map(([date, items]) => ({ date, items }));
    });

    // ============ 织程多选状态 ============
    const allSchedulesChecked = computed(() => {
      const list = filteredSchedules.value;
      return list.length > 0 && list.every(s => selectedScheduleIds.value.includes(s.id));
    });
    const someSchedulesChecked = computed(() => {
      const list = filteredSchedules.value;
      return list.some(s => selectedScheduleIds.value.includes(s.id)) && !allSchedulesChecked.value;
    });
    function toggleAllSchedules() {
      if (allSchedulesChecked.value) selectedScheduleIds.value = [];
      else selectedScheduleIds.value = filteredSchedules.value.map(s => s.id);
    }
    function toggleScheduleSelect(id) {
      const i = selectedScheduleIds.value.indexOf(id);
      if (i >= 0) selectedScheduleIds.value.splice(i, 1);
      else selectedScheduleIds.value.push(id);
    }
    // 织史多选状态
    const allReportsChecked = computed(() => {
      return aiReports.value.length > 0 && aiReports.value.every(r => selectedReportIds.value.includes(r.id));
    });
    function toggleAllReports() {
      if (allReportsChecked.value) selectedReportIds.value = [];
      else selectedReportIds.value = aiReports.value.map(r => r.id);
    }
    function toggleReportSelect(id) {
      const i = selectedReportIds.value.indexOf(id);
      if (i >= 0) selectedReportIds.value.splice(i, 1);
      else selectedReportIds.value.push(id);
    }

    const unreadNotifCount = computed(() => notifications.value.filter(n => !n.isRead).length);

    const filteredMallItems = computed(() => {
      if (mallTab.value === 'all') return mallItems.value;
      return mallItems.value.filter(i => i.type === mallTab.value);
    });

    const calendarDays = computed(() => {
      const year = calYear.value, month = calMonth.value;
      const firstDay = new Date(year, month - 1, 1);
      const lastDay = new Date(year, month, 0);
      const startDayOfWeek = firstDay.getDay();
      const daysInMonth = lastDay.getDate();
      const t = new Date();
      const tStr = t.getFullYear() + '-' + String(t.getMonth() + 1).padStart(2, '0') + '-' + String(t.getDate()).padStart(2, '0');

      const scheduleMap = {};
      allSchedules.value.forEach(s => {
        if (s.plannedStartTime) {
          const d = s.plannedStartTime.slice(0, 10);
          if (!scheduleMap[d]) scheduleMap[d] = [];
          scheduleMap[d].push(s);
        }
      });

      // 调色板背景（圆形渐变画布）：每种颜色 = 一个随机落点的颜料点，圆形扩散，
      // 半径随数量占比变大（占比越大扩散越广），各层半透明叠加 → 颜色之间自然渐变过渡
      function hexToRgba(hex, alpha) {
        const h = String(hex || '').replace('#', '');
        if (h.length !== 6 && h.length !== 3) return hex;
        const full = h.length === 3 ? h.split('').map(x => x + x).join('') : h;
        const n = parseInt(full, 16);
        return `rgba(${(n >> 16) & 255}, ${(n >> 8) & 255}, ${n & 255}, ${alpha})`;
      }
      function buildPaletteBg(sc) {
        if (!sc.length) return null;
        const map = new Map();
        sc.forEach(s => {
          const c = scheduleDotColor(s);
          map.set(c, (map.get(c) || 0) + 1);
        });
        const entries = [...map.entries()];
        for (let i = entries.length - 1; i > 0; i--) {
          const j = Math.floor(Math.random() * (i + 1));
          [entries[i], entries[j]] = [entries[j], entries[i]];
        }
        const total = sc.length;
        const N = entries.length;
        // 互斥分区：按点数排 rows×cols 网格，每个颜色点固定落在独立单元内（单元内随机、单元间互斥）
        const rows = Math.ceil(Math.sqrt(N));
        const cols = Math.ceil(N / rows);
        const cells = [];
        for (let r = 0; r < rows; r++) for (let c = 0; c < cols; c++) cells.push([r, c]);
        for (let i = cells.length - 1; i > 0; i--) {
          const j = Math.floor(Math.random() * (i + 1));
          [cells[i], cells[j]] = [cells[j], cells[i]];
        }
        const layers = entries.map(([c, cnt], idx) => {
          const [r, cc] = cells[idx % cells.length];
          const cellW = 84 / cols, cellH = 84 / rows;
          const x = (8 + (cc + 0.5) * cellW + (Math.random() - 0.5) * cellW * 0.55).toFixed(1);
          const y = (8 + (r + 0.5) * cellH + (Math.random() - 0.5) * cellH * 0.55).toFixed(1);
          const rad = (75 + (cnt / total) * 55).toFixed(1); // 占比越大扩散越广 75%-130%，覆盖整格不露底
          return `radial-gradient(circle at ${x}% ${y}%, ${hexToRgba(c, .92)} 0%, ${hexToRgba(c, .5)} 42%, ${hexToRgba(c, 0)} ${rad}%)`;
        });
        layers.push('linear-gradient(135deg, #F7F8FC, #FDF7EF)'); // 柔和暖底
        return layers.join(', ');
      }

      const days = [];
      const prevLastDay = new Date(year, month - 1, 0).getDate();
      for (let i = startDayOfWeek - 1; i >= 0; i--) {
        const d = prevLastDay - i;
        const dm = month - 1; const dy = dm === 0 ? year - 1 : year; const m = dm === 0 ? 12 : dm;
        const ds = `${dy}-${String(m).padStart(2, '0')}-${String(d).padStart(2, '0')}`;
        const sc = scheduleMap[ds] || [];
        days.push({ key: 'p' + d, dayOfMonth: d, isToday: false, isCurrentMonth: false, dateStr: ds, totalCount: sc.length, paletteBg: buildPaletteBg(sc) });
      }
      for (let d = 1; d <= daysInMonth; d++) {
        const ds = `${year}-${String(month).padStart(2, '0')}-${String(d).padStart(2, '0')}`;
        const sc = scheduleMap[ds] || [];
        days.push({ key: 'c' + d, dayOfMonth: d, isToday: ds === tStr, isCurrentMonth: true, dateStr: ds, totalCount: sc.length, paletteBg: buildPaletteBg(sc) });
      }
      const remaining = 42 - days.length;
      for (let d = 1; d <= remaining; d++) {
        const dm = month + 1; const dy = dm === 13 ? year + 1 : year; const m = dm === 13 ? 1 : dm;
        const ds = `${dy}-${String(m).padStart(2, '0')}-${String(d).padStart(2, '0')}`;
        const sc = scheduleMap[ds] || [];
        days.push({ key: 'n' + d, dayOfMonth: d, isToday: false, isCurrentMonth: false, dateStr: ds, totalCount: sc.length, paletteBg: buildPaletteBg(sc) });
      }
      return days;
    });
    // 日程任务点分色：用户自定义颜色标记优先，否则按紧急度（柔和自然暖色调）
    const PRIORITY_DOT_COLOR = { URGENT: '#F49B8B', HIGH: '#F6C177', MEDIUM: '#8FB8DE', LOW: '#A9C3A3' };
    function scheduleDotColor(s) {
      if (s.color) return s.color;
      return PRIORITY_DOT_COLOR[s.priority] || '#4A6CF7';
    }
    /** 颜色加深（气泡渐变末端）：hex → 按 factor 加深的 rgb */
    function shadeColor(hex, factor) {
      const h = String(hex || '').replace('#', '');
      const full = h.length === 3 ? h.split('').map(x => x + x).join('') : h;
      if (full.length !== 6) return hex;
      const n = parseInt(full, 16);
      const r = Math.min(255, Math.round(((n >> 16) & 255) * factor));
      const g = Math.min(255, Math.round(((n >> 8) & 255) * factor));
      const b = Math.min(255, Math.round((n & 255) * factor));
      return `rgb(${r}, ${g}, ${b})`;
    }
    /** 全局气泡背景：跟随任务点颜色动态渐变（135deg 风格不变），深色端为同色系加深 */
    function bubbleStyle(s) {
      if (!s) return {};
      const c = s.color || PRIORITY_DOT_COLOR[s.priority] || '#4A6CF7';
      return { background: `linear-gradient(135deg, ${c} 0%, ${shadeColor(c, 0.68)} 100%)` };
    }
    // 织历月份下拉：覆盖近 5 年（当前年 ±2）
    const calendarMonthOptions = computed(() => {
      const y = new Date().getFullYear();
      const opts = [];
      for (let yy = y - 2; yy <= y + 2; yy++) {
        for (let m = 1; m <= 12; m++) opts.push({ value: yy + '-' + m, label: yy + '年' + m + '月' });
      }
      return opts;
    });
    function onCalendarMonthChange(e) {
      const v = e.target && e.target.value;
      if (!v) return;
      const parts = v.split('-').map(Number);
      calYear.value = parts[0];
      calMonth.value = parts[1] || 1;
    }

    // 7 日完成趋势：来自真实 /statistics/trend（后端返回 0-1 比例）
    const trendData = computed(() => {
      return _trendPoints.value.map(p => ({ day: dayLabel(p.date), completionRate: p.completionRate }));
    });
    const _trendPoints = ref([]);
    function dayLabel(dateStr) {
      const d = new Date(dateStr + 'T00:00:00');
      return ['日', '一', '二', '三', '四', '五', '六'][d.getDay()];
    }

    // 标签时间分布：来自真实 /statistics/tags（totalDuration 秒、percentage 0-1）
    const tagStats = ref([]);

    const dayHeaders = ['日', '一', '二', '三', '四', '五', '六'];
    const tagColors = ['#8FB8DE', '#A9D6B8', '#C3B091', '#F49B8B', '#C4B5FD', '#F9A8D4', '#9DB8E8', '#7FD1CC'];

    // ============ 工具 ============
    function formatTime(dt) { if (!dt) return ''; return dt.slice(11, 16); }
    function formatDate(dt) { if (!dt) return ''; const d = new Date(dt); return `${d.getMonth() + 1}/${d.getDate()}`; }
    function formatDateTime(dt) { if (!dt) return ''; const d = new Date(dt); return d.toLocaleString('zh-CN'); }
    function formatSeconds(s) { const m = Math.floor(s / 60); const sec = s % 60; return `${m}分${sec}秒`; }
    function formatMinutes(m) { if (m >= 60) return `${Math.floor(m / 60)}小时${m % 60}分`; return `${m}分钟`; }
    function formatTimer(s) { const h = Math.floor(s / 3600), m = Math.floor((s % 3600) / 60), sec = s % 60; return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}:${String(sec).padStart(2, '0')}`; }

    function statusLabel(s) {
      return { PENDING: '待开始', IN_PROGRESS: '进行中', PAUSED: '已暂停', COMPLETED: '已完成', CANCELLED: '已取消', TIMEOUT: '已超时' }[s] || s;
    }

    function getTodayStr() {
      const d = new Date();
      return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
    }

    function showError(e, fallback = '操作失败，请稍后再试') {
      notifyError(e, fallback);
    }

    /** 会话彻底失效（续期也被拒）：清令牌回登录页 */
    function sessionExpired() {
      clearTokens();
      localStorage.removeItem('shuttle_user');
      stopTimer();
      activeSchedule.value = null;
      isLoggedIn.value = false;
      notifyError({ message: '登录状态已失效，请重新登录' });
    }

    /* ============================================================
     * 认证（真实接口）
     * ============================================================ */
    async function handleAuth() {
      if (authLoading.value) return;
      authLoading.value = true;
      try {
        const isLogin = authMode.value === 'login';
        const body = isLogin
          ? { username: authForm.username.trim(), password: authForm.password }
          : { username: authForm.username.trim(), password: authForm.password, email: authForm.email.trim() || undefined, nickname: authForm.nickname.trim() || undefined };
        const token = await apiFetch(isLogin ? '/auth/login' : '/auth/register', { method: 'POST', body, skipAuth: true });
        saveTokens(token);
        applyUser(token.user);
        isLoggedIn.value = true;
        resetScheduleForm();
        await loadAllData();
      } catch (e) {
        showError(e, '登录失败，请检查账号密码');
      } finally {
        authLoading.value = false;
      }
    }

    function applyUser(u) {
      Object.assign(userInfo, {
        id: u.id, username: u.username, nickname: u.nickname || u.username,
        email: u.email || '', phone: u.phone || '', avatarUrl: u.avatarUrl || '',
        points: u.points || 0, createdAt: u.createdAt || ''
      });
    }

    async function logout() {
      const ok = await notifyConfirm({ title: '退出登录', message: '确定退出登录吗？' });
      if (!ok) return;
      stopTimer();
      try { await apiFetch('/auth/logout', { method: 'POST', skipAuth: false }); } catch (e) { /* 失败也继续清本地 */ }
      clearTokens();
      localStorage.removeItem('shuttle_user');
      isLoggedIn.value = false;
      schedules.value = [];
      notifications.value = [];
      aiMessages.value = [{ role: 'bot', content: '你好！我是梭灵 🤖 我可以帮你：\n\n📅 **规划日程**：告诉我你的需求，我会生成结构化的日程安排\n📊 **效率分析**：分析你的时间使用情况\n📝 **生成总结**：每日/月度/年度智能总结\n\n试试对我说："明天上午学习Java 2小时，下午运动1小时"' }];
      aiSuggestions.value = [];
      aiConversationId.value = '';
      aiWriteEnabled.value = false;
      currentPlanId = null;
      currentConversationId.value = null;
      conversations.value = [];
      activeSchedule.value = null;
    }

    /* ============================================================
     * 计时（穿梭）状态机 + 自愈秒表
     * ------------------------------------------------------------
     * 状态机与后端 sorts-schedule TimerService 合法流转一一对应（非法跃迁 409）：
     *   PENDING       --开梭(start)--> IN_PROGRESS
     *   IN_PROGRESS   --暂停(pause)--> PAUSED
     *   PAUSED        --续梭(resume)--> IN_PROGRESS
     *   IN_PROGRESS   --落梭(end)-----> COMPLETED（事务内结算秒数 + 原子发放光阴砂）
     *   PAUSED        --落梭(end)-----> COMPLETED
     *   PENDING / IN_PROGRESS / PAUSED --取消(cancel)--> CANCELLED
     * 秒数自愈：elapsedSeconds = actualDuration（历史累计）+ (now - actualStartTime)，
     * 全部以服务端时间为准；页面刷新后按同一公式自动恢复，不依赖本地计数（README §核心设计）。
     * ============================================================ */
    const TIMER_ACTIONS = {
      PENDING: ['start', 'cancel'],
      IN_PROGRESS: ['pause', 'end', 'cancel'],
      PAUSED: ['resume', 'end', 'cancel'],
      COMPLETED: [], CANCELLED: [], TIMEOUT: []
    };
    /** 当前状态是否允许该状态机动作（模板据此渲染操作按钮） */
    function canAct(status, action) {
      return (TIMER_ACTIONS[status] || []).indexOf(action) >= 0;
    }

    const timerNow = ref(Date.now()); // 每秒刷新一次基准时间戳，驱动 elapsedSeconds 重算
    let timerInterval = null;
    function startTimer() {
      stopTimer();
      timerNow.value = Date.now();
      timerInterval = setInterval(() => { timerNow.value = Date.now(); }, 1000);
    }
    function stopTimer() {
      if (timerInterval) { clearInterval(timerInterval); timerInterval = null; }
    }
    // 已流逝秒数（computed 自愈）：
    //   IN_PROGRESS = 历史累计 actualDuration + 本次片段 actualStartTime → now
    //   PAUSED/其他 = actualDuration（暂停期间不计时）
    const elapsedSeconds = computed(() => {
      const s = activeSchedule.value;
      if (!s) return 0;
      const base = s.actualDuration || 0;
      if (s.status !== 'IN_PROGRESS' || !s.actualStartTime) return base;
      return base + Math.max(0, Math.floor((timerNow.value - new Date(s.actualStartTime).getTime()) / 1000));
    });

    /** 开启规则（前端第一道）：未到计划开始时间禁用开梭按钮（后端仍会二次校验） */
    function canStartNow(s) {
      if (!s || !canAct(s.status, 'start')) return false;
      if (s.actualStartTime) return true;      // 已开过梭（PAUSED 恢复等），不再受时间约束
      if (!s.plannedStartTime) return true;
      return new Date(s.plannedStartTime).getTime() <= Date.now();
    }

    async function startSchedule(s) {
      if (activeSchedule.value && activeSchedule.value.id !== s.id) {
        notifyWarning('请先结束正在进行的日程');
        return;
      }
      // 前端时间预检：未到开始时间直接提示，避免无谓请求（后端为权威）
      if (!canStartNow(s)) {
        notifyWarning(`未到计划开始时间（${s.plannedStartTime}），暂不可开启`);
        return;
      }
      try {
        const updated = await apiFetch(`/schedules/${s.id}/start`, { method: 'POST' });
        Object.assign(s, updated);
        activeSchedule.value = updated;
        startTimer(); // elapsedSeconds 由 computed 依服务端时间自愈
      } catch (e) { showError(e); }
    }

    async function pauseSchedule(s) {
      try {
        const updated = await apiFetch(`/schedules/${s.id}/pause`, { method: 'POST' });
        stopTimer(); // 暂停期间不计时；actualDuration 已由服务端结算
        Object.assign(s, updated);
        activeSchedule.value = { ...updated };
      } catch (e) { showError(e); }
    }

    async function resumeSchedule(s) {
      try {
        const updated = await apiFetch(`/schedules/${s.id}/resume`, { method: 'POST' });
        Object.assign(s, updated);
        activeSchedule.value = { ...updated };
        startTimer(); // 服务端已重置 actualStartTime 并保留累计秒数
      } catch (e) { showError(e); }
    }

    async function endSchedule(s) {
      stopTimer();
      const before = userInfo.points;
      try {
        const updated = await apiFetch(`/schedules/${s.id}/end`, { method: 'POST' });
        Object.assign(s, updated);
        // 结算积分：以服务端为准，重新拉取用户信息计算增量
        let earned = 0;
        try {
          const me = await apiFetch('/users/me');
          applyUser(me);
          earned = userInfo.points - before;
        } catch (e) { /* 积分拉取失败不阻塞主流程 */ }
        activeSchedule.value = null; // computed 自动归零
        await loadSchedules(); // 落梭后刷新列表状态与光阴砂余额
        notifySuccess(earned > 0 ? `日程完成！获得 ${earned} 光阴砂` : '日程已完成');
      } catch (e) {
        showError(e, '结束日程失败');
      }
    }

    async function cancelSchedule(s) {
      const ok = await notifyConfirm({ title: '取消日程', message: '确定取消此日程吗？', confirmText: '取消日程' });
      if (!ok) return;
      try {
        const updated = await apiFetch(`/schedules/${s.id}/cancel`, { method: 'POST' });
        Object.assign(s, updated);
        if (activeSchedule.value && activeSchedule.value.id === s.id) {
          stopTimer();
          activeSchedule.value = null;
        }
        await loadSchedules();
      } catch (e) { showError(e); }
    }

    /* ============================================================
     * 日历
     * ============================================================ */
    function prevMonth() { if (calMonth.value === 1) { calMonth.value = 12; calYear.value--; } else calMonth.value--; }
    function nextMonth() { if (calMonth.value === 12) { calMonth.value = 1; calYear.value++; } else calMonth.value++; }
    function goToToday() { const d = new Date(); calYear.value = d.getFullYear(); calMonth.value = d.getMonth() + 1; }
    /** 打开月份选择器：预览年初始化为当前年（箭头只改预览，不触发日历跳转） */
    function openMonthPicker() { pickerYear.value = calYear.value; monthPickerOpen.value = true; }
    /** 选定月份：应用预览年 + 月份，一并生效并关闭面板 */
    function pickMonth(m) { calYear.value = pickerYear.value; calMonth.value = m; monthPickerOpen.value = false; }
    function selectCalendarDay(day) {
      selectedDayLabel.value = day.dateStr;
      selectedDayDate.value = day.dateStr;
      selectedDaySchedules.value = allSchedules.value.filter(s => s.plannedStartTime && s.plannedStartTime.startsWith(day.dateStr));
      // 织历 → 织程联动：携带 date 参数跳转（hash 路由，刷新后仍停留该日）
      navigateTo('schedules', { date: day.dateStr });
      // navigateTo 已提前置 currentPage，hashchange 分支不再触发 → 这里直接应用路由参数并同步数据
      applyRouteParams({ date: day.dateStr });
      syncSchedulesFromFilter();
    }
    /** 织历页面「在织程中查看」 */
    function openDayInSchedules(dateStr) {
      if (!dateStr) return;
      navigateTo('schedules', { date: dateStr });
      applyRouteParams({ date: dateStr });
      syncSchedulesFromFilter();
    }

    /* ============================================================
     * 织程日期范围查询（预设 + 自定义闭区间）
     * ============================================================ */
    function applyDatePreset() {
      const today = getTodayStr();
      const d = new Date();
      switch (scheduleFilter.preset) {
        case 'today':
          scheduleFilter.startDate = today; scheduleFilter.endDate = today;
          break;
        case 'tomorrow':
          const tm = new Date(d); tm.setDate(tm.getDate() + 1);
          const ts = `${tm.getFullYear()}-${String(tm.getMonth() + 1).padStart(2, '0')}-${String(tm.getDate()).padStart(2, '0')}`;
          scheduleFilter.startDate = ts; scheduleFilter.endDate = ts;
          break;
        case 'week': {
          const day = d.getDay() === 0 ? 7 : d.getDay();
          const mon = new Date(d); mon.setDate(d.getDate() - day + 1);
          const sun = new Date(mon); sun.setDate(mon.getDate() + 6);
          scheduleFilter.startDate = fmtYmd(mon);
          scheduleFilter.endDate = fmtYmd(sun);
          break;
        }
        case 'month': {
          const first = new Date(d.getFullYear(), d.getMonth(), 1);
          const last = new Date(d.getFullYear(), d.getMonth() + 1, 0);
          scheduleFilter.startDate = fmtYmd(first);
          scheduleFilter.endDate = fmtYmd(last);
          break;
        }
        case 'custom':
          // 用户手动输入起止日期
          break;
        default:
          scheduleFilter.startDate = ''; scheduleFilter.endDate = '';
      }
      syncSchedulesFromFilter();
    }
    function fmtYmd(dt) {
      return `${dt.getFullYear()}-${String(dt.getMonth() + 1).padStart(2, '0')}-${String(dt.getDate()).padStart(2, '0')}`;
    }
    function clearDateFilter() {
      scheduleFilter.preset = '';
      scheduleFilter.startDate = '';
      scheduleFilter.endDate = '';
      syncSchedulesFromFilter();
    }
    /** 织程日期输入变更：手动改起止日期即自动查询（无需再点刷新/清空） */
    function onDateChange() {
      if (scheduleFilter.startDate || scheduleFilter.endDate) scheduleFilter.preset = 'custom';
      syncSchedulesFromFilter();
    }
    /** 织程数据源随筛选刷新：有范围走后端闭区间查询，否则拉全量 */
    async function syncSchedulesFromFilter() {
      try {
        const params = { view: 'all', pageSize: 500, sort: 'plannedStartTime', order: 'asc' };
        if (scheduleFilter.startDate || scheduleFilter.endDate) {
          if (scheduleFilter.startDate) params.startDate = scheduleFilter.startDate;
          if (scheduleFilter.endDate) params.endDate = scheduleFilter.endDate;
        }
        const page = await apiFetch('/schedules', { params });
        schedules.value = (page && page.list) || [];
      } catch (e) {
        if (!(e && e.code === 40103)) showError(e, '日程加载失败');
      }
    }

    /* ============================================================
     * 织程批量删除
     * ============================================================ */
    async function deleteSelectedSchedules() {
      const count = selectedScheduleIds.value.length;
      if (!count) { notifyWarning('请先勾选要删除的日程'); return; }
      const ok = await notifyConfirm({
        title: '批量删除日程', message: `确定删除选中的 ${count} 条日程吗？删除后可在数据中追溯（软删除），不可恢复。`,
        confirmText: '删除', cancelText: '取消'
      });
      if (!ok) return;
      deletingSchedules.value = true;
      try {
        await apiFetch('/schedules/batch-delete', { method: 'POST', body: { ids: selectedScheduleIds.value } });
        selectedScheduleIds.value = [];
        notifySuccess(`已删除 ${count} 条日程`);
        await Promise.all([syncSchedulesFromFilter(), loadStats()]);
      } catch (e) { /* apiFetch 已统一弹窗 */ } finally {
        deletingSchedules.value = false;
      }
    }

    /* ============================================================
     * 日程 CRUD（真实接口）
     * ============================================================ */
    function toApiTime(v) {
      // datetime-local 给的是 YYYY-MM-DDTHH:mm，后端按 ISO 秒解析，补 :00
      return /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(v) ? v + ':00' : v;
    }

    async function saveSchedule() {
      if (!scheduleForm.title || !scheduleForm.plannedStartTime) { notifyWarning('请填写标题和计划时间'); return; }
      const tags = scheduleForm.tagsStr ? scheduleForm.tagsStr.split(',').map(t => t.trim()).filter(Boolean) : [];
      const data = {
        title: scheduleForm.title.trim(),
        description: scheduleForm.description || undefined,
        plannedStartTime: toApiTime(scheduleForm.plannedStartTime),
        plannedDuration: scheduleForm.plannedDuration,
        priority: scheduleForm.priority,
        tags,
        color: scheduleForm.color
      };
      try {
        if (editingSchedule.value) {
          await apiFetch(`/schedules/${editingSchedule.value.id}`, { method: 'PUT', body: data });
        } else {
          await apiFetch('/schedules', { method: 'POST', body: data });
        }
        showScheduleModal.value = false;
        editingSchedule.value = null;
        resetScheduleForm();
        await loadSchedules();
      } catch (e) { showError(e, '保存日程失败'); }
    }

    function editSchedule(s) {
      editingSchedule.value = s;
      scheduleForm.title = s.title;
      scheduleForm.description = s.description || '';
      scheduleForm.plannedStartTime = s.plannedStartTime || '';
      scheduleForm.plannedDuration = s.plannedDuration || 60;
      scheduleForm.priority = s.priority || 'MEDIUM';
      scheduleForm.tagsStr = (s.tags || []).join(', ');
      scheduleForm.color = s.color || '';
      showScheduleModal.value = true;
    }

    async function deleteSchedule(s) {
      const ok = await notifyConfirm({
        title: '删除日程', message: `确定删除日程"${s.title}"吗？删除后可在数据中追溯（软删除），不可恢复。`,
        confirmText: '删除'
      });
      if (!ok) return;
      try {
        await apiFetch(`/schedules/${s.id}`, { method: 'DELETE' });
        if (activeSchedule.value && activeSchedule.value.id === s.id) {
          stopTimer();
          activeSchedule.value = null;
        }
        notifySuccess('日程已删除');
        await Promise.all([loadSchedules(), loadStats()]);
      } catch (e) { showError(e, '删除日程失败'); }
    }

    function openScheduleDetail(s) {
      // 后端 ScheduleVO 不含 timeRecords（计时明细），模板中该区块 v-if 自动隐藏
      scheduleDetail.value = { ...s };
    }

    async function loadScheduleDetail(id) {
      try {
        const s = await apiFetch(`/schedules/${id}`);
        scheduleDetail.value = { ...s };
      } catch (e) { showError(e); }
    }

    function resetScheduleForm() {
      scheduleForm.title = '';
      scheduleForm.description = '';
      scheduleForm.plannedStartTime = getTodayStr() + 'T09:00';
      scheduleForm.plannedDuration = 60;
      scheduleForm.priority = 'MEDIUM';
      scheduleForm.tagsStr = '';
      scheduleForm.color = '';   // 空 = 自动（按优先级分色）；选色后为自定义颜色标记
    }

    /* ============================================================
     * AI（真实接口：规划走 /ai/plan + 采纳，对话走 /ai/chat JSON）
     * ============================================================ */
    function aiScrollToBottom() {
      nextTick(() => {
        const el = document.querySelector('.ai-chat-messages');
        if (el) el.scrollTop = el.scrollHeight;
      });
    }

    /** 统一 AI 错误文案（503 = 服务未配置模型密钥） */
    function aiErrorMessage(e) {
      if (e && e.code === 503) return 'AI 服务暂不可用（未配置模型密钥或服务未就绪），请稍后再试。';
      return (e && e.message) || 'AI 服务异常，请稍后再试。';
    }

    /** 用户主动停止生成：abort 静默结束（不抛错），保留已生成内容 */
    function stopAiReply() {
      if (aiAbortController) { aiAbortController.abort(); aiAbortController = null; }
    }

    /** 执行 AI 建议操作（done 事件 suggestedActions，如 CREATE_SCHEDULE/VIEW_STATS） */
    function runSuggestedAction(action) {
      if (!action || !action.type) return;
      if (action.type === 'CREATE_SCHEDULE') { showScheduleModal.value = true; editingSchedule.value = null; }
      else if (action.type === 'VIEW_STATS') { currentPage.value = 'stats'; }
      else if (action.type === 'GENERATE_SUMMARY') { currentPage.value = 'reports'; }
    }

    async function sendAiMessage() {
      const msg = aiInput.value.trim();
      if (!msg || aiLoading.value) return;
      aiMessages.value.push({ role: 'user', content: msg });
      aiInput.value = '';
      aiLoading.value = true;
      aiScrollToBottom();

      // 规划意图：走 /ai/plan JSON 通道（右侧结构化建议面板），保持原行为。
      // 相对时间范围（未来一周/下周/本周/明天等）由服务端注入解析，前端不再猜日期。
      const isPlanning = /规划|安排|计划|日程/.test(msg);
      if (isPlanning) {
        try {
          const plan = await apiFetch('/ai/plan', { method: 'POST', body: { userPrompt: msg } });
          currentPlanId = plan.planId || null;
          aiSuggestions.value = (plan.suggestions || []).map(sg => ({ ...sg, suggestedStart: sg.suggestedStart || '09:00' }));
          aiSelectedIndices.value = aiSuggestions.value.map((_, i) => i); // 默认全选，用户可取消
          aiPlanRange.value = plan.range || null;
          planPage.value = 1; // 新规划生成后回到第一页
          aiMessages.value.push({
            role: 'bot',
            content: aiSuggestions.value.length
              ? `好的！我按你给的时间范围生成了 ${aiSuggestions.value.length} 条规划建议 👇\n\n可以查看右侧面板按天勾选，确认后一键批量添加到织程。`
              : '我没有生成到可用的规划建议，换一种说法再试试？'
          });
          await saveConversation(true);
        } catch (e) {
          aiMessages.value.push({ role: 'bot', content: aiErrorMessage(e) });
        } finally {
          aiLoading.value = false;
          aiScrollToBottom();
        }
        return;
      }

      // 总结意图：自动生成今日总结并写入织史（后端 /ai/summary/daily 生成后即落库）
      const isSummary = /(今日|今天|当日|当天)\s*(总结|汇总)|(总结|汇总)\s*(今日|今天|当日|当天)|生成\s*今日\s*总结|今日\s*总结|总结\s*今日/.test(msg);
      if (isSummary) {
        try {
          aiMessages.value.push({ role: 'user', content: msg });
          aiMessages.value.push({ role: 'bot', content: '正在为你总结今日织程与专注情况，请稍候…' });
          aiScrollToBottom();
          const report = await apiFetch('/ai/summary/daily?stream=false', { method: 'POST', body: { date: getTodayStr() } });
          const bot = aiMessages.value[aiMessages.value.length - 1];
          bot.content = (report && report.content)
            ? (report.title ? '**' + report.title + '**\n\n' : '') + report.content
            : '今日总结已生成，请到织史查看。';
          bot.suggestedActions = [{ type: 'VIEW_REPORTS', label: '查看织史' }];
          await loadReports(); // 织史列表同步刷新，新增总结立即可见
          notifySuccess('今日总结已自动保存到织史');
          await saveConversation(true);
        } catch (e) {
          aiMessages.value.push({ role: 'bot', content: aiErrorMessage(e) });
        } finally {
          aiLoading.value = false;
          aiStreaming.value = false;
          aiScrollToBottom();
        }
        return;
      }

      // 普通对话：SSE 流式（/ai/chat 默认流式），Markdown 打字机渲染
      aiAbortController = new AbortController();
      aiStreaming.value = true;
      aiLatestText = '';
      aiMessages.value.push({ role: 'bot', content: '', suggestedActions: [] });
      const botMsg = aiMessages.value[aiMessages.value.length - 1];
      // 节流渲染：SSE delta 高频到达，90ms 合并一次，避免逐字重解析 Markdown 造成闪烁
      const scheduleRender = () => {
        if (aiRenderTimer) return;
        aiRenderTimer = setTimeout(() => {
          aiRenderTimer = null;
          if (!aiStreaming.value) return;
          botMsg.content = aiLatestText;
          aiScrollToBottom();
        }, 90);
      };
      try {
        const body = { message: msg };
        if (aiConversationId.value) body.conversationId = aiConversationId.value; // 多轮上下文（Redis 12h）
        if (aiWriteEnabled.value) body.allowWrite = true; // 双钥匙第二把（用户已确认）
        await sseChat('/ai/chat', body, {
          onDelta: (delta) => { aiLatestText += delta; scheduleRender(); },
          onDone: (payload) => {
            aiLatestText = payload && payload.reply ? payload.reply : aiLatestText;
            botMsg.content = aiLatestText;
            if (payload && payload.conversationId) aiConversationId.value = payload.conversationId;
            // 建议操作：挂在当前消息上，渲染为气泡内快捷按钮
            botMsg.suggestedActions = (payload && Array.isArray(payload.suggestedActions)) ? payload.suggestedActions : [];
            saveConversation(true); // AI 返回后落一次会话快照
          },
          onError: (e) => { botMsg.content = aiErrorMessage(e); },
          onAuthExpired: () => { sessionExpired(); }
        }, aiAbortController.signal);
        if (!botMsg.content) botMsg.content = '（没有收到回复）';
      } catch (e) {
        botMsg.content = aiErrorMessage(e);
      } finally {
        if (aiRenderTimer) { clearTimeout(aiRenderTimer); aiRenderTimer = null; }
        aiStreaming.value = false;
        aiLoading.value = false;
        aiAbortController = null;
        aiScrollToBottom();
      }
    }

    function aiQuickPrompt(prompt) {
      aiInput.value = prompt;
      sendAiMessage();
    }

    /* ============================================================
     * 多日规划：勾选 / 编辑 / 批量采纳
     * ============================================================ */
    /** 按天分组（前端展示粒度：date → items） */
    const aiGroupedSuggestions = computed(() => {
      const map = new Map();
      aiSuggestions.value.forEach((sg, i) => {
        const d = sg.date || (aiPlanRange.value && aiPlanRange.value.startDate) || '';
        if (!map.has(d)) map.set(d, []);
        map.get(d).push({ sg, index: i });
      });
      return [...map.entries()].map(([date, items]) => ({ date, items }));
    });
    // 多日规划面板分页（按天分组分页：每页 2 天，避免长列表无限延长；勾选为全局索引不受分页影响）
    const PLAN_DAY_PAGE_SIZE = 2;
    const planPage = ref(1);
    const planTotalPages = computed(() => Math.max(1, Math.ceil(aiGroupedSuggestions.value.length / PLAN_DAY_PAGE_SIZE)));
    const pagedPlanGroups = computed(() => {
      const start = (planPage.value - 1) * PLAN_DAY_PAGE_SIZE;
      return aiGroupedSuggestions.value.slice(start, start + PLAN_DAY_PAGE_SIZE);
    });
    function prevPlanPage() { if (planPage.value > 1) planPage.value--; }
    function nextPlanPage() { if (planPage.value < planTotalPages.value) planPage.value++; }
    watch(planTotalPages, (t) => { if (planPage.value > t) planPage.value = t; });
    function toggleSuggestion(index) {
      const i = aiSelectedIndices.value.indexOf(index);
      if (i >= 0) aiSelectedIndices.value.splice(i, 1);
      else aiSelectedIndices.value.push(index);
      saveConversation(true);
    }
    function toggleDaySuggestions(indices) {
      const allSelected = indices.every(i => aiSelectedIndices.value.includes(i));
      if (allSelected) aiSelectedIndices.value = aiSelectedIndices.value.filter(i => !indices.includes(i));
      else {
        indices.forEach(i => { if (!aiSelectedIndices.value.includes(i)) aiSelectedIndices.value.push(i); });
      }
      saveConversation(true);
    }
    function toggleAllSuggestions() {
      if (aiSelectedIndices.value.length === aiSuggestions.value.length) aiSelectedIndices.value = [];
      else aiSelectedIndices.value = aiSuggestions.value.map((_, i) => i);
      saveConversation(true);
    }
    function clearAiSelection() { aiSelectedIndices.value = []; saveConversation(true); }

    // 采纳时的逐项覆盖（前端编辑后回传）
    const aiEdits = {};
    function startEditSuggestion(index) {
      aiEditIndex.value = index;
      const sg = aiSuggestions.value[index];
      if (sg) { aiEditForm.title = sg.title || ''; aiEditForm.suggestedStart = sg.suggestedStart || ''; aiEditForm.duration = sg.duration || 60; }
    }
    function saveSuggestionEdit() {
      const i = aiEditIndex.value;
      if (i < 0) return;
      const sg = aiSuggestions.value[i];
      if (!sg) return;
      if (!aiEditForm.title.trim()) { notifyWarning('标题不能为空'); return; }
      sg.title = aiEditForm.title.trim();
      if (aiEditForm.suggestedStart && /^\d{2}:\d{2}$/.test(aiEditForm.suggestedStart)) sg.suggestedStart = aiEditForm.suggestedStart;
      if (aiEditForm.duration > 0) sg.duration = Math.min(480, Math.round(aiEditForm.duration));
      aiEdits[i] = { title: sg.title, suggestedStart: sg.suggestedStart, duration: sg.duration };
      aiEditIndex.value = -1;
      saveConversation(true);
    }
    function cancelSuggestionEdit() { aiEditIndex.value = -1; }

    function buildOverrides(indices) {
      const list = [];
      indices.forEach(i => { if (aiEdits[i]) list.push({ index: i, ...aiEdits[i] }); });
      return list.length ? list : undefined;
    }

    /** 批量采纳当前勾选（带编辑覆盖） */
    async function adoptSelectedSuggestions() {
      const indices = [...aiSelectedIndices.value].sort((a, b) => a - b);
      if (!indices.length) { notifyWarning('请先勾选要采纳的规划项'); return; }
      const selected = indices.map(i => aiSuggestions.value[i]).filter(Boolean);
      if (!selected.length) return;
      try {
        if (!currentPlanId) throw { message: '规划已失效，请重新让 AI 规划' };
        const overrides = buildOverrides(indices);
        const created = await apiFetch(`/ai/plan/${encodeURIComponent(currentPlanId)}/adopt`, {
          method: 'POST', body: { planId: currentPlanId, selectedIndices: indices, overrides }
        });
        const removeSet = new Set(indices);
        aiSuggestions.value = aiSuggestions.value.filter((_, i) => !removeSet.has(i));
        aiSelectedIndices.value = aiSelectedIndices.value.filter(i => !removeSet.has(i));
        aiEditIndex.value = -1;
        aiMessages.value.push({ role: 'bot', content: `✅ 已采纳并创建 ${selected.length} 条日程，织程已更新。` });
        await saveConversation(true);
        if (Array.isArray(created) && created.length) await loadSchedules();
      } catch (e) { showError(e, '采纳失败'); }
    }

    async function adoptSuggestion(index) {
      aiSelectedIndices.value = [index];
      await adoptSelectedSuggestions();
    }

    async function adoptAllSuggestions() {
      const count = aiSuggestions.value.length;
      if (!count) return;
      aiSelectedIndices.value = aiSuggestions.value.map((_, i) => i);
      await adoptSelectedSuggestions();
      if (aiSuggestions.value.length === 0) aiMessages.value.push({ role: 'bot', content: `✅ 已全部采纳！成功创建了 ${count} 个日程。` });
    }

    /* ============================================================
     * AI 会话持久化（后端保存 + 前端自动快照）
     * ============================================================ */
    async function loadConversations() {
      try {
        const page = await apiFetch('/ai/conversations', { params: { page: 1, pageSize: 50 } });
        conversations.value = (page && page.list) || [];
      } catch (e) { /* 会话列表失败保留空态 */ }
    }
    async function newConversation() {
      try {
        await flushConversationSave();
        const conv = await apiFetch('/ai/conversations', { method: 'POST', body: { title: '新会话' } });
        conversations.value.unshift(conv);
        resetAiChatState();
        currentConversationId.value = conv.id;
        await saveConversation(true);
        return conv;
      } catch (e) { /* 已统一弹窗 */ }
    }
    async function switchConversation(id) {
      if (currentConversationId.value && currentConversationId.value !== id) await flushConversationSave();
      try {
        const conv = await apiFetch(`/ai/conversations/${id}`);
        currentConversationId.value = id;
        applyConversation(conv);
      } catch (e) { /* 已统一弹窗 */ }
    }
    function resetAiChatState() {
      aiMessages.value = [welcomeAiMessage()];
      aiSuggestions.value = [];
      aiSelectedIndices.value = [];
      aiPlanRange.value = null;
      planPage.value = 1;
      currentPlanId = null;
      aiInput.value = '';
      aiEditIndex.value = -1;
      aiConversationId.value = '';
    }
    function welcomeAiMessage() {
      return { role: 'bot', content: '你好！我是梭灵 🤖 我可以帮你：\n\n📅 **规划日程**：告诉我你的需求，我会生成结构化的日程安排\n📊 **效率分析**：分析你的时间使用情况\n📝 **生成总结**：每日/月度/年度智能总结\n\n试试对我说："帮我生成未来一周规划"' };
    }
    function applyConversation(conv) {
      const msgs = (conv.messages || []).map(m => ({
        role: m.role === 'user' ? 'user' : 'bot',
        content: m.content || '',
        suggestedActions: (m.structuredData && m.structuredData.suggestedActions) || []
      }));
      aiMessages.value = msgs.length ? msgs : [welcomeAiMessage()];
      aiInput.value = conv.draft || '';
      aiSelectedIndices.value = Array.isArray(conv.selectedPlanItems)
        ? conv.selectedPlanItems.map(Number).filter(n => Number.isInteger(n) && n >= 0) : [];
      if (conv.planSuggestions && Array.isArray(conv.planSuggestions)) {
        aiSuggestions.value = conv.planSuggestions.map(s => ({ ...s, suggestedStart: s.suggestedStart || '09:00' }));
      } else {
        aiSuggestions.value = [];
      }
      aiPlanRange.value = conv.lastGeneratedRange || null;
      planPage.value = 1; // 恢复会话后从第一页展示
      currentPlanId = conv.planId || null;
      aiEditIndex.value = -1;
      aiConversationId.value = '';
    }
    function currentConvTitle() {
      const firstUser = aiMessages.value.find(m => m.role === 'user');
      const base = (firstUser && firstUser.content ? firstUser.content : '新会话');
      return base.length > 20 ? base.slice(0, 20) : base;
    }
    function currentSnapshot() {
      return {
        title: currentConvTitle(),
        messages: aiMessages.value.map(m => ({
          id: 'm' + Date.now().toString(36) + Math.random().toString(36).slice(2, 7),
          role: m.role === 'user' ? 'user' : 'assistant',
          content: m.content || '',
          timestamp: new Date().toISOString(),
          structuredData: (m.suggestedActions && m.suggestedActions.length) ? { suggestedActions: m.suggestedActions } : null
        })),
        draft: aiInput.value,
        selectedPlanItems: [...aiSelectedIndices.value],
        lastGeneratedRange: aiPlanRange.value || undefined,
        planId: currentPlanId || undefined,
        planSuggestions: aiSuggestions.value.length ? aiSuggestions.value : undefined
      };
    }
    /** 会话快照保存：immediate=true 立即落库，否则防抖（草稿场景） */
    function saveConversation(immediate) {
      const id = currentConversationId.value;
      if (!id || !isLoggedIn.value) return;
      if (!immediate) {
        if (aiDraftTimer) clearTimeout(aiDraftTimer);
        aiDraftTimer = setTimeout(() => doSaveConversation(), 800);
        return;
      }
      if (aiDraftTimer) { clearTimeout(aiDraftTimer); aiDraftTimer = null; }
      doSaveConversation();
    }
    async function flushConversationSave() {
      if (aiDraftTimer) { clearTimeout(aiDraftTimer); aiDraftTimer = null; }
      await doSaveConversation();
    }
    async function doSaveConversation() {
      const id = currentConversationId.value;
      if (!id || !isLoggedIn.value) return;
      aiSaving.value = true;
      try {
        const body = currentSnapshot();
        const updated = await apiFetch(`/ai/conversations/${id}`, { method: 'PUT', body, silent: true });
        const c = conversations.value.find(x => x.id === id);
        if (c) { c.title = updated.title; c.updatedAt = updated.updatedAt; }
      } catch (e) { /* 会话保存失败不打断主流程（silent） */ } finally {
        aiSaving.value = false;
      }
    }
    async function renameConversation(c) {
      const text = (prompt('请输入新的会话标题', c.title) || '').trim();
      if (!text || text === c.title) return;
      try {
        await apiFetch(`/ai/conversations/${c.id}`, { method: 'PUT', body: { title: text }, silent: true });
        c.title = text;
        toast('会话已重命名', 'success');
      } catch (e) { /* 已统一弹窗 */ }
    }
    async function deleteConversation(c) {
      const ok = await notifyConfirm({ title: '删除会话', message: `确定删除会话"${c.title}"吗？`, confirmText: '删除' });
      if (!ok) return;
      try {
        await apiFetch(`/ai/conversations/${c.id}`, { method: 'DELETE' });
        conversations.value = conversations.value.filter(x => x.id !== c.id);
        if (currentConversationId.value === c.id) resetAiChatState();
        notifySuccess('会话已删除');
      } catch (e) { /* 已统一弹窗 */ }
    }
    function toggleConvSelect(id) {
      const i = convSelectedIds.value.indexOf(id);
      if (i >= 0) convSelectedIds.value.splice(i, 1);
      else convSelectedIds.value.push(id);
    }
    const allConversationsChecked = computed(() => {
      return conversations.value.length > 0 && conversations.value.every(c => convSelectedIds.value.includes(c.id));
    });
    function toggleAllConversations() {
      if (allConversationsChecked.value) convSelectedIds.value = [];
      else convSelectedIds.value = conversations.value.map(c => c.id);
    }
    async function deleteSelectedConversations() {
      const count = convSelectedIds.value.length;
      if (!count) { notifyWarning('请先勾选要删除的会话'); return; }
      const ok = await notifyConfirm({ title: '批量删除会话', message: `确定删除选中的 ${count} 个会话吗？`, confirmText: '删除' });
      if (!ok) return;
      try {
        await apiFetch('/ai/conversations/batch-delete', { method: 'POST', body: { ids: convSelectedIds.value } });
        const removed = new Set(convSelectedIds.value);
        conversations.value = conversations.value.filter(x => !removed.has(x.id));
        if (currentConversationId.value && removed.has(currentConversationId.value)) resetAiChatState();
        convSelectedIds.value = [];
        notifySuccess(`已删除 ${count} 个会话`);
      } catch (e) { /* 已统一弹窗 */ }
    }
    async function clearCurrentConversation() {
      const ok = await notifyConfirm({ title: '清空当前会话', message: '确定清空当前会话的消息记录与规划建议吗？（会话本身保留）', confirmText: '清空' });
      if (!ok) return;
      resetAiChatState();
      await saveConversation(true);
      notifySuccess('当前会话已清空');
    }

    /* ============================================================
     * 报告（真实接口：日结同步返回，月/年异步轮询）
     * ============================================================ */
    async function generateReport(type) {
      generatingReport.value = true;
      try {
        let report;
        if (type === 'DAILY') {
          report = await apiFetch('/ai/summary/daily?stream=false', { method: 'POST', body: { date: getTodayStr() } });
        } else {
          const path = type === 'MONTHLY' ? '/ai/summary/monthly' : '/ai/summary/yearly';
          const asyncResp = await apiFetch(path, { method: 'POST', body: {} });
          // 轮询直到完成/失败
          report = await pollReport(asyncResp.reportId);
        }
        if (report) {
          aiReports.value = [report, ...aiReports.value.filter(r => r.id !== report.id)];
        }
      } catch (e) {
        showError(e, '报告生成失败');
      } finally {
        generatingReport.value = false;
      }
    }

    async function pollReport(reportId, tries = 0) {
      const r = await apiFetch(`/ai/reports/${reportId}`);
      if (r.status === 'COMPLETED') return r;
      if (r.status === 'FAILED') throw { code: r.status, message: '报告生成失败，请稍后重试' };
      if (tries >= 20) throw { code: -1, message: '报告生成超时，请稍后查看列表' };
      await new Promise(res => setTimeout(res, 1500));
      return pollReport(reportId, tries + 1);
    }

    function viewReportDetail(r) { viewingReport.value = r; }

    /* ============================================================
     * 商城（真实接口 + 本地装饰 owned/isActive/color）
     * ============================================================ */
    const MALL_COLOR = { SKIN: 'blue', AVATAR: 'orange', BADGE: 'purple', STICKER: 'green' };

    function purchaseItem(item) { purchaseConfirm.value = item; }

    async function confirmPurchase() {
      const item = purchaseConfirm.value;
      if (!item) return;
      try {
        const result = await apiFetch('/mall/purchase', { method: 'POST', body: { itemId: item.id } });
        userInfo.points = result.remainingPoints;
        purchaseConfirm.value = null;
        await loadMallData();
        notifySuccess(`成功购买 "${item.name}"！`);
      } catch (e) {
        purchaseConfirm.value = null;
        showError(e, '购买失败（可能光阴砂不足或已拥有）');
      }
    }

    async function useItem(item) {
      try {
        await apiFetch('/users/wardrobe/active', { method: 'PUT', body: { itemId: item.id, type: item.type } });
        await loadMallData();
        // 皮肤即时生效：服务端已置为使用中，从最新云裳阁同步整站皮肤
        applySkinFromWardrobe(wardrobe.value);
      } catch (e) { showError(e, '切换装扮失败'); }
    }

    /* ============================================================
     * 通知（真实接口）
     * ============================================================ */
    async function readNotif(n) {
      try {
        await apiFetch(`/notifications/${n.id}/read`, { method: 'PUT' });
        n.isRead = true;
      } catch (e) { showError(e); }
    }

    async function markAllRead() {
      try {
        await apiFetch('/notifications/read-all', { method: 'PUT' });
        notifications.value.forEach(n => n.isRead = true);
      } catch (e) { showError(e); }
    }

    /* ============================================================
     * 个人设置（真实接口）
     * ============================================================ */
    async function saveProfile() {
      try {
        const me = await apiFetch('/users/me', {
          method: 'PUT',
          body: { nickname: userInfo.nickname, email: userInfo.email || undefined }
        });
        applyUser(me);
        profileSaved.value = true;
        setTimeout(() => { profileSaved.value = false; }, 2000);
      } catch (e) { showError(e, '保存失败'); }
    }

    /** 头像上传：FormData 直传（apiFetch 是 JSON 通道，multipart 单独处理） */
    async function uploadAvatar(file) {
      if (!file) return;
      if (!/image\/(png|jpeg|gif|webp)/.test(file.type)) { notifyWarning('仅支持 PNG / JPG / GIF / WebP 格式的头像'); return; }
      if (file.size > 2 * 1024 * 1024) { notifyWarning('头像图片不能超过 2MB'); return; }
      try {
        const fd = new FormData();
        fd.append('file', file);
        const access = getTokens().access;
        const resp = await fetch(API_BASE + '/users/avatar/upload', {
          method: 'POST',
          headers: { 'Authorization': access && access.startsWith('Bearer ') ? access : 'Bearer ' + access },
          body: fd
        });
        const parsed = await resp.json().catch(() => null);
        if (!parsed || parsed.code !== 0) {
          const err = parsed || {};
          throw { code: err.code || -1, message: err.message || '头像上传失败，请稍后再试' };
        }
        applyUser(parsed.data);
        notifySuccess('头像已更新');
        try { localStorage.setItem('shuttle_user', JSON.stringify({ ...userInfo })); } catch (e) { /* 缓存失败不阻塞 */ }
      } catch (e) { showError(e, '头像上传失败'); }
    }
    /** 文件选择变化：取第一张直接上传（input 复用，可连续换图） */
    function onAvatarFileChange(e) {
      const f = e.target && e.target.files && e.target.files[0];
      if (f) uploadAvatar(f);
      if (e.target) e.target.value = '';
    }

    async function saveReminderSettings() {
      try {
        await apiFetch('/notifications/settings', {
          method: 'PUT',
          body: { defaultAdvanceMinutes: reminderSettings.defaultAdvanceMinutes, quietHoursEnabled: reminderSettings.quietHoursEnabled }
        });
        notifySuccess('提醒设置已保存');
      } catch (e) { showError(e, '保存失败'); }
    }

    /* ============================================================
     * 数据加载
     * ============================================================ */
    async function loadSchedules() {
      await Promise.all([syncSchedulesFromFilter(), loadAllSchedules()]);
    }
    /** 织历专用：拉取全量日程（不受织程日期筛选影响） */
    async function loadAllSchedules() {
      try {
        const page = await apiFetch('/schedules', { params: { view: 'all', pageSize: 500, sort: 'plannedStartTime', order: 'asc' } });
        allSchedules.value = (page && page.list) || [];
      } catch (e) { if (!(e && e.code === 40103)) showError(e, '日程加载失败'); }
    }

    async function loadNotifications() {
      try {
        const page = await apiFetch('/notifications', { params: { page: 1, pageSize: 50 } });
        notifications.value = (page && page.list) || [];
      } catch (e) { /* 通知加载失败不打断主流程 */ }
    }

    async function loadMallData() {
      try {
        const [mallPage, wardrobeList, me] = await Promise.all([
          apiFetch('/mall/items', { params: { page: 1, pageSize: 50 } }),
          apiFetch('/users/wardrobe'),
          apiFetch('/users/me')
        ]);
        applyUser(me);
        const ownedMap = {}; const activeMap = {};
        (wardrobeList || []).forEach(w => {
          ownedMap[w.item.id] = true;
          if (w.isActive) activeMap[w.item.id] = true;
        });
        mallItems.value = ((mallPage && mallPage.list) || []).map(i => {
          const isSkin = i.type === 'SKIN';
          const skinKey = isSkin ? (SKIN_THEME_MAP[i.id] || '') : '';
          return {
            ...i,
            color: MALL_COLOR[i.type] || 'blue',
            skinKey,
            // 预览底：皮肤商品按主题键取色（所见即所得），其余按类型取色
            preview: isSkin ? ('skin-' + (skinKey || 'blue')) : ('skin-' + (MALL_COLOR[i.type] || 'blue')),
            owned: !!ownedMap[i.id],
            isActive: !!activeMap[i.id]
          };
        });
        wardrobe.value = wardrobeList || [];
        // 皮肤真源同步：云裳阁「使用中」的 SKIN 商品驱动整站换肤
        applySkinFromWardrobe(wardrobe.value);
      } catch (e) { /* 商城加载失败保留空态 */ }
    }

    async function loadReports() {
      try {
        const page = await apiFetch('/ai/reports', { params: { page: 1, pageSize: 50 } });
        aiReports.value = (page && page.list) || [];
      } catch (e) { /* AI 报告列表失败保留空态 */ }
    }

    async function deleteReport(r) {
      const ok = await notifyConfirm({ title: '删除织史', message: `确定删除「${r.title || '该条织史'}」吗？删除后不可恢复。`, confirmText: '删除' });
      if (!ok) return;
      try {
        await apiFetch('/ai/reports/' + r.id, { method: 'DELETE' });
        notifySuccess('已删除织史');
        await loadReports();
      } catch (e) { /* 已由 apiFetch 统一弹窗 */ }
    }

    async function deleteSelectedReports() {
      const ids = selectedReportIds.value;
      if (!ids.length) { notifyWarning('未选择任何织史'); return; }
      const ok = await notifyConfirm({ title: '批量删除织史', message: `确定删除选中的 ${ids.length} 条织史吗？删除后不可恢复。`, confirmText: '删除' });
      if (!ok) return;
      deletingReports.value = true;
      try {
        await apiFetch('/ai/reports/batch-delete', { method: 'POST', body: { ids } });
        notifySuccess(`已删除 ${ids.length} 条织史`);
        selectedReportIds.value = [];
        await loadReports();
      } catch (e) { /* 已由 apiFetch 统一弹窗 */ }
      finally { deletingReports.value = false; }
    }

    async function loadStats() {
      try {
        const [summary, trend, tags] = await Promise.all([
          apiFetch('/statistics/summary', { params: { period: 'week' } }),
          apiFetch('/statistics/trend', { params: { days: 7 } }),
          apiFetch('/statistics/tags', { params: { period: 'week' } })
        ]);
        const rate = summary.completionRate > 1 ? summary.completionRate / 100 : summary.completionRate;
        stats.totalSchedules = summary.totalSchedules || 0;
        stats.completionRate = rate || 0;
        stats.totalFocusTime = summary.totalFocusTime || 0;
        _trendPoints.value = (trend || []).map(p => ({ ...p, completionRate: p.completionRate > 1 ? p.completionRate / 100 : p.completionRate }));
        tagStats.value = tags || [];
        // 连续打卡天数：后端未提供，用真实日程数据推算最长连续有日程天数
        stats.streakDays = computeStreak();
      } catch (e) { /* 统计失败保留零值 */ }
    }

    function computeStreak() {
      const dates = new Set();
      schedules.value.forEach(s => { if (s.plannedStartTime) dates.add(s.plannedStartTime.slice(0, 10)); });
      const sorted = [...dates].sort();
      if (!sorted.length) return 0;
      let best = 1, cur = 1;
      for (let i = 1; i < sorted.length; i++) {
        const prev = new Date(sorted[i - 1] + 'T00:00:00');
        const curDate = new Date(sorted[i] + 'T00:00:00');
        const diff = (curDate - prev) / 86400000;
        if (diff === 1) { cur++; best = Math.max(best, cur); }
        else cur = 1;
      }
      return best;
    }

    async function loadReminderSettings() {
      try {
        const s = await apiFetch('/notifications/settings');
        reminderSettings.defaultAdvanceMinutes = s.defaultAdvanceMinutes != null ? s.defaultAdvanceMinutes : 15;
        reminderSettings.quietHoursEnabled = !!s.quietHoursEnabled;
      } catch (e) { /* 保留默认值 */ }
    }

    async function loadActiveSchedule() {
      try {
        const s = await apiFetch('/schedules/active');
        if (s) {
          activeSchedule.value = s;
          if (s.status === 'IN_PROGRESS') startTimer(); // 秒数由 computed 依服务端时间自愈
        }
      } catch (e) { /* 无进行中日程时服务端返回 data=null */ }
    }

    async function loadAllData() {
      await Promise.all([
        loadSchedules(),
        loadNotifications(),
        loadMallData(),
        loadReports(),
        loadStats(),
        loadReminderSettings(),
        loadActiveSchedule()
      ]);
    }

    /* ============================================================
     * 页面状态持久化与恢复（刷新保持）
     * ============================================================ */
    function restorePageContext() {
      // 路由参数（织历 → 织程跳转等）
      const { params } = parseHash();
      applyRouteParams(params);
      // 织程：日期筛选 + 选中项
      const s = loadPageState('schedules');
      if (s) {
        scheduleFilter.preset = s.preset || '';
        scheduleFilter.startDate = s.startDate || '';
        scheduleFilter.endDate = s.endDate || '';
        selectedScheduleIds.value = Array.isArray(s.selectedIds) ? s.selectedIds : [];
      }
      // 织历：月份 + 选中日期
      const c = loadPageState('calendar');
      if (c) {
        if (c.year) calYear.value = c.year;
        if (c.month) calMonth.value = c.month;
        if (c.selectedDate) {
          selectedDayDate.value = c.selectedDate;
          selectedDayLabel.value = c.selectedDate;
          selectedDaySchedules.value = schedules.value.filter(x => x.plannedStartTime && x.plannedStartTime.startsWith(c.selectedDate));
        }
      }
      // 织史：选中项
      const r = loadPageState('reports');
      if (r) selectedReportIds.value = Array.isArray(r.selectedIds) ? r.selectedIds : [];
    }

    async function restoreAiSession() {
      await loadConversations();
      const saved = loadPageState('ai');
      if (saved && saved.currentId && conversations.value.some(x => x.id === saved.currentId)) {
        await switchConversation(saved.currentId);
      } else if (conversations.value.length) {
        await switchConversation(conversations.value[0].id);
      } else {
        await newConversation();
      }
    }

    // 导航与 hash 双向同步：nav 点击改 currentPage → hash；hashchange（前进后退/手改）→ currentPage
    window.addEventListener('hashchange', () => {
      const { page, params } = parseHash();
      if (page && page !== currentPage.value) {
        currentPage.value = page;
        applyRouteParams(params);
      }
    });
    watch(currentPage, () => syncHashFromPage());

    // 页面关键状态 → sessionStorage（会话级：关闭标签页即失效，不残留跨账号数据）
    watch(() => [scheduleFilter.preset, scheduleFilter.startDate, scheduleFilter.endDate, selectedScheduleIds.value], () => {
      savePageState('schedules', { preset: scheduleFilter.preset, startDate: scheduleFilter.startDate, endDate: scheduleFilter.endDate, selectedIds: selectedScheduleIds.value });
    }, { deep: true });
    watch([calYear, calMonth, selectedDayDate], () => {
      savePageState('calendar', { year: calYear.value, month: calMonth.value, selectedDate: selectedDayDate.value });
    });
    watch(selectedReportIds, () => {
      savePageState('reports', { selectedIds: selectedReportIds.value });
    }, { deep: true });
    watch(currentConversationId, (id) => {
      savePageState('ai', { currentId: id });
    });
    // AI 草稿防抖保存（800ms）
    watch(aiInput, () => {
      if (currentConversationId.value && isLoggedIn.value) saveConversation(false);
    });
    // 勾选变化即保存（恢复时用抑制开关防回写）
    watch([aiSelectedIndices, aiSuggestions], () => {
      if (currentConversationId.value && isLoggedIn.value && !restoringAi) saveConversation(true);
    }, { deep: true });
    let restoringAi = false;

    /* ============================================================
     * 生命周期
     * ============================================================ */
    onMounted(async () => {
      resetScheduleForm();
      // 刷新保持：优先恢复 hash 中的页面（在登录校验之前），登录后恢复路由参数与关键状态
      const initialRoute = parseHash();
      if (initialRoute.page) currentPage.value = initialRoute.page;
      // 皮肤：优先本地缓存即时生效（避免闪白），登录后由云裳阁服务端真源校准
      const cachedSkin = localStorage.getItem(SKIN_STORAGE_KEY);
      if (cachedSkin && SKIN_KEYS.indexOf(cachedSkin) >= 0) applySkin(cachedSkin);
      // 清理旧版演示计时残留
      localStorage.removeItem('shuttle_timer_schedule');
      localStorage.removeItem('shuttle_timer_start');
      localStorage.removeItem('shuttle_timer_elapsed');

      // 已登录（有令牌）→ 校验并恢复会话
      const { access } = getTokens();
      if (access) {
        try {
          const me = await apiFetch('/users/me');
          applyUser(me);
          isLoggedIn.value = true;
          await loadAllData();
          restorePageContext();
          // AI 会话恢复：后端会话列表 + 上次当前会话（失败回退新建，不阻塞主流程）
          restoringAi = true;
          try { await restoreAiSession(); } catch (e) { /* 已统一弹窗 */ }
          restoringAi = false;
          return;
        } catch (e) {
          if (!(e && (e.code === 40101 || e.code === 40102 || e.code === 40103))) {
            // 非令牌类错误：降级为本地缓存用户
            const savedUser = localStorage.getItem('shuttle_user');
            if (savedUser) {
              try {
                Object.assign(userInfo, JSON.parse(savedUser));
                isLoggedIn.value = true;
                await loadAllData();
                restorePageContext();
                return;
              } catch (err) { /* 继续走登录页 */ }
            }
          }
        }
      }
    });

    onUnmounted(() => { stopTimer(); });

    // 登录状态持久化（用户信息冗余存一份，便于快速恢复）
    watch(isLoggedIn, (v) => {
      if (v) {
        localStorage.setItem('shuttle_user', JSON.stringify({ ...userInfo }));
      } else {
        localStorage.removeItem('shuttle_user');
      }
    });

    return {
      // Auth
      authMode, authForm, authLoading, isLoggedIn, userInfo,
      handleAuth, logout,
      // Nav
      currentPage, navigateTo,
      // Timer（状态机动作合法性 + 自愈秒表）
      activeSchedule, elapsedSeconds, canAct, canStartNow,
      startSchedule, pauseSchedule, resumeSchedule, endSchedule, cancelSchedule,
      formatTimer, bubbleStyle,
      // Data
      schedules, notifications, mallItems, wardrobe, aiReports,
      // Calendar
      calYear, calMonth, monthPickerOpen, pickerYear, calendarDays, dayHeaders,
      prevMonth, nextMonth, goToToday, openMonthPicker, pickMonth, selectCalendarDay, openDayInSchedules,
      selectedDaySchedules, selectedDayLabel, selectedDayDate,
      // AI（流式 + Markdown + 双钥匙写权限）
      aiInput, aiMessages, aiLoading, aiSuggestions,
      aiStreaming, aiConversationId, aiWriteEnabled,
      sendAiMessage, aiQuickPrompt, stopAiReply, runSuggestedAction,
      // AI 多日规划（勾选 / 编辑 / 批量采纳 / 分页）
      aiGroupedSuggestions, aiPlanRange, aiSelectedIndices, aiEditIndex, aiEditForm,
      pagedPlanGroups, planPage, planTotalPages, prevPlanPage, nextPlanPage,
      toggleSuggestion, toggleDaySuggestions, toggleAllSuggestions, clearAiSelection,
      adoptSuggestion, adoptAllSuggestions, adoptSelectedSuggestions,
      startEditSuggestion, saveSuggestionEdit, cancelSuggestionEdit,
      // AI 会话持久化
      conversations, currentConversationId, aiSaving, convSelectedIds,
      allConversationsChecked, toggleConvSelect, toggleAllConversations,
      newConversation, switchConversation, renameConversation, deleteConversation,
      deleteSelectedConversations, clearCurrentConversation,
      // AI 三栏面板：隐藏 / 拖拽调宽
      aiPanels, aiWidths, toggleAiPanel, showAllAiPanels, startAiResize,
      // 头像上传
      uploadAvatar, onAvatarFileChange,
      // Stats
      stats, todayStats, todaySchedules, upcomingSchedules,
      filteredSchedules, groupedSchedules, filteredMallItems,
      trendData, tagStats,
      // Filters（织程日期范围）
      scheduleFilter, mallTab, applyDatePreset, clearDateFilter, onDateChange,
      // 织程多选 / 批量删除
      selectedScheduleIds, allSchedulesChecked, someSchedulesChecked,
      toggleAllSchedules, toggleScheduleSelect, deleteSelectedSchedules, deletingSchedules,
      // 织史多选 / 删除
      selectedReportIds, allReportsChecked, toggleAllReports, toggleReportSelect,
      deleteReport, deleteSelectedReports, deletingReports,
      // Modals
      showScheduleModal, editingSchedule, scheduleDetail,
      purchaseConfirm, viewingReport, generatingReport,
      // 统一提示
      notify, notifyOk, notifyCancel, copyNotifyDetail, toasts, dismissToast,
      // Forms
      scheduleForm, reminderSettings, profileSaved,
      // Mall
      purchaseItem, confirmPurchase, useItem,
      // Notifications
      unreadNotifCount, readNotif, markAllRead,
      // Schedule CRUD
      saveSchedule, editSchedule, deleteSchedule,
      openScheduleDetail, loadScheduleDetail,
      // Reports
      generateReport, viewReportDetail,
      // Profile
      saveProfile, saveReminderSettings,
      // Utils
      formatTime, formatDate, formatDateTime, formatSeconds, formatMinutes,
      statusLabel, todayStr, tagColors, getTodayStr, renderMarkdown,
    };
  }
});

export default app;

