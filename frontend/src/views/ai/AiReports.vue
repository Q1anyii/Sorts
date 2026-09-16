<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { dailySummaryStream, getReport, listReports, monthlySummary, yearlySummary } from '@/api/ai'
import type { AIReportInfo, PageData } from '@/types'
import type { SseSession } from '@/api/sse'
import { ApiError } from '@/api/error'
import { useAppStore } from '@/stores/app'
import { REPORT_TYPE_META } from '@/utils/status'
import { formatDateTime, formatDuration } from '@/utils/datetime'
import SButton from '@/components/ui/SButton.vue'
import SCard from '@/components/ui/SCard.vue'
import SSelect from '@/components/ui/SSelect.vue'
import SModal from '@/components/ui/SModal.vue'
import SSkeleton from '@/components/ui/SSkeleton.vue'
import SEmpty from '@/components/ui/SEmpty.vue'
import STag from '@/components/ui/STag.vue'

/**
 * 梭影报告：
 * - 今日梭影：SSE 流式生成，逐字上屏
 * - 月度/年度：异步 202 → 轮询 /reports/{id}（3s 一次，最多 60 次）
 * - 历史报告：分页列表 + 弹窗查看全文
 */
const app = useAppStore()

/* ---------- 生成区 ---------- */
const generating = ref<'daily' | 'monthly' | 'yearly' | null>(null)
const streamingText = ref('')
const streamingTitle = ref('')

let session: SseSession | null = null
let pollTimer = 0

function generateDaily() {
  if (generating.value) return
  generating.value = 'daily'
  streamingTitle.value = '今日梭影'
  streamingText.value = ''
  session = dailySummaryStream(
    {},
    {
      onDelta: (c) => {
        streamingText.value += c
      },
      onDone: () => {
        generating.value = null
        loadList()
      },
      onError: (e: ApiError) => {
        generating.value = null
        app.toast(`生成失败：${e.message}`, 'error')
      }
    }
  )
  session.finished.finally(() => {
    if (generating.value === 'daily') generating.value = null
  })
}

async function generateAsync(kind: 'monthly' | 'yearly') {
  if (generating.value) return
  generating.value = kind
  streamingTitle.value = kind === 'monthly' ? '月度梭影' : '年度织锦'
  streamingText.value = ''
  try {
    const fn = kind === 'monthly' ? monthlySummary : yearlySummary
    const res = await fn()
    if (res.status === 'COMPLETED') {
      generating.value = null
      loadList()
      return
    }
    pollReport(res.reportId, res.estimatedSeconds ?? 30)
  } catch (e) {
    generating.value = null
    app.toast(e instanceof ApiError ? e.message : '生成失败', 'error')
  }
}

function pollReport(id: number, estimated: number) {
  let tries = 0
  const maxTries = Math.max(20, Math.ceil((estimated * 2) / 3))
  pollTimer = window.setInterval(async () => {
    tries += 1
    try {
      const report = await getReport(id)
      if (report.status === 'COMPLETED') {
        window.clearInterval(pollTimer)
        generating.value = null
        viewing.value = report
        loadList()
      } else if (report.status === 'FAILED' || tries >= maxTries) {
        window.clearInterval(pollTimer)
        generating.value = null
        app.toast(report.status === 'FAILED' ? '报告生成失败' : '等待超时，请稍后在列表查看', 'error')
      }
    } catch {
      /* 单次轮询失败忽略，等下一拍 */
    }
  }, 3000)
}

/* ---------- 历史列表 ---------- */
const list = ref<PageData<AIReportInfo> | null>(null)
const listLoading = ref(true)
const typeFilter = ref('')
const page = ref(1)
const viewing = ref<AIReportInfo | null>(null)

const TYPE_OPTIONS = [
  { value: '', label: '全部类型' },
  { value: 'DAILY', label: '今日梭影' },
  { value: 'WEEKLY', label: '本周梭影' },
  { value: 'MONTHLY', label: '月度梭影' },
  { value: 'YEARLY', label: '年度织锦' }
]

async function loadList() {
  listLoading.value = true
  try {
    list.value = await listReports(typeFilter.value || undefined, page.value, 8)
  } catch (e) {
    app.toast(e instanceof ApiError ? e.message : '加载失败', 'error')
  } finally {
    listLoading.value = false
  }
}

function onFilterChange() {
  page.value = 1
  loadList()
}

async function openReport(item: AIReportInfo) {
  if (item.status === 'GENERATING') {
    app.toast('这份报告还在织，稍等片刻', 'info')
    return
  }
  try {
    viewing.value = await getReport(item.id)
  } catch (e) {
    app.toast(e instanceof ApiError ? e.message : '读取失败', 'error')
  }
}

onMounted(loadList)
onUnmounted(() => {
  session?.abort()
  if (pollTimer) window.clearInterval(pollTimer)
})
</script>

<template>
  <div class="reports">
    <!-- 生成区 -->
    <SCard class="reports__gen">
      <div class="reports__gen-btns">
        <SButton variant="gold" :loading="generating === 'daily'" :disabled="!!generating" @click="generateDaily">
          今日梭影
        </SButton>
        <SButton variant="primary" :loading="generating === 'monthly'" :disabled="!!generating" @click="generateAsync('monthly')">
          月度梭影
        </SButton>
        <SButton variant="primary" :loading="generating === 'yearly'" :disabled="!!generating" @click="generateAsync('yearly')">
          年度织锦
        </SButton>
      </div>
      <p v-if="generating" class="reports__gen-status">
        正在织「{{ streamingTitle }}」<template v-if="generating !== 'daily'">，月报/年报需 1~2 分钟，可稍后在列表查看</template>…
      </p>
      <p v-if="streamingText" class="reports__streaming">{{ streamingText }}</p>
    </SCard>

    <!-- 历史列表 -->
    <div class="reports__head">
      <h3 class="reports__title">历史报告</h3>
      <SSelect v-model="typeFilter" :options="TYPE_OPTIONS" class="reports__filter" @update:model-value="onFilterChange" />
    </div>

    <SSkeleton v-if="listLoading" :lines="4" />
    <SEmpty v-else-if="!list || !list.list.length" text="还没有梭影报告" hint="点上方按钮织一份" icon="chart" />

    <div v-else class="reports__list">
      <button
        v-for="r in list.list"
        :key="r.id"
        class="reports__item weave-in"
        @click="openReport(r)"
      >
        <div class="reports__item-main">
          <span class="reports__item-title">{{ r.title }}</span>
          <span class="reports__item-meta num">{{ formatDateTime(r.generatedAt) }}</span>
        </div>
        <div class="reports__item-side">
          <STag>{{ REPORT_TYPE_META[r.type] ?? r.type }}</STag>
          <span v-if="r.status === 'GENERATING'" class="reports__item-status">织锦中…</span>
          <span v-else-if="r.status === 'FAILED'" class="reports__item-status reports__item-status--failed">失败</span>
        </div>
      </button>
      <div class="reports__pager num">
        <SButton variant="ghost" size="sm" :disabled="page <= 1" @click="page -= 1; loadList()">上一页</SButton>
        <span>第 {{ page }} 页 · 共 {{ list.total }} 份</span>
        <SButton variant="ghost" size="sm" :disabled="list.list.length < 8" @click="page += 1; loadList()">下一页</SButton>
      </div>
    </div>

    <!-- 报告全文 -->
    <SModal :open="!!viewing" :title="viewing?.title ?? ''" :width="640" @close="viewing = null">
      <div v-if="viewing" class="reports__detail">
        <p class="reports__detail-meta num">
          {{ formatDateTime(viewing.generatedAt) }}
          <template v-if="viewing.completionRate != null"> · 落梭率 {{ Math.round(viewing.completionRate * 100) }}%</template>
          <template v-if="viewing.totalFocusTime != null"> · 专注 {{ formatDuration(viewing.totalFocusTime) }}</template>
        </p>
        <p class="reports__detail-content">{{ viewing.content }}</p>
        <template v-if="viewing.highlights?.length">
          <h4 class="reports__detail-sub">高光</h4>
          <ul class="reports__detail-ul">
            <li v-for="(h, i) in viewing.highlights" :key="i">{{ h }}</li>
          </ul>
        </template>
        <template v-if="viewing.suggestions?.length">
          <h4 class="reports__detail-sub">织师建议</h4>
          <ul class="reports__detail-ul">
            <li v-for="(s, i) in viewing.suggestions" :key="i">{{ s }}</li>
          </ul>
        </template>
      </div>
    </SModal>
  </div>
</template>

<style scoped>
.reports { display: flex; flex-direction: column; gap: var(--sorts-space-4); }
.reports__gen { display: flex; flex-direction: column; gap: var(--sorts-space-3); }
.reports__gen-btns { display: flex; gap: var(--sorts-space-2); flex-wrap: wrap; }
.reports__gen-status { margin: 0; font-size: var(--sorts-text-sm); color: var(--sorts-color-gold-800); }
.reports__streaming {
  margin: 0;
  font-size: var(--sorts-text-sm);
  color: var(--sorts-text);
  white-space: pre-wrap;
  line-height: 1.7;
  border-top: 1px dashed var(--sorts-border);
  padding-top: var(--sorts-space-3);
}
.reports__head { display: flex; align-items: center; justify-content: space-between; }
.reports__title { margin: 0; font-size: var(--sorts-text-base); color: var(--sorts-ink); }
.reports__filter { width: 140px; }
.reports__list { display: flex; flex-direction: column; gap: var(--sorts-space-2); }
.reports__item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sorts-space-3);
  padding: 12px var(--sorts-space-4);
  background: var(--sorts-surface);
  border: 1px solid var(--sorts-border);
  border-radius: var(--sorts-radius-ctl);
  cursor: pointer;
  text-align: left;
  transition: border-color var(--sorts-dur-fast) var(--sorts-ease-out);
}
.reports__item:hover { border-color: var(--sorts-color-gold-500); }
.reports__item-main { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
.reports__item-title { color: var(--sorts-ink); font-size: var(--sorts-text-base); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.reports__item-meta { color: var(--sorts-text-faint); font-size: var(--sorts-text-xs); }
.reports__item-side { display: flex; align-items: center; gap: 8px; flex: none; }
.reports__item-status { font-size: var(--sorts-text-xs); color: var(--sorts-color-gold-700); }
.reports__item-status--failed { color: var(--sorts-color-late-600); }
.reports__pager {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--sorts-space-3);
  color: var(--sorts-text-faint);
  font-size: var(--sorts-text-sm);
  margin-top: var(--sorts-space-2);
}
.reports__detail { display: flex; flex-direction: column; gap: var(--sorts-space-3); }
.reports__detail-meta { margin: 0; font-size: var(--sorts-text-xs); color: var(--sorts-text-faint); }
.reports__detail-content { margin: 0; color: var(--sorts-text); line-height: 1.8; white-space: pre-wrap; }
.reports__detail-sub { margin: 0; font-size: var(--sorts-text-sm); color: var(--sorts-ink); }
.reports__detail-ul { margin: 0; padding-left: 18px; color: var(--sorts-text); line-height: 1.8; font-size: var(--sorts-text-sm); }
</style>
