<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useTimerStore } from '@/stores/timer'
import { useAppStore } from '@/stores/app'
import { listSchedules } from '@/api/schedule'
import type { ScheduleVO } from '@/types'
import { ApiError } from '@/api/error'
import { allowedActions } from '@/utils/status'
import type { TimerAction } from '@/utils/status'
import { formatDuration, formatTime, formatTimer } from '@/utils/datetime'
import SSeal from '@/components/ui/SSeal.vue'
import SButton from '@/components/ui/SButton.vue'
import SSkeleton from '@/components/ui/SSkeleton.vue'
import SEmpty from '@/components/ui/SEmpty.vue'
import SIcon from '@/components/ui/SIcon.vue'

/** 穿梭计时：大表盘 + 状态机五连 + 待开梭列表 */
const timer = useTimerStore()
const app = useAppStore()

const pending = ref<ScheduleVO[]>([])
const loadingPending = ref(true)
const acting = ref(false)

const active = computed(() => timer.active)
const actions = computed(() => (active.value ? allowedActions(active.value.status) : []))

const ACTION_LABEL: Record<TimerAction, string> = {
  start: '开梭',
  pause: '暂停',
  resume: '续梭',
  end: '落梭',
  cancel: '取消'
}

async function loadPending() {
  loadingPending.value = true
  try {
    const page = await listSchedules({ status: 'PENDING', page: 1, pageSize: 5, sort: 'plannedStartTime', order: 'asc' })
    pending.value = page.list
  } catch {
    /* 静默，主表盘不受影响 */
  } finally {
    loadingPending.value = false
  }
}

async function act(action: TimerAction) {
  acting.value = true
  try {
    await timer.act(action)
    if (action === 'end') app.toast('已落梭，光阴砂入袋', 'success')
    if (action === 'end' || action === 'cancel') await loadPending()
  } catch (e) {
    app.toast(e instanceof ApiError ? e.message : '操作失败', 'error')
  } finally {
    acting.value = false
  }
}

async function startOne(id: number) {
  acting.value = true
  try {
    await timer.startFor(id)
    app.toast('开梭！', 'success')
  } catch (e) {
    app.toast(e instanceof ApiError ? e.message : '开梭失败', 'error')
  } finally {
    acting.value = false
  }
}

onMounted(async () => {
  await timer.recover()
  await loadPending()
})
</script>

<template>
  <div class="timer-page">
    <!-- 表盘 -->
    <section class="timer-page__dial" :class="{ 'is-running': timer.isRunning }">
      <template v-if="active">
        <SSeal
          v-if="timer.isRunning"
          form="solid-gold"
          text="穿梭中"
          breath
          :size="64"
        />
        <SSeal v-else form="outline-arrow" text="暂停" :size="64" />
        <h2 class="timer-page__title">{{ active.title }}</h2>
        <p class="timer-page__clock timer-display">{{ formatTimer(timer.elapsedSeconds) }}</p>
        <p class="timer-page__meta num">
          计划 {{ formatTime(active.plannedStartTime) }} 起 · {{ active.plannedDuration }} 分钟
        </p>
        <div class="timer-page__actions">
          <SButton
            v-for="a in actions"
            :key="a"
            :variant="a === 'end' ? 'gold' : a === 'cancel' ? 'ghost' : 'primary'"
            size="lg"
            :loading="acting"
            @click="act(a)"
          >
            {{ ACTION_LABEL[a] }}
          </SButton>
        </div>
      </template>

      <template v-else>
        <p class="timer-page__clock timer-display timer-page__clock--idle">00:00:00</p>
        <p class="timer-page__idle-hint">梭子正闲搁着，挑一条待织的日程开梭吧</p>
      </template>
    </section>

    <!-- 待开梭 -->
    <section v-if="!active" class="timer-page__pending">
      <h3 class="timer-page__section-title">待开梭</h3>
      <SSkeleton v-if="loadingPending" :lines="3" />
      <SEmpty v-else-if="!pending.length" text="没有待开始的日程" hint="去「日程清单」织一条新的" icon="timer" />
      <ul v-else class="timer-page__pending-list">
        <li v-for="s in pending" :key="s.id" class="timer-page__pending-item weave-in">
          <div class="timer-page__pending-info">
            <span class="timer-page__pending-title">{{ s.title }}</span>
            <span class="timer-page__pending-meta num">
              {{ formatTime(s.plannedStartTime) }} · {{ s.plannedDuration }} 分钟
              <template v-if="s.tags.length"> · #{{ s.tags.join(' #') }}</template>
            </span>
          </div>
          <SButton size="sm" :loading="acting" @click="startOne(s.id)">
            <SIcon name="play" :size="13" /> 开梭
          </SButton>
        </li>
      </ul>
    </section>

    <p v-if="active" class="timer-page__tip">
      已织 {{ formatDuration(timer.elapsedSeconds) }}；落梭后按实际时长结算光阴砂。
    </p>
  </div>
</template>

<style scoped>
.timer-page {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--sorts-space-5);
  max-width: 640px;
  margin: 0 auto;
}
.timer-page__dial {
  width: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--sorts-space-3);
  padding: var(--sorts-space-8) var(--sorts-space-4);
  background: var(--sorts-surface);
  border: 1px solid var(--sorts-border);
  border-radius: var(--sorts-radius-box);
  box-shadow: var(--sorts-shadow-1);
}
.timer-page__dial.is-running { border-color: var(--sorts-color-gold-500); }
.timer-page__title { margin: 0; font-size: var(--sorts-text-xl); color: var(--sorts-ink); text-align: center; }
.timer-page__clock {
  margin: 0;
  font-size: 64px;
  font-weight: 600;
  color: var(--sorts-color-gold-700);
  line-height: 1.1;
}
.timer-page__clock--idle { color: var(--sorts-color-line-400); }
.timer-page__meta { margin: 0; font-size: var(--sorts-text-sm); color: var(--sorts-text-faint); }
.timer-page__actions { display: flex; gap: var(--sorts-space-3); margin-top: var(--sorts-space-2); }
.timer-page__idle-hint { margin: 0; color: var(--sorts-text-faint); }
.timer-page__pending { width: 100%; }
.timer-page__section-title { margin: 0 0 var(--sorts-space-3); font-size: var(--sorts-text-base); color: var(--sorts-ink); }
.timer-page__pending-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: var(--sorts-space-2);
}
.timer-page__pending-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sorts-space-3);
  padding: 10px var(--sorts-space-4);
  background: var(--sorts-surface);
  border: 1px solid var(--sorts-border);
  border-left: 3px solid var(--sorts-status-pending-fg);
  border-radius: var(--sorts-radius-ctl);
}
.timer-page__pending-info { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
.timer-page__pending-title { color: var(--sorts-ink); font-size: var(--sorts-text-base); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.timer-page__pending-meta { color: var(--sorts-text-faint); font-size: var(--sorts-text-xs); }
.timer-page__tip { margin: 0; font-size: var(--sorts-text-sm); color: var(--sorts-text-faint); }
@media (max-width: 768px) {
  .timer-page__clock { font-size: 48px; }
}
</style>
