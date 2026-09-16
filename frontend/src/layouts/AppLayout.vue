<script setup lang="ts">
import { onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useAppStore } from '@/stores/app'
import { useNotifyStore } from '@/stores/notify'
import SIcon from '@/components/ui/SIcon.vue'
import SBadge from '@/components/ui/SBadge.vue'

/** 应用外壳：织机栏（侧导航）+ 梭行条（顶栏）+ 移动端底部导航 */
const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const app = useAppStore()
const notify = useNotifyStore()

const NAV = [
  { name: 'today', label: '今日经纬', icon: 'home' },
  { name: 'calendar', label: '织历', icon: 'calendar' },
  { name: 'schedules', label: '日程清单', icon: 'list' },
  { name: 'timer', label: '穿梭计时', icon: 'timer' },
  { name: 'statistics', label: '纹谱统计', icon: 'chart' },
  { name: 'ai', label: 'AI 织师', icon: 'ai' },
  { name: 'mall', label: '锦市', icon: 'shop' },
  { name: 'wardrobe', label: '衣橱', icon: 'wardrobe' },
  { name: 'settings', label: '设置', icon: 'gear' }
] as const

/** 移动端底部导航（5 个高频入口） */
const MOBILE_NAV = ['today', 'calendar', 'timer', 'ai', 'settings'] as const
const mobileItems = NAV.filter((n) => (MOBILE_NAV as readonly string[]).includes(n.name))

let pollTimer = 0

onMounted(async () => {
  // 未读红点：进应用拉一次，之后每分钟轮询
  try {
    await notify.refresh()
  } catch {
    /* 静默：通知不可用不阻塞主流程 */
  }
  pollTimer = window.setInterval(() => notify.refresh().catch(() => {}), 60_000)
})

onUnmounted(() => {
  if (pollTimer) window.clearInterval(pollTimer)
})

async function onLogout() {
  await auth.logout()
  router.push({ name: 'login' })
}
</script>

<template>
  <div class="shell">
    <!-- 织机栏 -->
    <aside class="shell__sidebar">
      <router-link to="/today" class="shell__brand">
        <span class="shell__brand-seal">梭</span>
        <span class="shell__brand-name">梭子 SORTS</span>
      </router-link>
      <nav class="shell__nav">
        <router-link
          v-for="item in NAV"
          :key="item.name"
          :to="{ name: item.name }"
          class="shell__nav-item"
          :class="{ 'is-active': route.name === item.name }"
        >
          <SIcon :name="item.icon" :size="18" />
          <span>{{ item.label }}</span>
        </router-link>
      </nav>
    </aside>

    <div class="shell__main">
      <!-- 梭行条 -->
      <header class="shell__topbar">
        <h1 class="shell__title">{{ route.meta.title }}</h1>
        <div class="shell__actions">
          <button class="shell__icon-btn" :aria-label="app.theme === 'day' ? '切换夜梭' : '切换日梭'" @click="app.toggleTheme()">
            <SIcon :name="app.theme === 'day' ? 'moon' : 'sun'" :size="18" />
          </button>
          <router-link to="/notifications" class="shell__icon-btn shell__bell" aria-label="通知">
            <SIcon name="bell" :size="18" />
            <SBadge :count="notify.unreadCount" class="shell__bell-badge" />
          </router-link>
          <span class="shell__user">
            <SIcon name="user" :size="16" />
            {{ auth.nickname || '织者' }}
          </span>
          <button class="shell__icon-btn" aria-label="退出登录" @click="onLogout">
            <SIcon name="logout" :size="18" />
          </button>
        </div>
      </header>

      <!-- 页面区：穿梭过场（out-in 防闪烁） -->
      <main class="shell__content">
        <router-view v-slot="{ Component, route: childRoute }">
          <transition name="shuttle" mode="out-in">
            <component :is="Component" :key="childRoute.path" />
          </transition>
        </router-view>
      </main>
    </div>

    <!-- 移动端底部导航 -->
    <nav class="shell__bottom">
      <router-link
        v-for="item in mobileItems"
        :key="item.name"
        :to="{ name: item.name }"
        class="shell__bottom-item"
        :class="{ 'is-active': route.name === item.name }"
      >
        <SIcon :name="item.icon" :size="20" />
        <span>{{ item.label.slice(0, 2) }}</span>
      </router-link>
    </nav>
  </div>
</template>

<style scoped>
.shell {
  display: flex;
  min-height: 100vh;
  background: var(--sorts-bg);
}

/* ---------- 织机栏 ---------- */
.shell__sidebar {
  width: var(--sorts-sidebar-w);
  flex: none;
  display: flex;
  flex-direction: column;
  border-right: 1px solid var(--sorts-border);
  background: var(--sorts-surface);
  position: sticky;
  top: 0;
  height: 100vh;
}
.shell__brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: var(--sorts-space-4);
  text-decoration: none;
  border-bottom: 1px solid var(--sorts-border);
}
.shell__brand-seal {
  width: 32px;
  height: 32px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: var(--sorts-color-late-600);
  color: #fff;
  border-radius: var(--sorts-radius-seal);
  font-family: var(--sorts-font-serif);
  font-size: var(--sorts-text-lg);
  font-weight: 600;
}
.shell__brand-name {
  font-family: var(--sorts-font-serif);
  font-size: var(--sorts-text-lg);
  color: var(--sorts-ink);
  font-weight: 600;
}
.shell__nav {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: var(--sorts-space-2);
  overflow-y: auto;
}
.shell__nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 12px;
  border-radius: var(--sorts-radius-ctl);
  color: var(--sorts-text-faint);
  text-decoration: none;
  font-size: var(--sorts-text-sm);
  transition: background-color var(--sorts-dur-fast) var(--sorts-ease-out),
    color var(--sorts-dur-fast) var(--sorts-ease-out);
}
.shell__nav-item:hover { background: var(--sorts-color-line-100); color: var(--sorts-text); }
.shell__nav-item.is-active {
  background: var(--sorts-color-arrow-100);
  color: var(--sorts-color-arrow-700);
  font-weight: 500;
}

/* ---------- 梭行条 ---------- */
.shell__main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}
.shell__topbar {
  height: var(--sorts-topbar-h);
  flex: none;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 var(--sorts-space-4);
  border-bottom: 1px solid var(--sorts-border);
  background: var(--sorts-surface);
  position: sticky;
  top: 0;
  z-index: 100;
}
.shell__title { font-size: var(--sorts-text-lg); color: var(--sorts-ink); }
.shell__actions { display: flex; align-items: center; gap: var(--sorts-space-2); }
.shell__icon-btn {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border: none;
  border-radius: var(--sorts-radius-ctl);
  background: transparent;
  color: var(--sorts-text-faint);
  cursor: pointer;
  transition: background-color var(--sorts-dur-fast) var(--sorts-ease-out),
    color var(--sorts-dur-fast) var(--sorts-ease-out);
}
.shell__icon-btn:hover { background: var(--sorts-color-line-100); color: var(--sorts-text); }
.shell__bell-badge { position: absolute; top: 2px; right: 2px; }
.shell__user {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--sorts-text);
  font-size: var(--sorts-text-sm);
  padding: 0 6px;
}

/* ---------- 页面区 ---------- */
.shell__content {
  flex: 1;
  padding: var(--sorts-space-4);
  min-width: 0;
}

/* ---------- 移动端底部导航 ---------- */
.shell__bottom { display: none; }

@media (max-width: 768px) {
  .shell__sidebar { display: none; }
  .shell__content { padding-bottom: 72px; }
  .shell__bottom {
    display: flex;
    position: fixed;
    left: 0;
    right: 0;
    bottom: 0;
    z-index: 200;
    background: var(--sorts-surface);
    border-top: 1px solid var(--sorts-border);
  }
  .shell__bottom-item {
    flex: 1;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 2px;
    padding: 8px 0 6px;
    color: var(--sorts-text-faint);
    text-decoration: none;
    font-size: 11px;
  }
  .shell__bottom-item.is-active { color: var(--sorts-color-arrow-700); }
  .shell__user { display: none; }
}
</style>
