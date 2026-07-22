const { createApp, ref, reactive, computed, onMounted, onUnmounted, watch, nextTick } = Vue;

const app = createApp({
  setup() {
    // ============ Auth State ============
    const authMode = ref('login');
    const authForm = reactive({ username: '', password: '', email: '', nickname: '' });
    const authLoading = ref(false);
    const isLoggedIn = ref(false);
    const userInfo = reactive({
      id: 1, username: '', nickname: '', email: '', phone: '',
      avatarUrl: '', points: 1500, createdAt: ''
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
    const calYear = ref(2026);
    const calMonth = ref(6);
    const selectedDaySchedules = ref(null);
    const selectedDayLabel = ref('');
    const selectedDayDate = ref('');

    // ============ AI State ============
    const aiInput = ref('');
    const aiMessages = ref([
      { role: 'bot', content: '你好！我是梭子 AI 助手 🤖 我可以帮你：\n\n📅 **规划日程**：告诉我你的需求，我会生成结构化的日程安排\n📊 **效率分析**：分析你的时间使用情况\n📝 **生成总结**：每日/月度/年度智能总结\n\n试试对我说："明天上午学习Java 2小时，下午运动1小时"' }
    ]);
    const aiLoading = ref(false);
    const aiSuggestions = ref([]);

    // ============ Stats ============
    const stats = reactive({
      totalSchedules: 47, completionRate: 0.83, totalFocusTime: 126000, streakDays: 12
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

    // ============ Computed ============
    const todayStr = computed(() => new Date().toLocaleDateString('zh-CN', { year:'numeric', month:'long', day:'numeric', weekday:'long' }));

    const todaySchedules = computed(() => {
      const today = new Date().toISOString().slice(0, 10);
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
      const today = new Date().toISOString().slice(0, 10);
      return schedules.value.filter(s => s.plannedStartTime > today + 'T23:59' && s.status === 'PENDING').slice(0, 5);
    });

    const filteredSchedules = computed(() => {
      let list = schedules.value;
      if (scheduleFilter.status) list = list.filter(s => s.status === scheduleFilter.status);
      if (scheduleFilter.priority) list = list.filter(s => s.priority === scheduleFilter.priority);
      if (scheduleFilter.keyword) {
        const kw = scheduleFilter.keyword.toLowerCase();
        list = list.filter(s => s.title.toLowerCase().includes(kw) || (s.description||'').toLowerCase().includes(kw));
      }
      return list.sort((a,b) => (a.plannedStartTime||'').localeCompare(b.plannedStartTime||''));
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
      const today = new Date();
      const todayStr = today.getFullYear()+'-'+String(today.getMonth()+1).padStart(2,'0')+'-'+String(today.getDate()).padStart(2,'0');

      const scheduleMap = {};
      schedules.value.forEach(s => {
        if (s.plannedStartTime) {
          const d = s.plannedStartTime.slice(0, 10);
          if (!scheduleMap[d]) scheduleMap[d] = [];
          scheduleMap[d].push(s);
        }
      });

      const days = [];
      // Previous month
      const prevLastDay = new Date(year, month - 1, 0).getDate();
      for (let i = startDayOfWeek - 1; i >= 0; i--) {
        const d = prevLastDay - i;
        const dm = month - 1; const dy = dm === 0 ? year - 1 : year; const m = dm === 0 ? 12 : dm;
        const ds = `${dy}-${String(m).padStart(2,'0')}-${String(d).padStart(2,'0')}`;
        const sc = scheduleMap[ds] || [];
        days.push({ key:'p'+d, dayOfMonth:d, isToday:false, isCurrentMonth:false, dateStr:ds, totalCount:sc.length, colors:sc.map(s=>s.color || '#4A6CF7') });
      }
      // Current month
      for (let d = 1; d <= daysInMonth; d++) {
        const ds = `${year}-${String(month).padStart(2,'0')}-${String(d).padStart(2,'0')}`;
        const sc = scheduleMap[ds] || [];
        days.push({ key:'c'+d, dayOfMonth:d, isToday:ds===todayStr, isCurrentMonth:true, dateStr:ds, totalCount:sc.length, colors:sc.map(s=>s.color || '#4A6CF7') });
      }
      // Next month
      const remaining = 42 - days.length;
      for (let d = 1; d <= remaining; d++) {
        const dm = month + 1; const dy = dm === 13 ? year + 1 : year; const m = dm === 13 ? 1 : dm;
        const ds = `${dy}-${String(m).padStart(2,'0')}-${String(d).padStart(2,'0')}`;
        const sc = scheduleMap[ds] || [];
        days.push({ key:'n'+d, dayOfMonth:d, isToday:false, isCurrentMonth:false, dateStr:ds, totalCount:sc.length, colors:sc.map(s=>s.color || '#4A6CF7') });
      }
      return days;
    });

    const trendData = computed(() => {
      const data = [];
      for (let i = 6; i >= 0; i--) {
        const d = new Date(); d.setDate(d.getDate() - i);
        data.push({
          day: ['日','一','二','三','四','五','六'][d.getDay()],
          completionRate: 0.5 + Math.random() * 0.5
        });
      }
      return data;
    });

    const tagStats = computed(() => {
      const map = {};
      schedules.value.forEach(s => {
        (s.tags || []).forEach(t => {
          if (!map[t]) map[t] = { tag: t, count: 0, totalDuration: 0 };
          map[t].count++;
          map[t].totalDuration += (s.actualDuration || 0);
        });
      });
      const total = Object.values(map).reduce((sum, v) => sum + v.totalDuration, 0);
      return Object.values(map).map(v => ({ ...v, percentage: total > 0 ? v.totalDuration / total : 0 }));
    });

    const dayHeaders = ['日', '一', '二', '三', '四', '五', '六'];
    const tagColors = ['#4A6CF7', '#10B981', '#F59E0B', '#EF4444', '#8B5CF6', '#EC4899', '#6366F1', '#14B8A6'];

    // ============ Methods ============
    function formatTime(dt) { if (!dt) return ''; return dt.slice(11, 16); }
    function formatDate(dt) { if (!dt) return ''; const d = new Date(dt); return `${d.getMonth()+1}/${d.getDate()}`; }
    function formatDateTime(dt) { if (!dt) return ''; const d = new Date(dt); return d.toLocaleString('zh-CN'); }
    function formatSeconds(s) { const m = Math.floor(s / 60); const sec = s % 60; return `${m}分${sec}秒`; }
    function formatMinutes(m) { if (m >= 60) return `${Math.floor(m/60)}小时${m%60}分`; return `${m}分钟`; }
    function formatTimer(s) { const h = Math.floor(s/3600), m = Math.floor((s%3600)/60), sec = s % 60; return `${String(h).padStart(2,'0')}:${String(m).padStart(2,'0')}:${String(sec).padStart(2,'0')}`; }

    function statusLabel(s) {
      return { PENDING:'待开始', IN_PROGRESS:'进行中', PAUSED:'已暂停', COMPLETED:'已完成', CANCELLED:'已取消', TIMEOUT:'已超时' }[s] || s;
    }

    function getTodayStr() {
      const d = new Date();
      return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`;
    }

    // Auth
    function handleAuth() {
      authLoading.value = true;
      setTimeout(() => {
        userInfo.username = authForm.username;
        userInfo.nickname = authForm.nickname || authForm.username;
        userInfo.email = authForm.email || authForm.username + '@example.com';
        userInfo.points = 1500;
        isLoggedIn.value = true;
        authLoading.value = false;
        initMockData();
      }, 600);
    }

    function logout() {
      if (confirm('确定退出登录吗？')) {
        stopTimer();
        isLoggedIn.value = false;
        schedules.value = [];
        notifications.value = [];
        aiMessages.value = [{ role: 'bot', content: '你好！我是梭子 AI 助手 🤖...' }];
        aiSuggestions.value = [];
      }
    }

    // Timer
    function startTimer() {
      stopTimer();
      timerInterval = setInterval(() => { elapsedSeconds.value++; }, 1000);
      localStorage.setItem('shuttle_timer_schedule', JSON.stringify(activeSchedule.value));
      localStorage.setItem('shuttle_timer_start', Date.now().toString());
      localStorage.setItem('shuttle_timer_elapsed', elapsedSeconds.value.toString());
    }

    function stopTimer() {
      if (timerInterval) { clearInterval(timerInterval); timerInterval = null; }
    }

    function startSchedule(s) {
      if (activeSchedule.value && activeSchedule.value.id !== s.id) {
        alert('请先结束正在进行的日程');
        return;
      }
      s.status = 'IN_PROGRESS';
      s.actualStartTime = new Date().toISOString();
      activeSchedule.value = s;
      elapsedSeconds.value = 0;
      startTimer();
    }

    function pauseSchedule(s) {
      s.status = 'PAUSED';
      stopTimer();
      activeSchedule.value = { ...s };
    }

    function resumeSchedule(s) {
      s.status = 'IN_PROGRESS';
      startTimer();
      activeSchedule.value = { ...s };
    }

    function endSchedule(s) {
      stopTimer();
      s.status = 'COMPLETED';
      s.actualDuration = elapsedSeconds.value;
      s.actualEndTime = new Date().toISOString();
      const pointsEarned = Math.floor(elapsedSeconds.value / 60) + 10;
      userInfo.points += pointsEarned;
      activeSchedule.value = null;
      elapsedSeconds.value = 0;
      localStorage.removeItem('shuttle_timer_schedule');
      localStorage.removeItem('shuttle_timer_start');
      localStorage.removeItem('shuttle_timer_elapsed');
      alert(`✅ 日程完成！获得 ${pointsEarned} 积分`);
    }

    function cancelSchedule(s) {
      if (confirm('确定取消此日程吗？')) {
        s.status = 'CANCELLED';
        if (activeSchedule.value && activeSchedule.value.id === s.id) {
          stopTimer();
          activeSchedule.value = null;
          elapsedSeconds.value = 0;
        }
      }
    }

    // Calendar
    function prevMonth() { if (calMonth.value === 1) { calMonth.value = 12; calYear.value--; } else calMonth.value--; }
    function nextMonth() { if (calMonth.value === 12) { calMonth.value = 1; calYear.value++; } else calMonth.value++; }
    function goToToday() { const d = new Date(); calYear.value = d.getFullYear(); calMonth.value = d.getMonth() + 1; }
    function selectCalendarDay(day) {
      selectedDayLabel.value = day.dateStr;
      selectedDayDate.value = day.dateStr;
      selectedDaySchedules.value = schedules.value.filter(s => s.plannedStartTime && s.plannedStartTime.startsWith(day.dateStr));
    }

    // Schedule CRUD
    function saveSchedule() {
      if (!scheduleForm.title || !scheduleForm.plannedStartTime) { alert('请填写标题和计划时间'); return; }
      const tags = scheduleForm.tagsStr ? scheduleForm.tagsStr.split(',').map(t => t.trim()).filter(Boolean) : [];
      const data = {
        id: editingSchedule.value ? editingSchedule.value.id : Date.now(),
        userId: userInfo.id,
        title: scheduleForm.title,
        description: scheduleForm.description,
        plannedStartTime: scheduleForm.plannedStartTime,
        plannedDuration: scheduleForm.plannedDuration,
        priority: scheduleForm.priority,
        tags,
        color: scheduleForm.color,
        status: editingSchedule.value ? editingSchedule.value.status : 'PENDING',
        actualStartTime: editingSchedule.value ? editingSchedule.value.actualStartTime : null,
        actualEndTime: editingSchedule.value ? editingSchedule.value.actualEndTime : null,
        actualDuration: editingSchedule.value ? editingSchedule.value.actualDuration : null,
        createdAt: editingSchedule.value ? editingSchedule.value.createdAt : new Date().toISOString(),
        updatedAt: new Date().toISOString()
      };
      if (editingSchedule.value) {
        const idx = schedules.value.findIndex(s => s.id === editingSchedule.value.id);
        if (idx >= 0) schedules.value[idx] = data;
      } else {
        schedules.value.push(data);
      }
      showScheduleModal.value = false;
      editingSchedule.value = null;
      resetScheduleForm();
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

    function deleteSchedule(s) {
      if (confirm(`确定删除日程"${s.title}"吗？`)) {
        schedules.value = schedules.value.filter(x => x.id !== s.id);
        if (activeSchedule.value && activeSchedule.value.id === s.id) {
          stopTimer();
          activeSchedule.value = null;
        }
      }
    }

    function openScheduleDetail(s) {
      scheduleDetail.value = { ...s, timeRecords: s.timeRecords || [] };
    }

    function loadScheduleDetail(id) {
      const s = schedules.value.find(x => x.id === id);
      if (s) scheduleDetail.value = { ...s, timeRecords: s.timeRecords || [] };
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

    // AI
    function sendAiMessage() {
      if (!aiInput.value.trim() || aiLoading.value) return;
      const userMsg = aiInput.value.trim();
      aiMessages.value.push({ role: 'user', content: userMsg });
      aiInput.value = '';
      aiLoading.value = true;

      nextTick(() => {
        const el = document.querySelector('.ai-chat-messages');
        if (el) el.scrollTop = el.scrollHeight;
      });

      setTimeout(() => {
        aiLoading.value = false;

        // Simulate AI response
        if (userMsg.includes('规划') || userMsg.includes('安排') || userMsg.includes('计划')) {
          aiMessages.value.push({
            role: 'bot',
            content: '好的！我已经分析了你的需求，为你生成了以下日程规划建议 👇\n\n你可以查看右侧面板，选择合适的建议一键采纳创建日程。'
          });
          // Generate suggestions
          const now = new Date();
          const tomorrow = new Date(now); tomorrow.setDate(tomorrow.getDate() + 1);
          const dateStr = tomorrow.toISOString().slice(0, 10);

          aiSuggestions.value = [
            { title: '学习Java核心技术', suggestedStart: '09:00', duration: 120, priority: 'HIGH', tags: ['学习', 'Java'], reason: '上午精力充沛，适合技术学习' },
            { title: '午间休息', suggestedStart: '12:00', duration: 60, priority: 'MEDIUM', tags: ['生活'], reason: '保证充足休息，提升下午效率' },
            { title: '运动锻炼', suggestedStart: '14:00', duration: 60, priority: 'MEDIUM', tags: ['运动', '健康'], reason: '下午运动有助于恢复精力' },
            { title: '晚间阅读', suggestedStart: '20:00', duration: 60, priority: 'LOW', tags: ['阅读', '生活'], reason: '睡前阅读有助于放松' }
          ];
        } else if (userMsg.includes('总结')) {
          aiMessages.value.push({
            role: 'bot',
            content: '📊 **今日总结**\n\n今日完成日程 5 项，总专注时长 4.5 小时，完成率 83%。\n\n✅ **亮点**：\n- 技术学习投入充足，完成了 Spring Boot 核心章节\n- 按时完成所有计划任务\n\n💡 **建议**：\n- 可以考虑增加休息间隔，提升持续专注力\n- 下午时段效率略低，可以安排更多体力活动'
          });
        } else if (userMsg.includes('效率') || userMsg.includes('分析')) {
          aiMessages.value.push({
            role: 'bot',
            content: '📈 **本周效率分析**\n\n- 总日程数：28 项\n- 完成率：85.7%\n- 平均每日专注时长：3.8 小时\n- 最高效时段：上午 9:00-11:00\n- 标签分布：学习 45%、工作 30%、运动 15%、其他 10%\n\n📊 效率评分：⭐⭐⭐⭐ (良好)\n整体表现不错，建议保持上午高效时段，适当增加运动安排。'
          });
        } else {
          aiMessages.value.push({
            role: 'bot',
            content: '感谢你的提问！作为你的 AI 日程助手，我可以帮你：\n\n📅 **规划日程**：试试说"帮我规划明天的日程"\n📊 **效率分析**：试试说"分析我这周的效率"\n📝 **生成总结**：试试说"生成今日总结"\n💡 **管理建议**：试试说"给我一些时间管理建议"\n\n请告诉我你需要什么帮助？'
          });
        }
        nextTick(() => {
          const el = document.querySelector('.ai-chat-messages');
          if (el) el.scrollTop = el.scrollHeight;
        });
      }, 800 + Math.random() * 1200);
    }

    function aiQuickPrompt(prompt) {
      aiInput.value = prompt;
      sendAiMessage();
    }

    function adoptSuggestion(index) {
      const sg = aiSuggestions.value[index];
      if (!sg) return;
      const targetDate = new Date();
      targetDate.setDate(targetDate.getDate() + 1);
      const dateStr = targetDate.toISOString().slice(0, 10);
      const newSchedule = {
        id: Date.now() + index,
        userId: userInfo.id,
        title: sg.title,
        description: sg.reason || '',
        plannedStartTime: `${dateStr}T${sg.suggestedStart}:00`,
        plannedDuration: sg.duration,
        priority: sg.priority || 'MEDIUM',
        tags: sg.tags || [],
        color: tagColors[index % tagColors.length],
        status: 'PENDING',
        actualStartTime: null, actualEndTime: null, actualDuration: null,
        createdAt: new Date().toISOString(), updatedAt: new Date().toISOString()
      };
      schedules.value.push(newSchedule);
      aiSuggestions.value.splice(index, 1);
      aiMessages.value.push({ role: 'bot', content: `✅ 已采纳并创建日程：**${sg.title}**` });
    }

    function adoptAllSuggestions() {
      aiSuggestions.value.forEach((sg, i) => {
        const targetDate = new Date();
        targetDate.setDate(targetDate.getDate() + 1);
        const dateStr = targetDate.toISOString().slice(0, 10);
        schedules.value.push({
          id: Date.now() + i,
          userId: userInfo.id, title: sg.title, description: sg.reason || '',
          plannedStartTime: `${dateStr}T${sg.suggestedStart}:00`,
          plannedDuration: sg.duration, priority: sg.priority || 'MEDIUM',
          tags: sg.tags || [], color: tagColors[i % tagColors.length],
          status: 'PENDING',
          actualStartTime: null, actualEndTime: null, actualDuration: null,
          createdAt: new Date().toISOString(), updatedAt: new Date().toISOString()
        });
      });
      const count = aiSuggestions.value.length;
      aiSuggestions.value = [];
      aiMessages.value.push({ role: 'bot', content: `✅ 已全部采纳！成功创建了 ${count} 个日程。` });
    }

    // Reports
    function generateReport(type) {
      generatingReport.value = true;
      setTimeout(() => {
        generatingReport.value = false;
        const now = new Date().toISOString();
        const typeName = { DAILY: '每日', WEEKLY: '每周', MONTHLY: '月度', YEARLY: '年度' }[type];
        aiReports.value.unshift({
          id: Date.now(), userId: userInfo.id, type,
          title: `${new Date().toLocaleDateString('zh-CN')} ${typeName}总结`,
          content: type === 'DAILY'
            ? `📊 **${typeName}总结报告**\n\n## 概览\n今日共完成 **5** 项日程，总专注时长 **4.5** 小时。\n\n## 各时段分析\n- 🌅 上午 (8:00-12:00): 完成 2 项，效率最高\n- 🌤 下午 (13:00-18:00): 完成 2 项\n- 🌙 晚上 (19:00-22:00): 完成 1 项\n\n## 标签分布\n- 学习: 60%\n- 工作: 25%\n- 运动: 15%\n\n## AI 点评\n今天的整体效率不错！上午的学习任务完成得很好，建议明天继续保持这个节奏。下午的效率略有下降，可以考虑增加短暂休息。\n\n## 明日建议\n- 保持上午高效学习时段\n- 增加下午运动量\n- 尝试番茄工作法提升专注力`
            : type === 'MONTHLY'
            ? `📊 **${typeName}总结报告**\n\n## 月度概览\n本月共安排 **142** 项日程，完成 **118** 项，完成率 **83.1%**。\n总专注时长 **89** 小时。\n\n## 趋势分析\n本月效率呈上升趋势，最后一周完成率达到 92%。\n\n## 高频标签\n- 🥇 学习 (45%)\n- 🥈 工作 (30%)\n- 🥉 运动 (15%)\n- 其他 (10%)\n\n## 改进建议\n- 增加休息间隔，避免长时间连续工作\n- 周末安排更多放松活动\n- 可以尝试将大任务拆分为小任务`
            : `📊 **${typeName}总结报告**\n\n## 年度概览\n今年是丰收的一年！\n\n- 总日程数: **1,680**\n- 完成率: **85.2%**\n- 总专注时长: **1,020** 小时\n- 连续打卡最长: **45** 天\n\n## 成长轨迹\n- Q1: 完成率 78%，处于适应期\n- Q2: 完成率 85%，稳步上升\n- Q3: 完成率 89%，进入高效期\n- Q4: 完成率 88%，保持稳定\n\n## 年度高光\n🎯 最专注的一天: 2026-03-15 (8.5小时)\n🔥 最长连续打卡: 45天\n⭐ 获得积分: 15,800`,
          generatedAt: now,
          highlights: [],
          suggestions: [],
          status: 'COMPLETED'
        });
      }, 2000);
    }

    function viewReportDetail(r) { viewingReport.value = r; }

    // Mall
    function purchaseItem(item) { purchaseConfirm.value = item; }
    function confirmPurchase() {
      const item = purchaseConfirm.value;
      if (!item || userInfo.points < item.price) return;
      userInfo.points -= item.price;
      item.owned = true;
      wardrobe.value.push({
        id: Date.now(), userId: userInfo.id,
        item: { ...item }, isActive: false, purchasedAt: new Date().toISOString()
      });
      purchaseConfirm.value = null;
      alert(`✅ 成功购买 "${item.name}"！`);
    }

    function useItem(item) {
      wardrobe.value.forEach(w => { if (w.item.type === item.type) w.isActive = false; });
      const w = wardrobe.value.find(w => w.item.id === item.id);
      if (w) w.isActive = true;
      item.isActive = true;
    }

    // Notifications
    function readNotif(n) {
      n.isRead = true;
    }
    function markAllRead() {
      notifications.value.forEach(n => n.isRead = true);
    }

    // Profile
    function saveProfile() {
      profileSaved.value = true;
      setTimeout(() => { profileSaved.value = false; }, 2000);
    }
    function saveReminderSettings() {
      alert('✅ 提醒设置已保存');
    }

    // Init mock data
    function initMockData() {
      const today = getTodayStr();
      const tomorrow = new Date(); tomorrow.setDate(tomorrow.getDate() + 1);
      const tomorrowStr = tomorrow.toISOString().slice(0, 10);
      const dayAfter = new Date(); dayAfter.setDate(dayAfter.getDate() + 2);
      const dayAfterStr = dayAfter.toISOString().slice(0, 10);

      schedules.value = [
        { id: 1, userId: 1, title: '学习Spring Boot', description: '完成REST API开发章节', plannedStartTime: `${today}T09:00:00`, plannedDuration: 120, actualStartTime: `${today}T09:05:00`, actualEndTime: `${today}T11:10:00`, actualDuration: 7500, status: 'COMPLETED', priority: 'HIGH', tags: ['学习', 'Java'], color: '#4A6CF7', createdAt: today, updatedAt: today },
        { id: 2, userId: 1, title: '午休', description: '', plannedStartTime: `${today}T12:00:00`, plannedDuration: 45, status: 'COMPLETED', priority: 'LOW', tags: ['生活'], color: '#10B981', createdAt: today, updatedAt: today },
        { id: 3, userId: 1, title: '运动健身', description: '跑步5公里', plannedStartTime: `${today}T14:00:00`, plannedDuration: 60, status: 'PENDING', priority: 'MEDIUM', tags: ['运动', '健康'], color: '#F59E0B', createdAt: today, updatedAt: today },
        { id: 4, userId: 1, title: '阅读《代码整洁之道》', description: '第3-4章', plannedStartTime: `${today}T16:00:00`, plannedDuration: 90, status: 'PENDING', priority: 'MEDIUM', tags: ['阅读', '学习'], color: '#8B5CF6', createdAt: today, updatedAt: today },
        { id: 5, userId: 1, title: '英语单词复习', description: '复习200个单词', plannedStartTime: `${today}T20:00:00`, plannedDuration: 45, status: 'PENDING', priority: 'LOW', tags: ['学习', '英语'], color: '#EC4899', createdAt: today, updatedAt: today },
        { id: 6, userId: 1, title: '团队周会', description: '汇报本周进展', plannedStartTime: `${tomorrowStr}T10:00:00`, plannedDuration: 60, status: 'PENDING', priority: 'HIGH', tags: ['工作'], color: '#6366F1', createdAt: today, updatedAt: today },
        { id: 7, userId: 1, title: '学习微服务架构', description: 'Spring Cloud入门', plannedStartTime: `${tomorrowStr}T14:00:00`, plannedDuration: 180, status: 'PENDING', priority: 'HIGH', tags: ['学习', 'Java'], color: '#4A6CF7', createdAt: today, updatedAt: today },
        { id: 8, userId: 1, title: '产品需求评审', description: '评审Q3新功能需求', plannedStartTime: `${dayAfterStr}T09:30:00`, plannedDuration: 90, status: 'PENDING', priority: 'URGENT', tags: ['工作'], color: '#EF4444', createdAt: today, updatedAt: today },
      ];

      notifications.value = [
        { id: 1, userId: 1, type: 'REMINDER', title: '日程提醒', content: '"运动健身"将在30分钟后开始', isRead: false, createdAt: '2026-06-14 13:30' },
        { id: 2, userId: 1, type: 'SUMMARY', title: '昨日总结已生成', content: '查看你昨天的效率分析报告', isRead: false, createdAt: '2026-06-14 08:00' },
        { id: 3, userId: 1, type: 'SYSTEM', title: '连续打卡奖励', content: '已连续打卡7天，获得 50 积分奖励！', isRead: true, createdAt: '2026-06-13 20:00' },
        { id: 4, userId: 1, type: 'PROMOTION', title: '新品上架', content: '积分商城上架"星空主题"新皮肤，限时8折！', isRead: true, createdAt: '2026-06-12 10:00' },
      ];

      mallItems.value = [
        { id: 1, name: '深海蓝主题', type: 'SKIN', description: '清爽的蓝色主题皮肤', price: 200, color: 'blue', owned: false, isActive: false },
        { id: 2, name: '翡翠绿主题', type: 'SKIN', description: '护眼的绿色主题皮肤', price: 200, color: 'green', owned: false, isActive: false },
        { id: 3, name: '星空紫主题', type: 'SKIN', description: '神秘的紫色星空主题', price: 300, color: 'purple', owned: false, isActive: false },
        { id: 4, name: '活力橙主题', type: 'SKIN', description: '充满活力的橙色主题', price: 200, color: 'orange', owned: false, isActive: false },
        { id: 5, name: '暗夜黑主题', type: 'SKIN', description: '酷炫的暗黑风格主题', price: 350, color: 'dark', owned: false, isActive: false },
        { id: 6, name: '粉红甜心主题', type: 'SKIN', description: '可爱的粉色主题', price: 250, color: 'pink', owned: false, isActive: false },
        { id: 7, name: '学霸徽章', type: 'BADGE', description: '连续学习30天专属徽章', price: 500, color: 'blue', owned: false, isActive: false },
        { id: 8, name: '运动达人徽章', type: 'BADGE', description: '运动累计100小时专属徽章', price: 500, color: 'green', owned: false, isActive: false },
        { id: 9, name: '效率大师徽章', type: 'BADGE', description: '完成率95%以上专属徽章', price: 800, color: 'purple', owned: false, isActive: false },
        { id: 10, name: '闪光头像框', type: 'AVATAR', description: '酷炫的金色头像边框', price: 400, color: 'orange', owned: false, isActive: false },
      ];

      // Auto-add some items to wardrobe for demo
      wardrobe.value = [
        { id: 1, userId: 1, item: { id: 1, name: '默认主题', type: 'SKIN', price: 0 }, isActive: true, purchasedAt: today }
      ];

      // Set default schedule form date
      resetScheduleForm();

      // Restore timer state
      const savedSchedule = localStorage.getItem('shuttle_timer_schedule');
      const savedStart = localStorage.getItem('shuttle_timer_start');
      const savedElapsed = localStorage.getItem('shuttle_timer_elapsed');
      if (savedSchedule && savedStart) {
        try {
          activeSchedule.value = JSON.parse(savedSchedule);
          const startTime = parseInt(savedStart);
          const storedElapsed = parseInt(savedElapsed) || 0;
          elapsedSeconds.value = storedElapsed + Math.floor((Date.now() - startTime) / 1000);
          startTimer();
        } catch(e) {}
      }
    }

    // Lifecycle
    onMounted(() => {
      resetScheduleForm();
      // Check for saved login (demo: auto-login for convenience)
      const savedUser = localStorage.getItem('shuttle_user');
      if (savedUser) {
        try {
          const u = JSON.parse(savedUser);
          Object.assign(userInfo, u);
          isLoggedIn.value = true;
          initMockData();
        } catch(e) {}
      }
    });

    onUnmounted(() => { stopTimer(); });

    // Watch for login state to save
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

