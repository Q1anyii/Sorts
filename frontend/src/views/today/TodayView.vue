<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { getCalendarToday } from '@/api/calendar'
import type { TodayOverviewVO } from '@/types'
import { useTimerStore } from '@/stores/timer'
import { useAppStore } from '@/stores/app'
import { ApiError } from '@/api/error'
import { formatDuration, formatTimer, shichenLabel, todayStr, weekdayCn, solarTermOf } from '@/utils/datetime'
import SCard from '@/components/ui/SCard.vue'
import SSkeleton from '@/components/ui/SSkeleton.vue'
import SEmpty from '@/components/ui/SEmpty.vue'
import SSeal from '@/components/ui/SSeal.vue'
import SButton from '@/components/ui/SButton.vue'
import ScheduleItem from '@/components/schedule/ScheduleItem.vue'
import type { TimerAction } from '@/utils/status'

/** 今日经纬：概览四联卡 + 当前穿梭 + 即将开梭列表 */
const timer = useTimerStore()
const app = useAppStore()

const overview = ref<TodayOverviewVO | null>(null)
const loading = ref(true)
const errorText = ref('')
const busyId = ref(0)

const today = todayStr()
const solarTerm = solarTermOf(today)

async function load() {
  loading.value = true
  errorText.value = ''
  try {
    overview.value = await getCalendarToday()
    // 顺便校准计时 store（其他页可能已开梭）
    await timer.recover()
  } catch (e) {
    errorText.value = e instanceof ApiError ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
}

async function onAction(id: number, action: TimerAction) {
  busyId.value = id
  try {
    if (timer.active?.id === id) {
      await timer.act(action)
    } else if (action === 'start') {
      await timer.startFor(id)
    }
    app.toast(action === 'end' ? '已落梭，光阴砂入袋' : '状态已更新', 'success')
    await load()
  } catch (e) {
    app.toast(e instanceof ApiError ? e.message : '操作失败', 'error')
  } finally {
    busyId.value = 0
  }
}

onMounted(load)
</script>

<template>
  <div class="today">
    <header class="today__head">
      <div>
        <p class="today__date num">{{ today }} · {{ weekdayCn(new Date()) }}</p>
        <p class="today__shichen">{{ shichenLabel() }}<template v-if="solarTerm"> · {{ solarTerm }}</template></p>
      </div>
      <SButton variant="gold" @click="load">刷新</SButton>
    </header>

    <SSkeleton v-if="loading" :lines="4" />
    <p v-else-if="errorText" class="today__error">{{ errorText }}</p>

    <template v-else-if="overview">
      <!-- 概览四联卡 -->
      <div class="today__stats">
        <SCard class="today__stat">
          <p class="today__stat-label">今日日程</p>
          <p class="today__stat-value num">{{ overview.totalCount }}</p>
        </SCard>
        <SCard class="today__stat">
          <p class="today__stat-label">已落梭</p>
          <p class="today__stat-value num">{{ overview.completedCount }}</p>
        </SCard>
        <SCard class="today__stat">
          <p class="today__stat-label">穿梭中</p>
          <p class="today__stat-value num">{{ overview.inProgressCount }}</p>
        </SCard>
        <SCard class="today__stat">
          <p class="today__stat-label">专注时长</p>
          <p class="today__stat-value num">{{ formatDuration(overview.focusTime) }}</p>
        </SCard>
      </div>

      <!-- 当前穿梭 -->
      <SCard v-if="timer.active" class="today__active shimmer-border">
        <div class="today__active-main">
          <p class="today__active-label">正在穿梭</p>
          <h3 class="today__active-title">{{ timer.active.title }}</h3>
          <p class="today__active-timer timer-display">{{ formatTimer(timer.elapsedSeconds) }}</p>
        </div>
        <SSeal form="solid-gold" text="穿梭中" breath :size="56" />
      </SCard>

      <!-- 即将开梭 -->
      <section class="today__upcoming">
        <h3 class="today__section-title">即将开梭</h3>
        <SEmpty
          v-if="!overview.upcomingSchedules.length"
          text="今日余下的经线已经织完"
          hint="去「日程清单」排一条新纬线吧"
          icon="calendar"
        />
        <div v-else class="today__list">
          <ScheduleItem
            v-for="s in overview.upcomingSchedules"
            :key="s.id"
            :schedule="s"
            :busy="busyId === s.id"
            :manage="false"
            class="weave-in"
            @action="onAction"
          />
        </div>
      </section>
    </template>
  </div>
</template>

<style scoped>
.today { display: flex; flex-direction: column; gap: var(--sorts-space-4); max-width: 880px; }
.today__head { display: flex; align-items: flex-end; justify-content: space-between; }
.today__date { font-size: var(--sorts-text-lg); color: var(--sorts-ink); margin: 0; }
.today__shichen { font-size: var(--sorts-text-sm); color: var(--sorts-text-faint); margin: 4px 0 0; }
.today__error { color: var(--sorts-color-late-600); }
.today__stats { display: grid; grid-template-columns: repeat(4, 1fr); gap: var(--sorts-space-3); }
.today__stat-label { margin: 0; font-size: var(--sorts-text-xs); color: var(--sorts-text-faint); }
.today__stat-value { margin: 6px 0 0; font-size: var(--sorts-text-2xl); color: var(--sorts-ink); }
.today__active { display: flex; align-items: center; justify-content: space-between; }
.today__active-label { margin: 0; font-size: var(--sorts-text-xs); color: var(--sorts-color-gold-700); }
.today__active-title { margin: 4px 0; color: var(--sorts-ink); }
.today__active-timer { margin: 0; font-size: var(--sorts-text-3xl); color: var(--sorts-color-gold-700); }
.today__section-title { margin: 0 0 var(--sorts-space-3); font-size: var(--sorts-text-base); color: var(--sorts-ink); }
.today__list { display: flex; flex-direction: column; gap: var(--sorts-space-2); }
@media (max-width: 768px) {
  .today__stats { grid-template-columns: repeat(2, 1fr); }
}
</style>
