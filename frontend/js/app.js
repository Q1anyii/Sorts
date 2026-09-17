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
    const activeSchedule = ref(null);
    const elapsedSeconds = ref(0);
    let timerInterval = null;

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
      currentPlanId = null;
      activeSchedule.value = null;
    }

    /* ============================================================
     * 计时（真实状态机 + 本地秒表显示）
     * ============================================================ */
    function startTimer() {
      stopTimer();
      timerInterval = setInterval(() => { elapsedSeconds.value++; }, 1000);
    }

    function stopTimer() {
      if (timerInterval) { clearInterval(timerInterval); timerInterval = null; }
    }

    /** 依据服务端 actualStartTime 重算已流逝秒数 */
    function syncElapsed(s) {
      if (s && s.actualStartTime) {
        elapsedSeconds.value = Math.max(0, Math.floor((Date.now() - new Date(s.actualStartTime).getTime()) / 1000));
      }
    }

    async function startSchedule(s) {
      if (activeSchedule.value && activeSchedule.value.id !== s.id) {
        alert('请先结束正在进行的日程');
        return;
      }
      try {
        const updated = await apiFetch(`/schedules/${s.id}/start`, { method: 'POST' });
        Object.assign(s, updated);
        activeSchedule.value = updated;
        syncElapsed(updated);
        startTimer();
      } catch (e) { showError(e); }
    }

    async function pauseSchedule(s) {
      try {
        const updated = await apiFetch(`/schedules/${s.id}/pause`, { method: 'POST' });
        stopTimer();
        Object.assign(s, updated);
        activeSchedule.value = { ...updated };
      } catch (e) { showError(e); }
    }

    async function resumeSchedule(s) {
      try {
        const updated = await apiFetch(`/schedules/${s.id}/resume`, { method: 'POST' });
        Object.assign(s, updated);
        activeSchedule.value = { ...updated };
        syncElapsed(updated);
        startTimer();
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
        activeSchedule.value = null;
        elapsedSeconds.value = 0;
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
          elapsedSeconds.value = 0;
        }
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

    async function sendAiMessage() {
      const msg = aiInput.value.trim();
      if (!msg || aiLoading.value) return;
      aiMessages.value.push({ role: 'user', content: msg });
      aiInput.value = '';
      aiLoading.value = true;
      aiScrollToBottom();

      try {
        const isPlanning = /规划|安排|计划|日程/.test(msg);
        if (isPlanning) {
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
        } else {
          const chat = await apiFetch('/ai/chat?stream=false', { method: 'POST', body: { message: msg } });
          aiMessages.value.push({ role: 'bot', content: chat.reply || '（没有收到回复）' });
        }
      } catch (e) {
        if (e && e.code === 503) {
          aiMessages.value.push({ role: 'bot', content: 'AI 服务暂不可用（未配置模型密钥或服务未就绪），请稍后再试。' });
        } else {
          aiMessages.value.push({ role: 'bot', content: (e && e.message) || 'AI 服务异常，请稍后再试。' });
        }
      } finally {
        aiLoading.value = false;
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
          method: 'POST', body: { selectedIndices: [index] }
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
          method: 'POST', body: { selectedIndices: aiSuggestions.value.map((_, i) => i) }
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
        mallItems.value = ((mallPage && mallPage.list) || []).map(i => ({
          ...i,
          color: MALL_COLOR[i.type] || 'blue',
          owned: !!ownedMap[i.id],
          isActive: !!activeMap[i.id]
        }));
        wardrobe.value = wardrobeList || [];
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
          if (s.status === 'IN_PROGRESS') { syncElapsed(s); startTimer(); }
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
      // Timer
      activeSchedule, elapsedSeconds,
      startSchedule, pauseSchedule, resumeSchedule, endSchedule, cancelSchedule,
      formatTimer,
      // Data
      schedules, notifications, mallItems, wardrobe, aiReports,
      // Calendar
      calYear, calMonth, calendarDays, dayHeaders,
      prevMonth, nextMonth, goToToday, selectCalendarDay,
      selectedDaySchedules, selectedDayLabel, selectedDayDate,
      // AI
      aiInput, aiMessages, aiLoading, aiSuggestions,
      sendAiMessage, aiQuickPrompt, adoptSuggestion, adoptAllSuggestions,
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
      statusLabel, todayStr, tagColors, getTodayStr,
    };
  }
});

app.mount('#app');
