/**
 * 梭子 SORTS · 主版前端（legacy-demo 提升）数据层
 * ----------------------------------------------------------------
 * 设计（index.html / css/style.css）保持不动，本文件仅替换数据来源：
 *   - 所有数据均来自真实后端接口（网关 8080 / 经 nginx /api 反代）
 *   - 令牌：localStorage 复用 Vite 版键名 sorts.access / sorts.refresh
 *   - 40101/40102/HTTP 401 → 无感续期（单飞）→ 重放一次；续期失败回登录页
 * 契约真源：frontend/vite-app/src/types/index.ts 与 api-spec.json
 */
const { createApp, ref, reactive, computed, onMounted, onUnmounted, watch, nextTick } = Vue;

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
  const { method = 'GET', body, params, skipAuth = false, retried = false } = opts;
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
  let resp;
  try {
    resp = await fetch(url, { method, headers, body: body ? JSON.stringify(body) : undefined });
  } catch (e) {
    throw { code: -1, message: '网络异常，请检查连接后重试' };
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

    // ============ Navigation ============
    const currentPage = ref('dashboard');

    // ============ Timer State ============
    // 注意：elapsedSeconds / timerInterval 定义在下方「计时状态机」段（自愈秒表）
    const activeSchedule = ref(null);

    // ============ Data Stores ============
    const schedules = ref([]);
    const notifications = ref([]);
    const mallItems = ref([]);
    const wardrobe = ref([]);
    const aiReports = ref([]);

    // ============ Calendar State ============
    const calYear = ref(new Date().getFullYear());
    const calMonth = ref(new Date().getMonth() + 1);
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
    const scheduleFilter = reactive({ status: '', priority: '', keyword: '' });
    const mallTab = ref('all');

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

    const todaySchedules = computed(() => {
      const today = getTodayStr();
      return schedules.value.filter(s => s.plannedStartTime && s.plannedStartTime.startsWith(today));
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
      return schedules.value
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
      return list.sort((a, b) => (a.plannedStartTime || '').localeCompare(b.plannedStartTime || ''));
    });

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
      schedules.value.forEach(s => {
        if (s.plannedStartTime) {
          const d = s.plannedStartTime.slice(0, 10);
          if (!scheduleMap[d]) scheduleMap[d] = [];
          scheduleMap[d].push(s);
        }
      });

      const days = [];
      const prevLastDay = new Date(year, month - 1, 0).getDate();
      for (let i = startDayOfWeek - 1; i >= 0; i--) {
        const d = prevLastDay - i;
        const dm = month - 1; const dy = dm === 0 ? year - 1 : year; const m = dm === 0 ? 12 : dm;
        const ds = `${dy}-${String(m).padStart(2, '0')}-${String(d).padStart(2, '0')}`;
        const sc = scheduleMap[ds] || [];
        days.push({ key: 'p' + d, dayOfMonth: d, isToday: false, isCurrentMonth: false, dateStr: ds, totalCount: sc.length, colors: sc.map(s => s.color || '#4A6CF7') });
      }
      for (let d = 1; d <= daysInMonth; d++) {
        const ds = `${year}-${String(month).padStart(2, '0')}-${String(d).padStart(2, '0')}`;
        const sc = scheduleMap[ds] || [];
        days.push({ key: 'c' + d, dayOfMonth: d, isToday: ds === tStr, isCurrentMonth: true, dateStr: ds, totalCount: sc.length, colors: sc.map(s => s.color || '#4A6CF7') });
      }
      const remaining = 42 - days.length;
      for (let d = 1; d <= remaining; d++) {
        const dm = month + 1; const dy = dm === 13 ? year + 1 : year; const m = dm === 13 ? 1 : dm;
        const ds = `${dy}-${String(m).padStart(2, '0')}-${String(d).padStart(2, '0')}`;
        const sc = scheduleMap[ds] || [];
        days.push({ key: 'n' + d, dayOfMonth: d, isToday: false, isCurrentMonth: false, dateStr: ds, totalCount: sc.length, colors: sc.map(s => s.color || '#4A6CF7') });
      }
      return days;
    });

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
    const tagColors = ['#4A6CF7', '#10B981', '#F59E0B', '#EF4444', '#8B5CF6', '#EC4899', '#6366F1', '#14B8A6'];

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
      alert((e && e.message) ? e.message : fallback);
    }

    /** 会话彻底失效（续期也被拒）：清令牌回登录页 */
    function sessionExpired() {
      clearTokens();
      localStorage.removeItem('shuttle_user');
      stopTimer();
      activeSchedule.value = null;
      isLoggedIn.value = false;
      alert('登录状态已失效，请重新登录');
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
      if (!confirm('确定退出登录吗？')) return;
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

    async function startSchedule(s) {
      if (activeSchedule.value && activeSchedule.value.id !== s.id) {
        alert('请先结束正在进行的日程');
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
        alert(earned > 0 ? `✅ 日程完成！获得 ${earned} 光阴砂` : '✅ 日程已完成');
      } catch (e) {
        showError(e, '结束日程失败');
      }
    }

    async function cancelSchedule(s) {
      if (!confirm('确定取消此日程吗？')) return;
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
    function selectCalendarDay(day) {
      selectedDayLabel.value = day.dateStr;
      selectedDayDate.value = day.dateStr;
      selectedDaySchedules.value = schedules.value.filter(s => s.plannedStartTime && s.plannedStartTime.startsWith(day.dateStr));
    }

    /* ============================================================
     * 日程 CRUD（真实接口）
     * ============================================================ */
    function toApiTime(v) {
      // datetime-local 给的是 YYYY-MM-DDTHH:mm，后端按 ISO 秒解析，补 :00
      return /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(v) ? v + ':00' : v;
    }

    async function saveSchedule() {
      if (!scheduleForm.title || !scheduleForm.plannedStartTime) { alert('请填写标题和计划时间'); return; }
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
      scheduleForm.color = s.color || '#4A6CF7';
      showScheduleModal.value = true;
    }

    async function deleteSchedule(s) {
      if (!confirm(`确定删除日程"${s.title}"吗？`)) return;
      try {
        await apiFetch(`/schedules/${s.id}`, { method: 'DELETE' });
        if (activeSchedule.value && activeSchedule.value.id === s.id) {
          stopTimer();
          activeSchedule.value = null;
        }
        await loadSchedules();
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
      scheduleForm.color = '#4A6CF7';
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

      // 规划意图：走 /ai/plan JSON 通道（右侧结构化建议面板），保持原行为
      const isPlanning = /规划|安排|计划|日程/.test(msg);
      if (isPlanning) {
        try {
          const tomorrow = new Date(); tomorrow.setDate(tomorrow.getDate() + 1);
          const targetDate = tomorrow.toISOString().slice(0, 10);
          const plan = await apiFetch('/ai/plan', { method: 'POST', body: { userPrompt: msg, targetDate } });
          currentPlanId = plan.planId || null;
          aiSuggestions.value = (plan.suggestions || []).map(sg => ({ ...sg, suggestedStart: sg.suggestedStart || '09:00' }));
          aiMessages.value.push({
            role: 'bot',
            content: aiSuggestions.value.length
              ? `好的！我已经分析了你的需求，生成了 ${aiSuggestions.value.length} 条规划建议 👇\n\n你可以查看右侧面板，选择合适的建议一键采纳创建日程。`
              : '我没有生成到可用的规划建议，换一种说法再试试？'
          });
        } catch (e) {
          aiMessages.value.push({ role: 'bot', content: aiErrorMessage(e) });
        } finally {
          aiLoading.value = false;
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

    async function adoptSuggestion(index) {
      const sg = aiSuggestions.value[index];
      if (!sg) return;
      try {
        if (!currentPlanId) throw { message: '规划已失效，请重新让 AI 规划' };
        const created = await apiFetch(`/ai/plan/${encodeURIComponent(currentPlanId)}/adopt`, {
          method: 'POST', body: { planId: currentPlanId, selectedIndices: [index] }
        });
        aiSuggestions.value.splice(index, 1);
        aiMessages.value.push({ role: 'bot', content: `✅ 已采纳并创建日程：**${sg.title}**` });
        if (Array.isArray(created) && created.length) await loadSchedules();
      } catch (e) { showError(e, '采纳失败'); }
    }

    async function adoptAllSuggestions() {
      const count = aiSuggestions.value.length;
      if (!count) return;
      try {
        if (!currentPlanId) throw { message: '规划已失效，请重新让 AI 规划' };
        await apiFetch(`/ai/plan/${encodeURIComponent(currentPlanId)}/adopt`, {
          method: 'POST', body: { planId: currentPlanId, selectedIndices: aiSuggestions.value.map((_, i) => i) }
        });
        aiSuggestions.value = [];
        aiMessages.value.push({ role: 'bot', content: `✅ 已全部采纳！成功创建了 ${count} 个日程。` });
        await loadSchedules();
      } catch (e) { showError(e, '批量采纳失败'); }
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
        alert(`✅ 成功购买 "${item.name}"！`);
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

    async function saveReminderSettings() {
      try {
        await apiFetch('/notifications/settings', {
          method: 'PUT',
          body: { defaultAdvanceMinutes: reminderSettings.defaultAdvanceMinutes, quietHoursEnabled: reminderSettings.quietHoursEnabled }
        });
        alert('✅ 提醒设置已保存');
      } catch (e) { showError(e, '保存失败'); }
    }

    /* ============================================================
     * 数据加载
     * ============================================================ */
    async function loadSchedules() {
      try {
        const page = await apiFetch('/schedules', { params: { view: 'all', pageSize: 500, sort: 'plannedStartTime', order: 'asc' } });
        schedules.value = (page && page.list) || [];
      } catch (e) {
        if (!(e && e.code === 40103)) showError(e, '日程加载失败');
      }
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
     * 生命周期
     * ============================================================ */
    onMounted(async () => {
      resetScheduleForm();
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
      currentPage,
      // Timer（状态机动作合法性 + 自愈秒表）
      activeSchedule, elapsedSeconds, canAct,
      startSchedule, pauseSchedule, resumeSchedule, endSchedule, cancelSchedule,
      formatTimer,
      // Data
      schedules, notifications, mallItems, wardrobe, aiReports,
      // Calendar
      calYear, calMonth, calendarDays, dayHeaders,
      prevMonth, nextMonth, goToToday, selectCalendarDay,
      selectedDaySchedules, selectedDayLabel, selectedDayDate,
      // AI（流式 + Markdown + 双钥匙写权限）
      aiInput, aiMessages, aiLoading, aiSuggestions,
      aiStreaming, aiConversationId, aiWriteEnabled,
      sendAiMessage, aiQuickPrompt, stopAiReply, runSuggestedAction,
      adoptSuggestion, adoptAllSuggestions,
      // Stats
      stats, todayStats, todaySchedules, upcomingSchedules,
      filteredSchedules, filteredMallItems,
      trendData, tagStats,
      // Filters
      scheduleFilter, mallTab,
      // Modals
      showScheduleModal, editingSchedule, scheduleDetail,
      purchaseConfirm, viewingReport, generatingReport,
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

app.mount('#app');
