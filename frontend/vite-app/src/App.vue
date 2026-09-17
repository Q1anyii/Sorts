<script setup lang="ts">
import { onMounted } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useAppStore } from '@/stores/app'
import { useTimerStore } from '@/stores/timer'

const auth = useAuthStore()
const app = useAppStore()
const timer = useTimerStore()

onMounted(async () => {
  app.initTheme()
  if (auth.isAuthenticated) {
    try {
      // 会话恢复与计时恢复并行；401 续期由 http 拦截器兜底
      await Promise.all([auth.fetchMe(), timer.recover()])
    } catch {
      /* 拦截器已处理跳转，这里静默 */
    }
  }
})
</script>

<template>
  <!-- 页面出口：登录后的穿梭过场在 AppLayout 内部完成 -->
  <router-view />

  <!-- 全局轻提示 -->
  <div class="toast-stack" aria-live="polite">
    <transition-group name="shuttle">
      <div v-for="t in app.toasts" :key="t.id" class="toast" :class="`toast--${t.kind}`">
        {{ t.text }}
      </div>
    </transition-group>
  </div>
</template>

<style scoped>
.toast-stack {
  position: fixed;
  top: var(--sorts-space-4, 16px);
  right: var(--sorts-space-4, 16px);
  z-index: 1000;
  display: flex;
  flex-direction: column;
  gap: var(--sorts-space-2, 8px);
  pointer-events: none;
}
.toast {
  padding: var(--sorts-space-2, 8px) var(--sorts-space-4, 16px);
  border-radius: var(--sorts-radius-ctl, 6px);
  background: var(--sorts-ink);
  color: var(--sorts-paper, #fff);
  font-size: var(--sorts-text-sm, 14px);
  box-shadow: var(--sorts-shadow-2, 0 4px 12px rgb(0 0 0 / 0.12));
}
.toast--success { background: var(--sorts-color-arrow-600, var(--sorts-ink)); }
.toast--error { background: var(--sorts-color-late-600, var(--sorts-ink)); }
</style>
