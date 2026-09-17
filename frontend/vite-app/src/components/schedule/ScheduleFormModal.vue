<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import type { Priority, ScheduleSaveRequest, ScheduleVO } from '@/types'
import { PRIORITY_META } from '@/utils/status'
import { ApiError } from '@/api/error'
import SModal from '@/components/ui/SModal.vue'
import SField from '@/components/ui/SField.vue'
import SInput from '@/components/ui/SInput.vue'
import SSelect from '@/components/ui/SSelect.vue'
import SButton from '@/components/ui/SButton.vue'

/**
 * 新建 / 编辑日程弹窗。
 * plannedStartTime 用 datetime-local，提交时转为后端 ISO（yyyy-MM-ddTHH:mm:ss）。
 */
const props = defineProps<{
  open: boolean
  /** 传入则为编辑，否则新建 */
  schedule?: ScheduleVO | null
  saving?: boolean
}>()

const emit = defineEmits<{
  close: []
  submit: [data: ScheduleSaveRequest, id?: number]
}>()

const PRIORITY_OPTIONS = (Object.keys(PRIORITY_META) as Priority[]).map((p) => ({
  value: p,
  label: PRIORITY_META[p].label
}))

const form = reactive({
  title: '',
  plannedStartTime: '',
  plannedDuration: '60',
  priority: 'MEDIUM' as Priority,
  tags: '',
  description: ''
})

const errorText = ref('')

watch(
  () => [props.open, props.schedule] as const,
  ([open, schedule]) => {
    if (!open) return
    errorText.value = ''
    if (schedule) {
      form.title = schedule.title
      form.plannedStartTime = schedule.plannedStartTime.slice(0, 16)
      form.plannedDuration = String(schedule.plannedDuration)
      form.priority = schedule.priority
      form.tags = schedule.tags.join(', ')
      form.description = schedule.description ?? ''
    } else {
      const d = new Date()
      d.setMinutes(0, 0, 0)
      d.setHours(d.getHours() + 1)
      const p = (n: number) => String(n).padStart(2, '0')
      form.title = ''
      form.plannedStartTime = `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:00`
      form.plannedDuration = 60
      form.priority = 'MEDIUM'
      form.tags = ''
      form.description = ''
    }
  },
  { immediate: true }
)

function onSubmit() {
  if (!form.title.trim()) {
    errorText.value = '请输入标题'
    return
  }
  if (!form.plannedStartTime) {
    errorText.value = '请选择计划开始时间'
    return
  }
  const tags = form.tags
    .split(/[,，]/)
    .map((t) => t.trim())
    .filter(Boolean)
  emit('submit', {
    title: form.title.trim(),
    description: form.description.trim() || undefined,
    plannedStartTime: `${form.plannedStartTime}:00`.slice(0, 19),
    plannedDuration: form.plannedDuration || undefined,
    priority: form.priority,
    tags: tags.length ? tags : undefined
  }, props.schedule?.id)
}
</script>

<template>
  <SModal :open="open" :title="schedule ? '编辑日程' : '织一条新日程'" width={520} @close="emit('close')">
    <div class="sch-form">
      <SField label="标题" required>
        <SInput v-model="form.title" placeholder="要织什么？" :maxlength="64" />
      </SField>
      <div class="sch-form__row">
        <SField label="计划开始" required>
          <SInput v-model="form.plannedStartTime" type="datetime-local" />
        </SField>
        <SField label="时长（分钟）">
          <SInput v-model="form.plannedDuration" type="number" />
        </SField>
      </div>
      <SField label="优先级">
        <SSelect v-model="form.priority" :options="PRIORITY_OPTIONS" />
      </SField>
      <SField label="标签" hint="多个标签用逗号分隔">
        <SInput v-model="form.tags" placeholder="如：工作, 学习" :maxlength="128" />
      </SField>
      <SField label="备注">
        <SInput v-model="form.description" placeholder="可选" :maxlength="256" />
      </SField>
      <p v-if="errorText" class="sch-form__error">{{ errorText }}</p>
    </div>
    <template #footer>
      <SButton variant="ghost" @click="emit('close')">取消</SButton>
      <SButton variant="gold" :loading="saving" @click="onSubmit">{{ schedule ? '保存' : '织入' }}</SButton>
    </template>
  </SModal>
</template>

<style scoped>
.sch-form { display: flex; flex-direction: column; gap: var(--sorts-space-4); }
.sch-form__row { display: grid; grid-template-columns: 1fr 120px; gap: var(--sorts-space-3); }
.sch-form__error { margin: 0; font-size: var(--sorts-text-sm); color: var(--sorts-color-late-600); }
</style>
