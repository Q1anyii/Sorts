import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import { tokenStore } from '@/api/token'

/**
 * 路由总表。
 * - meta.public：免登录白名单（登录/注册）
 * - meta.title：页签标题 + 顶栏标题
 * - 登录后的页面挂在 AppLayout 下（织机栏 + 梭行条），过渡动画在布局内部完成
 * - 组件全部懒加载；尚未实现的模块先指向 StubView（随 #34~#36 逐个替换为真实页面）
 */
const stub = () => import('@/views/StubView.vue')

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: stub,
    meta: { public: true, title: '入梭 · 登录' }
  },
  {
    path: '/',
    component: () => import('@/layouts/AppLayout.vue'),
    children: [
      { path: '', redirect: '/today' },
      { path: 'today', name: 'today', component: stub, meta: { title: '今日经纬' } },
      { path: 'calendar', name: 'calendar', component: stub, meta: { title: '织历' } },
      { path: 'schedules', name: 'schedules', component: stub, meta: { title: '日程清单' } },
      { path: 'timer', name: 'timer', component: stub, meta: { title: '穿梭计时' } },
      { path: 'statistics', name: 'statistics', component: stub, meta: { title: '纹谱统计' } },
      { path: 'ai', name: 'ai', component: stub, meta: { title: 'AI 织师' } },
      { path: 'mall', name: 'mall', component: stub, meta: { title: '锦市' } },
      { path: 'wardrobe', name: 'wardrobe', component: stub, meta: { title: '衣橱' } },
      { path: 'notifications', name: 'notifications', component: stub, meta: { title: '飞鸽传书' } },
      { path: 'settings', name: 'settings', component: stub, meta: { title: '设置' } }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: stub,
    meta: { title: '迷失的梭子' }
  }
]

export const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  const authed = !!tokenStore.access
  if (!to.meta.public && !authed) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.name === 'login' && authed) {
    return { path: '/' }
  }
  return true
})

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} · 梭子 SORTS` : '梭子 SORTS'
})
