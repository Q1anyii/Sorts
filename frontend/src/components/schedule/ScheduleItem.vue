<script setup lang="ts">
import { computed } from 'vue'
import type { ScheduleVO } from '@/types'
import { allowedActions, PRIORITY_META, statusMeta } from '@/utils/status'
import type { TimerAction } from '@/utils/status'
import { formatDuration, formatTime } from '@/utils/datetime'
import SSeal from '@/components/ui/SSeal.vue'
import STag from '@/components/ui/STag.vue'
import SIcon from '@/components/ui/SIcon.vue'

/** 日程条目：左 3px 状态条 + 标题/时间/标签 + 印章 + 状态机动作按钮（§6.3/§九） */
const props = withDefaults(defineProps<{ schedule: ScheduleVO; busy?: boolean; manage?: boolean }>(), {
  manage: true
})

const emit = defineEmits<{
  action: [id: number, action: TimerAction]
  edit: [schedule: ScheduleVO]
  remove: [schedule: ScheduleVO]
}>()

const meta = computed(() => statusMeta(props.schedule.status))
const actions = computed(() => allowedActions(props.schedule.status))

const ACTION_META: Record<TimerAction, { label: string; icon: string }> = {
  start: { label: '开梭', icon: 'play' },
  pause: { label: '暂停', icon: 'pause' },
  resume: { label: '续梭', icon: 'play' },
  end: { label: '落梭', icon: 'check' },
  cancel: { label: '取消', icon: 'close' }
}

const timeLabel = computed(() => {
  const s = props.schedule
  const planned = `${formatTime(s.plannedStartTime)} · ${s.plannedDuration} 分钟`
  if (s.status === 'COMPLETED' && s.actualDuration != null) {
    return `${planned}｜实织 ${formatDuration(s.actualDuration)}`
  }
  return planned
})
</script>

<template>
  <article class="sch-item" :style="{ borderLeftColor: meta.barColor }">
    <div class="sch-item__main">
      <div class="sch-item__head">
        <h4 class="sch-item__title" :class="{ 'is-done': schedule.status === 'COMPLETED' }">
          {{ schedule.title }}
        </h4>
        <STag :color="PRIORITY_META[schedule.priority].color">{{ PRIORITY_META[schedule.priority].label }}</STag>
      </div>
      <p class="sch-item__time num">{{ timeLabel }}</p>
      <div v-if="schedule.tags.length" class="sch-item__tags">
        <STag v-for="tag in schedule.tags" :key="tag">#{{ tag }}</STag>
      </div>
    </div>

    <SSeal v-if="meta.seal && meta.sealText" :form="meta.seal" :text="meta.sealText" :breath="schedule.status === 'IN_PROGRESS'" :size="40" />

    <div class="sch-item__actions">
      <button
        v-for="a in actions"
        :key="a"
        class="sch-item__action"
        :disabled="busy"
        :title="ACTION_META[a].label"
        @click="emit('action', schedule.id, a)"
      >
        <SIcon :name="ACTION_META[a].icon" :size="15" />
        <span>{{ ACTION_META[a].label }}</span>
      </button>
      <button class="sch-item__action sch-item__action--mute" :disabled="busy" title="编辑" @click="emit('edit', schedule)">
        <SIcon name="gear" :size="15" />
      </button>
      <button class="sch-item__action sch-item__action--mute" :disabled="busy" title="删除" @click="emit('remove', schedule)">
        <SIcon name="close" :size="15" />
      </button>
    </div>
  </article>
</template>

<style scoped>
.sch-item {
  display: flex;
  align-items: center;
  gap: var(--sorts-space-3);
  padding: 12px var(--sorts-space-4);
  background: var(--sorts-surface);
  border: 1px solid var(--sorts-border);
  border-left: 3px solid var(--sorts-border);
  border-radius: var(--sorts-radius-ctl);
}
.sch-item__main { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 4px; }
.sch-item__head { display: flex; align-items: center; gap: 8px; }
.sch-item__title {
  font-size: var(--sorts-text-base);
  color: var(--sorts-ink);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.sch-item__title.is-done { text-decoration: line-through; color: var(--sorts-text-faint); }
.sch-item__time { font-size: var(--sorts-text-xs); color: var(--sorts-text-faint); }
.sch-item__tags { display: flex; gap: 6px; flex-wrap: wrap; }
.sch-item__actions { display: flex; align-items: center; gap: 4px; flex: none; }
.sch-item__action {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border: 1px solid var(--sorts-border);
  background: transparent;
  color: var(--sorts-color-arrow-700);
  font-size: var(--sorts-text-xs);
  padding: 4px 8px;
  border-radius: var(--sorts-radius-ctl);
  cursor: pointer;
  transition: background-color var(--sorts-dur-fast) var(--sorts-ease-out);
}
.sch-item__action:hover:not(:disabled) { background: var(--sorts-color-arrow-100); }
.sch-item__action:disabled { opacity: .5; cursor: not-allowed; }
.sch-item__action--mute { color: var(--sorts-text-faint); padding: 4px 6px; }
.sch-item__action--mute:hover:not(:disabled) { background: var(--sorts-color-line-100); }
</style>
