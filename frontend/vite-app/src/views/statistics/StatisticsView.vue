<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { getStatisticsSummary } from '@/api/statistics'
import type { StatisticsPeriod, StatisticsSummaryVO } from '@/types'
import { useAppStore } from '@/stores/app'
import { ApiError } from '@/api/error'
import { formatDuration } from '@/utils/datetime'
import SCard from '@/components/ui/SCard.vue'
import STabs from '@/components/ui/STabs.vue'
import SSkeleton from '@/components/ui/SSkeleton.vue'
import SEmpty from '@/components/ui/SEmpty.vue'

/** 纹谱统计：周期汇总卡 + 专注趋势（SVG 面积图）+ 标签分布（横条） */
const app = useAppStore()

const period = ref<StatisticsPeriod>('week')
const data = ref<StatisticsSummaryVO | null>(null)
const loading = ref(true)

async function load() {
  loading.value = true
  try {
    data.value = await getStatisticsSummary(period.value)
  } catch (e) {
    app.toast(e instanceof ApiError ? e.message : '加载失败', 'error')
  } finally {
    loading.value = false
  }
}

function onPeriodChange(v: string) {
  period.value = v as StatisticsPeriod
  load()
}

/* ---------- 趋势图几何（viewBox 680×200，纯 SVG 无依赖） ---------- */
const CHART_W = 680
const CHART_H = 200
const PAD = 8

const trend = computed(() => data.value?.dailyTrend ?? [])
const maxTrend = computed(() => Math.max(1, ...trend.value.map((p) => p.totalDuration)))

const trendPoints = computed(() => {
  const pts = trend.value
  if (!pts.length) return ''
  const stepX = pts.length > 1 ? (CHART_W - PAD * 2) / (pts.length - 1) : 0
  return pts
    .map((p, i) => {
      const x = PAD + i * stepX
      const y = CHART_H - PAD - (p.totalDuration / maxTrend.value) * (CHART_H - PAD * 2)
      return `${x.toFixed(1)},${y.toFixed(1)}`
    })
    .join(' ')
})

const trendArea = computed(() => {
  if (!trendPoints.value) return ''
  const pts = trend.value
  const stepX = pts.length > 1 ? (CHART_W - PAD * 2) / (pts.length - 1) : 0
  const lastX = PAD + (pts.length - 1) * stepX
  return `${PAD},${CHART_H - PAD} ${trendPoints.value} ${lastX.toFixed(1)},${CHART_H - PAD}`
})

/* ---------- 标签分布 ---------- */
const tags = computed(() => (data.value?.tagDistribution ?? []).slice(0, 8))
const maxTag = computed(() => Math.max(1, ...tags.value.map((t) => t.totalDuration)))

const completionPct = computed(() => Math.round((data.value?.completionRate ?? 0) * 100))

onMounted(load)
</script>

<template>
  <div class="stats">
    <STabs
      :model-value="period"
      :items="[
        { value: 'day', label: '日' },
        { value: 'week', label: '周' },
        { value: 'month', label: '月' },
        { value: 'year', label: '年' }
      ]"
      @update:model-value="onPeriodChange"
    />

    <SSkeleton v-if="loading" :lines="5" />

    <template v-else-if="data">
      <!-- 汇总卡 -->
      <div class="stats__cards">
        <SCard class="stats__card">
          <p class="stats__label">总日程</p>
          <p class="stats__value num">{{ data.totalSchedules }}</p>
        </SCard>
        <SCard class="stats__card">
          <p class="stats__label">落梭率</p>
          <p class="stats__value num">{{ completionPct }}%</p>
        </SCard>
        <SCard class="stats__card">
          <p class="stats__label">总专注</p>
          <p class="stats__value num">{{ formatDuration(data.totalFocusTime) }}</p>
        </SCard>
        <SCard class="stats__card">
          <p class="stats__label">日均专注</p>
          <p class="stats__value num">{{ formatDuration(data.avgFocusTime) }}</p>
        </SCard>
      </div>

      <!-- 专注趋势 -->
      <SCard class="stats__panel">
        <h3 class="stats__panel-title">专注走势 <span class="stats__panel-sub num">{{ data.startDate }} ~ {{ data.endDate }}</span></h3>
        <SEmpty v-if="!trend.length" text="这段日子还没有织痕" icon="chart" />
        <svg v-else :viewBox="`0 0 ${CHART_W} ${CHART_H}`" class="stats__chart" role="img" aria-label="每日专注时长走势">
          <polygon :points="trendArea" class="stats__chart-area" />
          <polyline :points="trendPoints" class="stats__chart-line" />
        </svg>
      </SCard>

      <!-- 标签分布 -->
      <SCard class="stats__panel">
        <h3 class="stats__panel-title">经纬分布（按标签）</h3>
        <SEmpty v-if="!tags.length" text="还没有标签数据" hint="给日程打上标签，这里就会开花" icon="chart" />
        <ul v-else class="stats__tags">
          <li v-for="t in tags" :key="t.tag" class="stats__tag-row">
            <span class="stats__tag-name">#{{ t.tag }}</span>
            <span class="stats__tag-bar-wrap">
              <span class="stats__tag-bar" :style="{ width: `${(t.totalDuration / maxTag) * 100}%` }" />
            </span>
            <span class="stats__tag-num num">{{ formatDuration(t.totalDuration) }} · {{ t.percentage }}%</span>
          </li>
        </ul>
      </SCard>
    </template>
  </div>
</template>

<style scoped>
.stats { display: flex; flex-direction: column; gap: var(--sorts-space-4); max-width: 880px; }
.stats__cards { display: grid; grid-template-columns: repeat(4, 1fr); gap: var(--sorts-space-3); }
.stats__label { margin: 0; font-size: var(--sorts-text-xs); color: var(--sorts-text-faint); }
.stats__value { margin: 6px 0 0; font-size: var(--sorts-text-2xl); color: var(--sorts-ink); }
.stats__panel-title { margin: 0 0 var(--sorts-space-3); font-size: var(--sorts-text-base); color: var(--sorts-ink); }
.stats__panel-sub { font-size: var(--sorts-text-xs); color: var(--sorts-text-faint); font-weight: 400; margin-left: 8px; }
.stats__chart { width: 100%; height: auto; display: block; }
.stats__chart-line { fill: none; stroke: var(--sorts-color-arrow-600); stroke-width: 2; }
.stats__chart-area { fill: var(--sorts-color-arrow-100); opacity: .8; }
.stats__tags { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 10px; }
.stats__tag-row { display: grid; grid-template-columns: 90px 1fr 150px; align-items: center; gap: var(--sorts-space-3); }
.stats__tag-name { font-size: var(--sorts-text-sm); color: var(--sorts-text); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.stats__tag-bar-wrap { height: 10px; background: var(--sorts-color-line-100); border-radius: 5px; overflow: hidden; }
.stats__tag-bar { display: block; height: 100%; background: var(--sorts-color-gold-500); border-radius: 5px; }
.stats__tag-num { font-size: var(--sorts-text-xs); color: var(--sorts-text-faint); text-align: right; }
@media (max-width: 768px) {
  .stats__cards { grid-template-columns: repeat(2, 1fr); }
  .stats__tag-row { grid-template-columns: 70px 1fr 110px; }
}
</style>
