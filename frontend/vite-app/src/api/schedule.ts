import { del, get, post, put } from './http'
import { PageData, ScheduleQueryParams, ScheduleSaveRequest, ScheduleVO } from '@/types'

/** 清理查询参数：undefined / 空串不上送（view 缺省 all、page 缺省 1 由服务端兜底） */
function cleanParams(params: ScheduleQueryParams): Record<string, unknown> {
  const out: Record<string, unknown> = {}
  for (const [key, value] of Object.entries(params)) {
    if (value === undefined || value === null || value === '') continue
    out[key] = value
  }
  return out
}

export function createSchedule(data: ScheduleSaveRequest): Promise<ScheduleVO> {
  return post<ScheduleVO>('/schedules', data)
}

export function listSchedules(query: ScheduleQueryParams = {}): Promise<PageData<ScheduleVO>> {
  return get<PageData<ScheduleVO>>('/schedules', cleanParams(query))
}

export function getSchedule(id: number): Promise<ScheduleVO> {
  return get<ScheduleVO>(`/schedules/${id}`)
}

export function updateSchedule(id: number, data: ScheduleSaveRequest): Promise<ScheduleVO> {
  return put<ScheduleVO>(`/schedules/${id}`, data)
}

export function deleteSchedule(id: number): Promise<void> {
  return del<void>(`/schedules/${id}`)
}

export function batchCreateSchedules(schedules: ScheduleSaveRequest[]): Promise<ScheduleVO[]> {
  return post<ScheduleVO[]>('/schedules/batch', { schedules })
}

/** 当前穿梭中的日程；无进行中时服务端返回 code=0 且 data=null */
export function getActiveSchedule(): Promise<ScheduleVO | null> {
  return get<ScheduleVO | null>('/schedules/active')
}

/* ---------- 计时状态机五连 ---------- */

export function startSchedule(id: number): Promise<ScheduleVO> {
  return post<ScheduleVO>(`/schedules/${id}/start`)
}

export function pauseSchedule(id: number): Promise<ScheduleVO> {
  return post<ScheduleVO>(`/schedules/${id}/pause`)
}

export function resumeSchedule(id: number): Promise<ScheduleVO> {
  return post<ScheduleVO>(`/schedules/${id}/resume`)
}

/** 落梭：结算时长并发放光阴砂 */
export function endSchedule(id: number): Promise<ScheduleVO> {
  return post<ScheduleVO>(`/schedules/${id}/end`)
}

export function cancelSchedule(id: number): Promise<ScheduleVO> {
  return post<ScheduleVO>(`/schedules/${id}/cancel`)
}
