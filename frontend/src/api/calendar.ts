import { get } from './http'
import { CalendarResponse, TodayOverviewVO, WeekViewResponse } from '@/types'

/** 月历；year / month 缺省为当月（服务端兜底） */
export function getCalendarMonth(year?: number, month?: number): Promise<CalendarResponse> {
  return get<CalendarResponse>('/calendar', { year, month })
}

/** 周视图（周一 → 周日）；date 缺省今天 */
export function getCalendarWeek(date?: string): Promise<WeekViewResponse> {
  return get<WeekViewResponse>('/calendar/week', { date })
}

export function getCalendarToday(): Promise<TodayOverviewVO> {
  return get<TodayOverviewVO>('/calendar/today')
}
