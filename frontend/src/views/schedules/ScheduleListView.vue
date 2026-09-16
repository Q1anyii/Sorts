<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import {
  createSchedule,
  deleteSchedule,
  listSchedules,
  updateSchedule
} from '@/api/schedule'
import type { PageData, ScheduleQueryParams, ScheduleSaveRequest, ScheduleVO } from '@/types'
import { useTimerStore } from '@/stores/timer'
import { useAppStore } from '@/stores/app'
import { ApiError } from '@/api/error'
import { SCHEDULE_STATUS_META, PRIORITY_META } from '@/utils/status'
import type { Priority, ScheduleStatus } from '@/types'
import type { TimerAction } from '@/utils/status'
import SButton from '@/components/ui/SButton.vue'
import SInput from '@/components/ui/SInput.vue'
import SSelect from '@/components/ui/SSelect.vue'
import SSkeleton from '@/components/ui/SSkeleton.vue'
import SEmpty from '@/components/ui/SEmpty.vue'
import SModal from '@/components/ui/SModal.vue'
import ScheduleItem from '@/components/schedule/ScheduleItem.vue'
import ScheduleFormModal from '@/components/schedule/ScheduleFormModal.vue'

/** 日程清单：筛选 + 分页 + 新建/编辑/删除 + 状态机动作 */
const timer = useTimerStore()
const app = useAppStore()

const STATUS_OPTIONS = [
  { value: '', label: '全部状态' },
  ...(Object.keys(SCHEDULE_STATUS_META) as ScheduleStatus[]).map((s) => ({
    value: s,
    label: SCHEDULE_STATUS_META[s].label
  }))
]
const PRIORITY_OPTIONS = [
  { value: '', label: '全部优先级' },
  ...(Object.keys(PRIORITY_META) as Priority[]).map((p) => ({ value: p, label: PRIORITY_META[p].label }))
]

const query = reactive({ keyword: '', status: '', priority: '', date: '' })
const page = ref(1)
const pageSize = 10
const data = ref<PageData<ScheduleVO> | null>(null)
const loading = ref(true)
const busyId = ref(0)

const formOpen = ref(false)
const editing = ref<ScheduleVO | null>(null)
const saving = ref(false)

const removing = ref<ScheduleVO | null>(null)
const removeBusy = ref(false)

async function load() {
  loading.value = true
  try {
    const params: ScheduleQueryParams = {
      page: page.value,
      pageSize,
      keyword: query.keyword || undefined,
      status: (query.status || undefined) as ScheduleStatus | undefined,
      priority: (query.priority || undefined) as Priority | undefined,
      date: query.date || undefined,
      sort: 'plannedStartTime',
      order: 'asc'
    }
    data.value = await listSchedules(params)
  } catch (e) {
    app.toast(e instanceof ApiError ? e.message : '加载失败', 'error')
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  load()
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

function openCreate() {
  editing.value = null
  formOpen.value = true
}

function openEdit(s: ScheduleVO) {
  editing.value = s
  formOpen.value = true
}

async function onSubmit(form: ScheduleSaveRequest, id?: number) {
  saving.value = true
  try {
    if (id) {
      await updateSchedule(id, form)
      app.toast('已保存', 'success')
    } else {
      await createSchedule(form)
      app.toast('新日程已织入', 'success')
    }
    formOpen.value = false
    await load()
  } catch (e) {
    app.toast(e instanceof ApiError ? e.message : '保存失败', 'error')
  } finally {
    saving.value = false
  }
}

async function confirmRemove() {
  if (!removing.value) return
  removeBusy.value = true
  try {
    await deleteSchedule(removing.value.id)
    app.toast('已删除', 'success')
    removing.value = null
    // 删除的是本页最后一条且不是第一页 → 回退一页
    if (data.value && data.value.list.length === 1 && page.value > 1) page.value -= 1
    await load()
  } catch (e) {
    app.toast(e instanceof ApiError ? e.message : '删除失败', 'error')
  } finally {
    removeBusy.value = false
  }
}

function totalPages(): number {
  return data.value ? Math.max(1, Math.ceil(data.value.total / pageSize)) : 1
}

function goPage(p: number) {
  if (p < 1 || p > totalPages()) return
  page.value = p
  load()
}

onMounted(load)
</script>

<template>
  <div class="sch-list">
    <!-- 筛选条 -->
    <div class="sch-list__filters">
      <SInput v-model="query.keyword" placeholder="搜索标题 / 备注" class="sch-list__search" @enter="search" />
      <SSelect v-model="query.status" :options="STATUS_OPTIONS" class="sch-list__select" />
      <SSelect v-model="query.priority" :options="PRIORITY_OPTIONS" class="sch-list__select" />
      <SInput v-model="query.date" type="date" class="sch-list__date" />
      <SButton variant="ghost" @click="search">筛选</SButton>
      <SButton variant="gold" @click="openCreate">+ 新日程</SButton>
    </div>

    <SSkeleton v-if="loading" :lines="5" />
    <SEmpty v-else-if="!data || !data.list.length" text="这一页没有日程" hint="调整筛选，或织一条新日程" icon="list">
      <SButton variant="gold" size="sm" @click="openCreate">+ 新日程</SButton>
    </SEmpty>

    <template v-else>
      <div class="sch-list__items">
        <ScheduleItem
          v-for="s in data.list"
          :key="s.id"
          :schedule="s"
          :busy="busyId === s.id"
          class="weave-in"
          @action="onAction"
          @edit="openEdit"
          @remove="removing = $event"
        />
      </div>

      <!-- 分页 -->
      <div class="sch-list__pager num">
        <SButton variant="ghost" size="sm" :disabled="page <= 1" @click="goPage(page - 1)">上一页</SButton>
        <span>{{ page }} / {{ totalPages() }}（共 {{ data.total }} 条）</span>
        <SButton variant="ghost" size="sm" :disabled="page >= totalPages()" @click="goPage(page + 1)">下一页</SButton>
      </div>
    </template>

    <!-- 新建 / 编辑 -->
    <ScheduleFormModal
      :open="formOpen"
      :schedule="editing"
      :saving="saving"
      @close="formOpen = false"
      @submit="onSubmit"
    />

    <!-- 删除确认 -->
    <SModal :open="!!removing" title="删除日程" :width="400" @close="removing = null">
      <p class="sch-list__remove-text">
        确定删除「{{ removing?.title }}」吗？此操作不可恢复。
      </p>
      <template #footer>
        <SButton variant="ghost" @click="removing = null">再想想</SButton>
        <SButton variant="danger" :loading="removeBusy" @click="confirmRemove">删除</SButton>
      </template>
    </SModal>
  </div>
</template>

<style scoped>
.sch-list { display: flex; flex-direction: column; gap: var(--sorts-space-4); }
.sch-list__filters {
  display: flex;
  gap: var(--sorts-space-2);
  align-items: center;
  flex-wrap: wrap;
}
.sch-list__search { flex: 1; min-width: 180px; }
.sch-list__select { width: 130px; }
.sch-list__date { width: 150px; }
.sch-list__items { display: flex; flex-direction: column; gap: var(--sorts-space-2); }
.sch-list__pager {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--sorts-space-3);
  color: var(--sorts-text-faint);
  font-size: var(--sorts-text-sm);
}
.sch-list__remove-text { margin: 0; color: var(--sorts-text); }
</style>
