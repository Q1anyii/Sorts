<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { getReminderSettings, updateReminderSettings } from '@/api/notification'
import type { NotificationPageVO, ReminderSettingVO } from '@/types'
import { useNotifyStore } from '@/stores/notify'
import { useAppStore } from '@/stores/app'
import { ApiError } from '@/api/error'
import { NOTIFICATION_TYPE_META } from '@/utils/status'
import { formatDateTime } from '@/utils/datetime'
import SButton from '@/components/ui/SButton.vue'
import SSelect from '@/components/ui/SSelect.vue'
import SCard from '@/components/ui/SCard.vue'
import SField from '@/components/ui/SField.vue'
import SInput from '@/components/ui/SInput.vue'
import SSwitch from '@/components/ui/SSwitch.vue'
import SSkeleton from '@/components/ui/SSkeleton.vue'
import SEmpty from '@/components/ui/SEmpty.vue'
import STag from '@/components/ui/STag.vue'

/** 飞鸽传书：通知列表（筛选/已读）+ 提醒设置 */
const notify = useNotifyStore()
const app = useAppStore()

const TYPE_OPTIONS = [
  { value: '', label: '全部类型' },
  { value: 'REMINDER', label: '提醒' },
  { value: 'SUMMARY', label: '总结' },
  { value: 'SYSTEM', label: '系统' },
  { value: 'PROMOTION', label: '锦市' }
]
const READ_OPTIONS = [
  { value: '', label: '全部' },
  { value: 'false', label: '未读' },
  { value: 'true', label: '已读' }
]

const typeFilter = ref('')
const readFilter = ref('')
const page = ref(1)
const data = ref<NotificationPageVO | null>(null)
const loading = ref(true)
const busyId = ref(0)

const settings = ref<ReminderSettingVO | null>(null)
const settingsSaving = ref(false)

async function load() {
  loading.value = true
  try {
    data.value = await notify.fetchPage({
      type: typeFilter.value || undefined,
      isRead: readFilter.value === '' ? undefined : readFilter.value === 'true',
      page: page.value,
      pageSize: 15
    })
    notify.unreadCount = data.value.unreadCount
  } catch (e) {
    app.toast(e instanceof ApiError ? e.message : '加载失败', 'error')
  } finally {
    loading.value = false
  }
}

async function markRead(id: number, isRead: boolean) {
  if (isRead) return
  busyId.value = id
  try {
    await notify.markRead(id)
    const item = data.value?.list.find((n) => n.id === id)
    if (item) item.isRead = true
    if (data.value) data.value.unreadCount = notify.unreadCount
  } catch (e) {
    app.toast(e instanceof ApiError ? e.message : '操作失败', 'error')
  } finally {
    busyId.value = 0
  }
}

async function markAll() {
  try {
    const n = await notify.markAllRead()
    app.toast(`已读 ${n} 封`, 'success')
    await load()
  } catch (e) {
    app.toast(e instanceof ApiError ? e.message : '操作失败', 'error')
  }
}

async function loadSettings() {
  try {
    settings.value = await getReminderSettings()
  } catch {
    /* 设置区降级隐藏 */
  }
}

async function saveSettings() {
  if (!settings.value) return
  settingsSaving.value = true
  try {
    settings.value = await updateReminderSettings({
      defaultAdvanceMinutes: settings.value.defaultAdvanceMinutes,
      quietHoursEnabled: settings.value.quietHoursEnabled,
      quietStart: settings.value.quietStart ?? undefined,
      quietEnd: settings.value.quietEnd ?? undefined
    })
    app.toast('提醒设置已保存', 'success')
  } catch (e) {
    app.toast(e instanceof ApiError ? e.message : '保存失败', 'error')
  } finally {
    settingsSaving.value = false
  }
}

onMounted(() => {
  load()
  loadSettings()
})
</script>

<template>
  <div class="notify">
    <!-- 筛选与操作 -->
    <div class="notify__bar">
      <SSelect v-model="typeFilter" :options="TYPE_OPTIONS" class="notify__select" @update:model-value="page = 1; load()" />
      <SSelect v-model="readFilter" :options="READ_OPTIONS" class="notify__select" @update:model-value="page = 1; load()" />
      <span class="notify__unread num">未读 {{ data?.unreadCount ?? notify.unreadCount }}</span>
      <SButton variant="ghost" size="sm" :disabled="!(data?.unreadCount ?? 0)" @click="markAll">全部已读</SButton>
    </div>

    <SSkeleton v-if="loading" :lines="5" />
    <SEmpty v-else-if="!data || !data.list.length" text="没有飞鸽来信" icon="bell" />

    <div v-else class="notify__list">
      <button
        v-for="n in data.list"
        :key="n.id"
        class="notify__item weave-in"
        :class="{ 'is-unread': !n.isRead }"
        :disabled="busyId === n.id"
        @click="markRead(n.id, n.isRead)"
      >
        <span class="notify__item-dot" />
        <div class="notify__item-main">
          <div class="notify__item-head">
            <span class="notify__item-title">{{ n.title }}</span>
            <STag>{{ NOTIFICATION_TYPE_META[n.type] ?? n.type }}</STag>
          </div>
          <p class="notify__item-content">{{ n.content }}</p>
          <p class="notify__item-time num">{{ formatDateTime(n.createdAt) }}</p>
        </div>
      </button>
      <div class="notify__pager num">
        <SButton variant="ghost" size="sm" :disabled="page <= 1" @click="page -= 1; load()">上一页</SButton>
        <span>第 {{ page }} 页 · 共 {{ data.total }} 封</span>
        <SButton variant="ghost" size="sm" :disabled="data.list.length < 15" @click="page += 1; load()">下一页</SButton>
      </div>
    </div>

    <!-- 提醒设置 -->
    <SCard v-if="settings" class="notify__settings">
      <h3 class="notify__settings-title">提醒设置</h3>
      <div class="notify__settings-grid">
        <SField label="默认提前（分钟）">
          <SInput
            :model-value="String(settings.defaultAdvanceMinutes)"
            type="number"
            @update:model-value="settings!.defaultAdvanceMinutes = Number($event) || 0"
          />
        </SField>
        <SField label="免打扰">
          <span class="notify__quiet">
            <SSwitch v-model="settings.quietHoursEnabled" />
            <span class="notify__quiet-label">{{ settings.quietHoursEnabled ? '已开启' : '已关闭' }}</span>
          </span>
        </SField>
        <template v-if="settings.quietHoursEnabled">
          <SField label="免打扰开始">
            <SInput :model-value="settings.quietStart ?? ''" type="time" @update:model-value="settings!.quietStart = $event" />
          </SField>
          <SField label="免打扰结束">
            <SInput :model-value="settings.quietEnd ?? ''" type="time" @update:model-value="settings!.quietEnd = $event" />
          </SField>
        </template>
      </div>
      <div class="notify__settings-foot">
        <SButton variant="gold" size="sm" :loading="settingsSaving" @click="saveSettings">保存设置</SButton>
      </div>
    </SCard>
  </div>
</template>

<style scoped>
.notify { display: flex; flex-direction: column; gap: var(--sorts-space-4); max-width: 760px; }
.notify__bar { display: flex; align-items: center; gap: var(--sorts-space-2); flex-wrap: wrap; }
.notify__select { width: 130px; }
.notify__unread { margin-left: auto; font-size: var(--sorts-text-sm); color: var(--sorts-color-late-600); }
.notify__list { display: flex; flex-direction: column; gap: var(--sorts-space-2); }
.notify__item {
  display: flex;
  gap: var(--sorts-space-3);
  padding: 12px var(--sorts-space-4);
  background: var(--sorts-surface);
  border: 1px solid var(--sorts-border);
  border-radius: var(--sorts-radius-ctl);
  cursor: pointer;
  text-align: left;
}
.notify__item-dot {
  width: 8px;
  height: 8px;
  margin-top: 6px;
  border-radius: 50%;
  background: transparent;
  flex: none;
}
.notify__item.is-unread .notify__item-dot { background: var(--sorts-color-late-600); }
.notify__item-main { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 4px; }
.notify__item-head { display: flex; align-items: center; gap: 8px; }
.notify__item-title { color: var(--sorts-ink); font-size: var(--sorts-text-base); }
.notify__item.is-unread .notify__item-title { font-weight: 600; }
.notify__item-content { margin: 0; font-size: var(--sorts-text-sm); color: var(--sorts-text-faint); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.notify__item-time { margin: 0; font-size: var(--sorts-text-xs); color: var(--sorts-text-faint); }
.notify__pager { display: flex; align-items: center; justify-content: center; gap: var(--sorts-space-3); color: var(--sorts-text-faint); font-size: var(--sorts-text-sm); }
.notify__settings { display: flex; flex-direction: column; gap: var(--sorts-space-4); }
.notify__settings-title { margin: 0; font-size: var(--sorts-text-base); color: var(--sorts-ink); }
.notify__settings-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: var(--sorts-space-4); }
.notify__quiet { display: inline-flex; align-items: center; gap: 8px; }
.notify__quiet-label { font-size: var(--sorts-text-sm); color: var(--sorts-text-faint); }
.notify__settings-foot { display: flex; justify-content: flex-end; }
@media (max-width: 768px) {
  .notify__settings-grid { grid-template-columns: 1fr; }
}
</style>
