/* ============================================================
 * 契约类型：与 backend 各服务 VO/DTO 一一对齐
 * 真源：backend 各服务模块 src/main/java/com/sorts 下的 dto 包
 * 命名、可空性、枚举值均以 Java 定义为准（分页字段 list、成功码 0）。
 * ============================================================ */

/* ---------- 公共响应体（sorts-common/result） ---------- */

/** Result<T>：code=0 成功；timestamp 为毫秒 */
export interface ApiResult<T> {
  code: number
  message: string
  data: T
  timestamp: number
}

/** PageData<T>：注意分页字段是 list，不是 api-spec 里的 records */
export interface PageData<T> {
  list: T[]
  total: number
  page: number
  pageSize: number
}

/** 错误码（sorts-common/result/ErrorCode） */
export const ErrorCode = {
  SUCCESS: 0,
  PARAM_ERROR: 400,
  UNAUTHORIZED: 401,
  TOKEN_EXPIRED: 40101,
  TOKEN_INVALID: 40102,
  REFRESH_TOKEN_INVALID: 40103,
  FORBIDDEN: 403,
  NOT_FOUND: 404,
  METHOD_NOT_ALLOWED: 405,
  CONFLICT: 409,
  RATE_LIMITED: 429,
  SYSTEM_ERROR: 500,
  SERVICE_UNAVAILABLE: 503
} as const

/* ---------- 枚举（以后端枚举类为准） ---------- */

export type ScheduleStatus = 'PENDING' | 'IN_PROGRESS' | 'PAUSED' | 'COMPLETED' | 'CANCELLED' | 'TIMEOUT'
export type Priority = 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT'
export type NotificationType = 'REMINDER' | 'SUMMARY' | 'SYSTEM' | 'PROMOTION'
export type ReminderChannel = 'APP' | 'EMAIL' | 'SMS'
export type ItemType = 'SKIN' | 'AVATAR' | 'BADGE' | 'STICKER'
export type ItemStatus = 'ON_SHELF' | 'OFF_SHELF'
export type ReportType = 'DAILY' | 'WEEKLY' | 'MONTHLY' | 'YEARLY'
export type ReportStatus = 'GENERATING' | 'COMPLETED' | 'FAILED'
export type StatisticsPeriod = 'day' | 'week' | 'month' | 'year'
export type ScheduleView = 'day' | 'week' | 'month' | 'all'

/* ---------- 用户（sorts-user） ---------- */

export interface UserVO {
  id: number
  username: string
  nickname: string
  email: string | null
  phone: string | null
  avatarUrl: string | null
  points: number
  activeSkin: string | null
  activeAvatarFrame: string | null
  createdAt: string
}

export interface TokenVO {
  accessToken: string
  refreshToken: string
  /** 秒 */
  expiresIn: number
  user: UserVO
}

export interface LoginRequest { username: string; password: string }

export interface RegisterRequest {
  username: string
  password: string
  email?: string
  phone?: string
  nickname?: string
}

export interface UpdateUserRequest {
  nickname?: string
  email?: string
  phone?: string
  avatarUrl?: string
}

export interface PointsLogVO {
  id: number
  changeAmount: number
  balance: number
  reason: string
  relatedId: number | null
  createdAt: string
}

export interface PointsVO {
  points: number
  logs: PointsLogVO[]
}

/* ---------- 日程（sorts-schedule） ---------- */

export interface ScheduleVO {
  id: number
  userId: number
  title: string
  description: string | null
  /** ISO-8601 字符串，如 2026-09-16T09:00:00 */
  plannedStartTime: string
  /** 计划时长：分钟（全项目唯一用分钟的字段） */
  plannedDuration: number
  actualStartTime: string | null
  actualEndTime: string | null
  /** 实际时长：秒 */
  actualDuration: number | null
  status: ScheduleStatus
  priority: Priority
  tags: string[]
  color: string | null
  createdAt: string
  updatedAt: string
}

export interface ScheduleSaveRequest {
  title: string
  description?: string
  plannedStartTime: string
  plannedDuration?: number
  priority?: Priority
  tags?: string[]
  color?: string
}

export interface ScheduleQueryParams {
  date?: string
  view?: ScheduleView
  startDate?: string
  endDate?: string
  status?: ScheduleStatus
  priority?: Priority
  tag?: string
  keyword?: string
  page?: number
  pageSize?: number
  sort?: 'plannedStartTime' | 'createdAt' | 'priority'
  order?: 'asc' | 'desc'
}

export interface CalendarDayVO {
  date: string
  dayOfWeek: number
  isToday: boolean
  totalCount: number
  completedCount: number
  /** 秒 */
  focusTime: number
  schedules: ScheduleVO[]
}

export interface CalendarResponse {
  year: number
  month: number
  days: CalendarDayVO[]
  totalCount: number
  completedCount: number
  /** 秒 */
  focusTime: number
}

export interface WeekViewResponse {
  weekStart: string
  weekEnd: string
  days: CalendarDayVO[]
}

export interface TodayOverviewVO {
  date: string
  totalCount: number
  completedCount: number
  inProgressCount: number
  pendingCount: number
  /** 秒 */
  focusTime: number
  activeSchedule: ScheduleVO | null
  upcomingSchedules: ScheduleVO[]
}

export interface TrendPointVO {
  date: string
  total: number
  completed: number
  /** 秒 */
  totalDuration: number
  completionRate: number
}

export interface TagStatVO {
  tag: string
  count: number
  /** 秒 */
  totalDuration: number
  completedCount: number
  percentage: number
}

export interface StatisticsSummaryVO {
  period: string
  startDate: string
  endDate: string
  totalSchedules: number
  completedSchedules: number
  completionRate: number
  /** 秒 */
  totalFocusTime: number
  /** 秒（日均，只算已过去天数） */
  avgFocusTime: number
  tagDistribution: TagStatVO[]
  dailyTrend: TrendPointVO[]
}

/* ---------- AI（sorts-ai） ---------- */

export interface ChatRequest {
  message: string
  conversationId?: string
  /** 契约保留字段；传输方式实际由 query 参数决定 */
  stream?: boolean
  /** 双钥匙第二把：用户确认后置 true */
  allowWrite?: boolean
}

export interface SuggestedAction {
  type: string
  label: string
  payload: Record<string, unknown>
}

export interface ChatResponse {
  conversationId: string
  reply: string
  suggestedActions: SuggestedAction[] | null
}

export interface PlanPreferences {
  preferredStartHour?: number
  preferredEndHour?: number
  defaultDuration?: number
}

export interface AIPlanRequest {
  userPrompt: string
  /** yyyy-MM-dd；缺省服务端补 */
  targetDate?: string
  preferences?: PlanPreferences
}

export interface PlanSuggestion {
  title: string
  description: string | null
  /** HH:mm，日期由服务端补 */
  suggestedStart: string
  /** 分钟 */
  duration: number
  priority: Priority
  tags: string[]
  reason: string | null
}

export interface AIPlanResponse {
  planId: string
  suggestions: PlanSuggestion[]
  adopted: boolean
  createdScheduleIds: number[] | null
}

export interface PlanAdjustments {
  startOffset?: number
  date?: string
}

export interface AdoptPlanRequest {
  planId: string
  selectedIndices?: number[]
  adjustments?: PlanAdjustments
}

export interface AISummaryRequest {
  /** yyyy-MM-dd；缺省今天 */
  date?: string
}

export interface PeriodSummaryRequest {
  year?: number
  month?: number
}

export interface AsyncReportResponse {
  reportId: number
  status: ReportStatus | string
  estimatedSeconds: number | null
}

export interface AIReportInfo {
  id: number
  userId: number
  type: ReportType | string
  title: string
  content: string
  completionRate: number | null
  /** 秒 */
  totalFocusTime: number | null
  highlights: string[] | null
  suggestions: string[] | null
  generatedAt: string
  status: ReportStatus | string
}

/** SSE 事件载荷（sorts-ai/support/SseStream） */
export interface SseDeltaPayload { content: string }
export interface SseErrorPayload { code: number; message: string }

/* ---------- 通知（sorts-notification） ---------- */

export interface NotificationVO {
  id: number
  userId: number
  type: NotificationType | string
  title: string
  content: string
  isRead: boolean
  relatedId: number | null
  createdAt: string
}

export interface NotificationPageVO {
  list: NotificationVO[]
  total: number
  page: number
  pageSize: number
  /** 未读总数：不受筛选条件影响 */
  unreadCount: number
}

export interface NotificationQueryParams {
  type?: NotificationType | string
  isRead?: boolean
  page?: number
  pageSize?: number
}

export interface ReminderSettingVO {
  defaultAdvanceMinutes: number
  channels: ReminderChannel[] | string[]
  quietHoursEnabled: boolean
  /** HH:mm */
  quietStart: string | null
  quietEnd: string | null
  /** 用户从未改过时为 false（返回的是服务端默认值） */
  customized: boolean
}

export interface ReminderSettingRequest {
  defaultAdvanceMinutes?: number
  channels?: ReminderChannel[] | string[]
  quietHoursEnabled?: boolean
  quietStart?: string
  quietEnd?: string
}

/* ---------- 商城（sorts-mall） ---------- */

export interface MallItemVO {
  id: number
  name: string
  type: ItemType | string
  description: string | null
  imageUrl: string | null
  previewUrl: string | null
  price: number
  /** -1 表示无限库存 */
  stock: number
  status: ItemStatus | string
  createdAt: string
}

export interface MallItemPageVO {
  list: MallItemVO[]
  total: number
  page: number
  pageSize: number
  /** 用户服务不可用时为 null（降级） */
  userPoints: number | null
}

export interface MallItemDetailVO extends MallItemVO {
  owned: boolean
  purchaseCount: number
}

export interface PurchaseRequest { itemId: number }

export interface PurchaseResultVO {
  purchaseId: number
  item: MallItemVO
  remainingPoints: number
}

export interface WardrobeItemVO {
  id: number
  userId: number
  item: MallItemVO
  isActive: boolean
  purchasedAt: string
}

export interface ActivateRequest {
  itemId: number
  type: ItemType | string
}
