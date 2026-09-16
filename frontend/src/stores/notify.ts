import { defineStore } from 'pinia'
import { listNotifications, markAllNotificationsRead, markNotificationRead } from '@/api/notification'
import type { NotificationQueryParams, NotificationVO } from '@/types'

/**
 * 通知：顶栏红点只依赖 unreadCount（服务端随分页返回，不受筛选影响）。
 * 列表本身由通知页维护，这里只缓存最近一页供下拉预览。
 */
export const useNotifyStore = defineStore('notify', {
  state: () => ({
    unreadCount: 0,
    recent: [] as NotificationVO[],
    loaded: false
  }),
  actions: {
    /** 拉第一页（顺带拿 unreadCount）；红点轮询也走这里 */
    async refresh(): Promise<void> {
      const page = await listNotifications({ page: 1, pageSize: 10 })
      this.unreadCount = page.unreadCount
      this.recent = page.list
      this.loaded = true
    },
    async markRead(id: number): Promise<void> {
      await markNotificationRead(id)
      const item = this.recent.find((n) => n.id === id)
      if (item && !item.isRead) {
        item.isRead = true
        this.unreadCount = Math.max(0, this.unreadCount - 1)
      }
    },
    async markAllRead(): Promise<number> {
      const count = await markAllNotificationsRead()
      this.unreadCount = 0
      this.recent.forEach((n) => (n.isRead = true))
      return count
    },
    /** 供通知页分页查询（透传，不缓存） */
    fetchPage(query: NotificationQueryParams) {
      return listNotifications(query)
    }
  }
})
