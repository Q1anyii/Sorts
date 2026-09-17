<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { getCalendarMonth } from '@/api/calendar'
import type { CalendarDayVO, CalendarResponse } from '@/types'
import { useAppStore } from '@/stores/app'
import { ApiError } from '@/api/error'
import { formatDuration, todayStr } from '@/utils/datetime'
import SIcon from '@/components/ui/SIcon.vue'
import SSkeleton from '@/components/ui/SSkeleton.vue'
import SEmpty from '@/components/ui/SEmpty.vue'
import ScheduleItem from '@/components/schedule/ScheduleItem.vue'

/** 织历：月格视图 + 选中日的日程明细 */
const app = useAppStore()

const now = new Date()
const year = ref(now.getFullYear())
const month = ref(now.getMonth() + 1)
const data = ref<CalendarResponse | null>(null)
const loading = ref(true)
const selectedDate = ref(todayStr())

const selectedDay = computed<CalendarDayVO | null>(() => {
  return data.value?.days.find((d) => d.date === selectedDate.value) ?? null
})

/** 月历网格：CalendarDayVO 从 1 号开始；前面补 dayOfWeek 个空格（后端 dayOfWeek 以周一=1 计） */
const leadingBlanks = computed(() => {
  const first = data.value?.days[0]
  return first ? first.dayOfWeek - 1 : 0
})

async function load() {
  loading.value = true
  try {
    data.value = await getCalendarMonth(year.value, month.value)
  } catch (e) {
    app.toast(e instanceof ApiError ? e.message : '加载失败', 'error')
  } finally {
    loading.value = false
  }
}

function shiftMonth(delta: number) {
  const d = new Date(year.value, month.value - 1 + delta, 1)
  year.value = d.getFullYear()
  month.value = d.getMonth() + 1
  load()
}

function backToToday() {
  const t = new Date()
  year.value = t.getFullYear()
  month.value = t.getMonth() + 1
  selectedDate.value = todayStr()
  load()
}

onMounted(load)
</script>

<template>
  <div class="cal">
    <header class="cal__head">
      <div class="cal__nav">
        <button class="cal__nav-btn" aria-label="上一月" @click="shiftMonth(-1)">
          <SIcon name="chevron-left" :size="18" />
        </button>
        <h2 class="cal__month num">{{ year }} 年 {{ month }} 月</h2>
        <button class="cal__nav-btn" aria-label="下一月" @click="shiftMonth(1)">
          <SIcon name="chevron-right" :size="18" />
        </button>
      </div>
      <button class="cal__today-btn" @click="backToToday">回今日</button>
    </header>

    <SSkeleton v-if="loading" :lines="6" />

    <div v-else-if="data" class="cal__body">
      <!-- 月格 -->
      <section class="cal__grid-wrap">
        <div class="cal__weekdays">
          <span v-for="w in ['一', '二', '三', '四', '五', '六', '日']" :key="w">{{ w }}</span>
        </div>
        <div class="cal__grid">
          <span v-for="i in leadingBlanks" :key="`b${i}`" class="cal__cell cal__cell--blank" />
          <button
            v-for="day in data.days"
            :key="day.date"
            class="cal__cell"
            :class="{
              'is-today': day.isToday,
              'is-selected': day.date === selectedDate,
              'has-items': day.totalCount > 0
            }"
            @click="selectedDate = day.date"
          >
            <span class="cal__date num">{{ Number(day.date.slice(-2)) }}</span>
            <span v-if="day.totalCount" class="cal__count num">
              {{ day.completedCount }}/{{ day.totalCount }}
            </span>
            <span v-if="day.focusTime" class="cal__dot" :style="{ opacity: Math.min(1, day.focusTime / 7200 + .25) }" />
          </button>
        </div>
        <p class="cal__summary num">
          本月 {{ data.totalCount }} 条 · 落梭 {{ data.completedCount }} · 专注 {{ formatDuration(data.focusTime) }}
        </p>
      </section>

      <!-- 选中日明细 -->
      <aside class="cal__detail">
        <h3 class="cal__detail-title num">{{ selectedDate }}</h3>
        <SEmpty v-if="!selectedDay || !selectedDay.schedules.length" text="这一天没有经纬" icon="calendar" />
        <div v-else class="cal__detail-list">
          <ScheduleItem
            v-for="s in selectedDay.schedules"
            :key="s.id"
            :schedule="s"
            :manage="false"
            @action="() => {}"
          />
        </div>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.cal { display: flex; flex-direction: column; gap: var(--sorts-space-4); }
.cal__head { display: flex; align-items: center; justify-content: space-between; }
.cal__nav { display: flex; align-items: center; gap: var(--sorts-space-2); }
.cal__month { margin: 0; font-size: var(--sorts-text-xl); color: var(--sorts-ink); min-width: 160px; text-align: center; }
.cal__nav-btn, .cal__today-btn {
  border: 1px solid var(--sorts-border);
  background: var(--sorts-surface);
  color: var(--sorts-text);
  border-radius: var(--sorts-radius-ctl);
  cursor: pointer;
  padding: 6px 10px;
  display: inline-flex;
  align-items: center;
}
.cal__today-btn { font-size: var(--sorts-text-sm); }
.cal__nav-btn:hover, .cal__today-btn:hover { background: var(--sorts-color-line-100); }

.cal__body { display: grid; grid-template-columns: 1fr 340px; gap: var(--sorts-space-4); align-items: start; }
.cal__grid-wrap {
  background: var(--sorts-surface);
  border: 1px solid var(--sorts-border);
  border-radius: var(--sorts-radius-box);
  box-shadow: var(--sorts-shadow-1);
  padding: var(--sorts-space-4);
}
.cal__weekdays {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  text-align: center;
  font-size: var(--sorts-text-xs);
  color: var(--sorts-text-faint);
  padding-bottom: var(--sorts-space-2);
}
.cal__grid { display: grid; grid-template-columns: repeat(7, 1fr); gap: 4px; }
.cal__cell {
  position: relative;
  aspect-ratio: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  border: 1px solid transparent;
  border-radius: var(--sorts-radius-ctl);
  background: transparent;
  cursor: pointer;
  transition: background-color var(--sorts-dur-fast) var(--sorts-ease-out);
}
.cal__cell--blank { cursor: default; }
.cal__cell:not(.cal__cell--blank):hover { background: var(--sorts-color-line-100); }
.cal__cell.is-today { border-color: var(--sorts-color-gold-500); }
.cal__cell.is-selected { background: var(--sorts-color-arrow-100); border-color: var(--sorts-color-arrow-500); }
.cal__date { font-size: var(--sorts-text-sm); color: var(--sorts-text); }
.cal__count { font-size: 10px; color: var(--sorts-text-faint); }
.cal__dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--sorts-color-gold-500);
}
.cal__summary {
  margin: var(--sorts-space-3) 0 0;
  font-size: var(--sorts-text-sm);
  color: var(--sorts-text-faint);
  text-align: center;
}
.cal__detail {
  background: var(--sorts-surface);
  border: 1px solid var(--sorts-border);
  border-radius: var(--sorts-radius-box);
  box-shadow: var(--sorts-shadow-1);
  padding: var(--sorts-space-4);
  display: flex;
  flex-direction: column;
  gap: var(--sorts-space-3);
  max-height: 70vh;
  overflow-y: auto;
}
.cal__detail-title { margin: 0; font-size: var(--sorts-text-base); color: var(--sorts-ink); }
.cal__detail-list { display: flex; flex-direction: column; gap: var(--sorts-space-2); }
@media (max-width: 960px) {
  .cal__body { grid-template-columns: 1fr; }
}
</style>
