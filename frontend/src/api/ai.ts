import { get, post } from './http'
import { ssePost, SseHandlers, SseSession } from './sse'
import {
  AdoptPlanRequest,
  AIPlanRequest,
  AIPlanResponse,
  AIReportInfo,
  AISummaryRequest,
  AsyncReportResponse,
  ChatRequest,
  ChatResponse,
  PageData,
  PeriodSummaryRequest,
  ScheduleVO
} from '@/types'

/**
 * AI 接口的传输方式由 query 参数决定（不是请求体）：
 * - /ai/chat、/ai/summary/daily：默认 SSE，?stream=false 走 JSON
 * - /ai/plan：默认 JSON，?stream=true 走 SSE
 * 月报 / 年报固定为异步（202 + reportId），前端轮询 /reports/{id}。
 */

/* ---------- 对话助手 ---------- */

export function chatStream(body: ChatRequest, handlers: SseHandlers<ChatResponse>): SseSession {
  return ssePost<ChatResponse>('/ai/chat', body, handlers)
}

export function chatJson(body: ChatRequest): Promise<ChatResponse> {
  return post<ChatResponse>('/ai/chat?stream=false', body)
}

/* ---------- 日程规划 ---------- */

export function generatePlan(body: AIPlanRequest): Promise<AIPlanResponse> {
  return post<AIPlanResponse>('/ai/plan', body)
}

export function generatePlanStream(body: AIPlanRequest, handlers: SseHandlers<AIPlanResponse>): SseSession {
  return ssePost<AIPlanResponse>('/ai/plan?stream=true', body, handlers)
}

export function adoptPlan(planId: string, body: Omit<AdoptPlanRequest, 'planId'>): Promise<ScheduleVO[]> {
  return post<ScheduleVO[]>(`/ai/plan/${encodeURIComponent(planId)}/adopt`, body)
}

/* ---------- 周期总结 ---------- */

export function dailySummaryStream(body: AISummaryRequest, handlers: SseHandlers<AIReportInfo>): SseSession {
  return ssePost<AIReportInfo>('/ai/summary/daily', body, handlers)
}

export function dailySummaryJson(body: AISummaryRequest): Promise<AIReportInfo> {
  return post<AIReportInfo>('/ai/summary/daily?stream=false', body)
}

/** 月报：202 + reportId，之后轮询 getReport */
export function monthlySummary(body: PeriodSummaryRequest = {}): Promise<AsyncReportResponse> {
  return post<AsyncReportResponse>('/ai/summary/monthly', body)
}

export function yearlySummary(body: PeriodSummaryRequest = {}): Promise<AsyncReportResponse> {
  return post<AsyncReportResponse>('/ai/summary/yearly', body)
}

/* ---------- 报告 ---------- */

export function listReports(type?: string, page = 1, pageSize = 10): Promise<PageData<AIReportInfo>> {
  return get<PageData<AIReportInfo>>('/ai/reports', { type, page, pageSize })
}

export function getReport(id: number): Promise<AIReportInfo> {
  return get<AIReportInfo>(`/ai/reports/${id}`)
}
