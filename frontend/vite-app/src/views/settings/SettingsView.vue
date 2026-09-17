<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { getPoints, updateMe } from '@/api/user'
import type { PointsVO } from '@/types'
import { useAuthStore } from '@/stores/auth'
import { useAppStore } from '@/stores/app'
import { ApiError } from '@/api/error'
import { formatDateTime } from '@/utils/datetime'
import SCard from '@/components/ui/SCard.vue'
import SField from '@/components/ui/SField.vue'
import SInput from '@/components/ui/SInput.vue'
import SButton from '@/components/ui/SButton.vue'
import SSwitch from '@/components/ui/SSwitch.vue'

/** 设置：资料编辑 + 光阴砂流水 + 外观（日梭/夜梭） */
const auth = useAuthStore()
const app = useAppStore()

const profile = reactive({ nickname: '', email: '', phone: '', avatarUrl: '' })
const saving = ref(false)
const points = ref<PointsVO | null>(null)

onMounted(async () => {
  if (auth.user) {
    profile.nickname = auth.user.nickname ?? ''
    profile.email = auth.user.email ?? ''
    profile.phone = auth.user.phone ?? ''
    profile.avatarUrl = auth.user.avatarUrl ?? ''
  }
  try {
    points.value = await getPoints(10)
  } catch {
    /* 流水降级隐藏 */
  }
})

async function saveProfile() {
  saving.value = true
  try {
    const updated = await updateMe({
      nickname: profile.nickname.trim() || undefined,
      email: profile.email.trim() || undefined,
      phone: profile.phone.trim() || undefined,
      avatarUrl: profile.avatarUrl.trim() || undefined
    })
    auth.applyUser(updated)
    app.toast('资料已保存', 'success')
  } catch (e) {
    app.toast(e instanceof ApiError ? e.message : '保存失败', 'error')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="settings">
    <SCard class="settings__panel">
      <h3 class="settings__title">织者资料</h3>
      <div class="settings__grid">
        <SField label="昵称">
          <SInput v-model="profile.nickname" :maxlength="32" />
        </SField>
        <SField label="邮箱">
          <SInput v-model="profile.email" type="email" :maxlength="64" />
        </SField>
        <SField label="手机号">
          <SInput v-model="profile.phone" :maxlength="20" />
        </SField>
        <SField label="头像 URL">
          <SInput v-model="profile.avatarUrl" :maxlength="256" />
        </SField>
      </div>
      <div class="settings__foot">
        <SButton variant="gold" size="sm" :loading="saving" @click="saveProfile">保存资料</SButton>
      </div>
    </SCard>

    <SCard class="settings__panel">
      <h3 class="settings__title">外观</h3>
      <div class="settings__theme">
        <span>夜梭（暗色）</span>
        <SSwitch :model-value="app.theme === 'night'" @update:model-value="app.toggleTheme()" />
      </div>
    </SCard>

    <SCard v-if="points" class="settings__panel">
      <h3 class="settings__title">光阴砂 <span class="settings__points num">{{ points.points }}</span></h3>
      <ul v-if="points.logs.length" class="settings__logs">
        <li v-for="log in points.logs" :key="log.id" class="settings__log num">
          <span class="settings__log-reason">{{ log.reason }}</span>
          <span class="settings__log-amount" :class="log.changeAmount >= 0 ? 'is-plus' : 'is-minus'">
            {{ log.changeAmount >= 0 ? '+' : '' }}{{ log.changeAmount }}
          </span>
          <span class="settings__log-time">{{ formatDateTime(log.createdAt) }}</span>
        </li>
      </ul>
      <p v-else class="settings__logs-empty">还没有流水</p>
    </SCard>
  </div>
</template>

<style scoped>
.settings { display: flex; flex-direction: column; gap: var(--sorts-space-4); max-width: 640px; }
.settings__panel { display: flex; flex-direction: column; gap: var(--sorts-space-4); }
.settings__title { margin: 0; font-size: var(--sorts-text-base); color: var(--sorts-ink); }
.settings__points { margin-left: 8px; color: var(--sorts-color-gold-700); }
.settings__grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: var(--sorts-space-4); }
.settings__foot { display: flex; justify-content: flex-end; }
.settings__theme { display: flex; align-items: center; justify-content: space-between; color: var(--sorts-text); font-size: var(--sorts-text-sm); }
.settings__logs { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 8px; }
.settings__log {
  display: grid;
  grid-template-columns: 1fr 70px 150px;
  gap: var(--sorts-space-2);
  font-size: var(--sorts-text-sm);
  align-items: center;
}
.settings__log-reason { color: var(--sorts-text); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.settings__log-amount.is-plus { color: var(--sorts-color-gold-700); text-align: right; }
.settings__log-amount.is-minus { color: var(--sorts-color-late-600); text-align: right; }
.settings__log-time { color: var(--sorts-text-faint); font-size: var(--sorts-text-xs); text-align: right; }
.settings__logs-empty { margin: 0; color: var(--sorts-text-faint); font-size: var(--sorts-text-sm); }
@media (max-width: 768px) {
  .settings__grid { grid-template-columns: 1fr; }
}
</style>
