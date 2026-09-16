import type { ItemType, NotificationType, Priority, ReportType, ScheduleStatus } from '@/types'

/* 状态语义映射：与 docs/theme-design.md §6.3 / §9.2 一一对应 */

export type SealForm = 'solid-gold' | 'outline-arrow' | 'solid-late' | null

export interface StatusMeta {
  /** 中文标签（品牌文案：穿梭中/落梭/逾期） */
  label: string
  /** 印章形态；null 表示不设印（§9.3） */
  seal: SealForm
  /** 状态徽记文案（与品牌文案一致） */
  sealText: string | null
  /** 列表项左侧 3px 状态条使用的 token 变量 */
  barColor: string
}

export const SCHEDULE_STATUS_META: Record<ScheduleStatus, StatusMeta> = {
  PENDING: {
    label: '待开始',
    seal: null,
    sealText: null,
    barColor: 'var(--sorts-status-pending-fg)'
  },
  IN_PROGRESS: {
    label: '穿梭中',
    seal: 'solid-gold',
    sealText: '穿梭中',
    barColor: 'var(--sorts-color-gold-500)'
  },
  PAUSED: {
    label: '已暂停',
    seal: null,
    sealText: null,
    barColor: 'var(--sorts-color-gold-300)'
  },
  COMPLETED: {
    label: '已落梭',
    seal: 'outline-arrow',
    sealText: '已落梭',
    barColor: 'var(--sorts-color-arrow-500)'
  },
  CANCELLED: {
    label: '已取消',
    seal: null,
    sealText: null,
    barColor: 'var(--sorts-color-line-500)'
  },
  TIMEOUT: {
    label: '逾期',
    seal: 'solid-late',
    sealText: '逾期',
    barColor: 'var(--sorts-color-late-500)'
  }
}

export function statusMeta(status: ScheduleStatus | string): StatusMeta {
  return SCHEDULE_STATUS_META[status as ScheduleStatus] ?? SCHEDULE_STATUS_META.PENDING
}

/** 状态机允许的下一步操作（与 TimerService 非法流转 409 对齐） */
export type TimerAction = 'start' | 'pause' | 'resume' | 'end' | 'cancel'

export function allowedActions(status: ScheduleStatus | string): TimerAction[] {
  switch (status) {
    case 'PENDING':
      return ['start', 'cancel']
    case 'IN_PROGRESS':
      return ['pause', 'end', 'cancel']
    case 'PAUSED':
      return ['resume', 'end', 'cancel']
    default:
      return []
  }
}

export const PRIORITY_META: Record<Priority, { label: string; color: string }> = {
  URGENT: { label: '紧急', color: 'var(--sorts-priority-urgent)' },
  HIGH: { label: '高', color: 'var(--sorts-priority-high)' },
  MEDIUM: { label: '中', color: 'var(--sorts-priority-medium)' },
  LOW: { label: '低', color: 'var(--sorts-priority-low)' }
}

export const NOTIFICATION_TYPE_META: Record<NotificationType, string> = {
  REMINDER: '提醒',
  SUMMARY: '总结',
  SYSTEM: '系统',
  PROMOTION: '锦市'
}

export const ITEM_TYPE_META: Record<ItemType, string> = {
  SKIN: '皮肤',
  AVATAR: '头像',
  BADGE: '徽章',
  STICKER: '贴纸'
}

export const REPORT_TYPE_META: Record<string, string> = {
  DAILY: '今日梭影',
  WEEKLY: '本周梭影',
  MONTHLY: '月度梭影',
  YEARLY: '年度织锦'
}

/** 商品类型的预览底色（锦缎纹样底的基调：用低档色而非随意渐变） */
export function itemTypeTone(type: ItemType | string): { bg: string; fg: string } {
  switch (type) {
    case 'SKIN':
      return { bg: 'var(--sorts-color-gold-100)', fg: 'var(--sorts-color-gold-700)' }
    case 'AVATAR':
      return { bg: 'var(--sorts-color-arrow-100)', fg: 'var(--sorts-color-arrow-700)' }
    case 'BADGE':
      return { bg: 'var(--sorts-color-late-100)', fg: 'var(--sorts-color-late-700)' }
    default:
      return { bg: 'var(--sorts-color-line-100)', fg: 'var(--sorts-color-line-800)' }
  }
}

/** 无限库存（stock = -1）展示文案 */
export function stockLabel(stock: number): string {
  if (stock < 0) return '不限量'
  if (stock === 0) return '已售罄'
  return `仅剩 ${stock} 件`
}
