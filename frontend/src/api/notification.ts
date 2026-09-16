import { get, put } from './http'
import {
  NotificationPageVO,
  NotificationQueryParams,
  ReminderSettingRequest,
  ReminderSettingVO
} from '@/types'

export function listNotifications(query: NotificationQueryParams = {}): Promise<NotificationPageVO> {
  const params: Record<string, unknown> = {}
  if (query.type) params.type = query.type
  if (query.isRead !== undefined) params.isRead = query.isRead
  params.page = query.page ?? 1
  params.pageSize = query.pageSize ?? 20
  return get<NotificationPageVO>('/notifications', params)
}

/** 标记单条已读（幂等，重复标记不报错） */
export function markNotificationRead(id: number): Promise<void> {
  return put<void>(`/notifications/${id}/read`)
}

/** 全部已读；返回实际标记条数 */
export function markAllNotificationsRead(): Promise<number> {
  return put<number>('/notifications/read-all')
}

export function getReminderSettings(): Promise<ReminderSettingVO> {
  return get<ReminderSettingVO>('/notifications/settings')
}

/** 仅覆盖传入的非空字段（服务端语义） */
export function updateReminderSettings(data: ReminderSettingRequest): Promise<ReminderSettingVO> {
  return put<ReminderSettingVO>('/notifications/settings', data)
}
