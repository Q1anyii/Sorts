import { ApiError } from './error'
import { absoluteApiUrl } from './http'
import { tokenStore } from './token'
import { ApiResult, SseDeltaPayload, SseErrorPayload } from '@/types'

/**
 * SSE 客户端（fetch + ReadableStream 手写解析）。
 *
 * 为什么不用 EventSource：三个流式接口都是 POST + JSON body + Authorization 头，
 * EventSource 只支持 GET 且无法自定义请求头，只能走 fetch。
 *
 * 事件约定（sorts-ai/support/SseStream）：
 *   event: delta   data: {"content":"增量文本"}
 *   event: done    data: { ...最终结构化结果... }
 *   event: error   data: {"code":503,"message":"..."}
 */

export interface SseHandlers<TDone> {
  onDelta?: (content: string) => void
  onDone?: (payload: TDone) => void
  onError?: (error: ApiError) => void
}

export interface SseSession {
  /** 整个流结束（done / error / abort / 网络断开）时 settle */
  finished: Promise<void>
  /** 用户主动停止生成：静默结束，不触发 onError */
  abort: () => void
}

interface Frame {
  event: string
  data: string
}

/** 把一个文本块（可能含多帧、跨包半帧）切成完整帧；返回完整帧并保留残帧 */
export function splitFrames(buffer: string): { frames: Frame[]; rest: string } {
  const frames: Frame[] = []
  let rest = buffer
  // SSE 帧以空行分隔；规范换行是 \n，兼容 \r\n
  for (;;) {
    const idx = rest.search(/\r?\n\r?\n/)
    if (idx === -1) break
    const raw = rest.slice(0, idx)
    const sepLen = rest.slice(idx).match(/^\r?\n\r?\n/)![0].length
    rest = rest.slice(idx + sepLen)

    let event = 'message'
    const dataLines: string[] = []
    for (const line of raw.split(/\r?\n/)) {
      if (line.startsWith('event:')) event = line.slice(6).trim()
      else if (line.startsWith('data:')) dataLines.push(line.slice(5).replace(/^ /, ''))
    }
    if (dataLines.length > 0) {
      frames.push({ event, data: dataLines.join('\n') })
    }
  }
  return { frames, rest }
}

export function ssePost<TDone>(path: string, body: unknown, handlers: SseHandlers<TDone>): SseSession {
  const controller = new AbortController()
  let aborted = false

  const finished = (async () => {
    let response: Response
    try {
      response = await fetch(absoluteApiUrl(path), {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Accept: 'text/event-stream',
          ...(tokenStore.access ? { Authorization: `Bearer ${tokenStore.access}` } : {})
        },
        body: JSON.stringify(body),
        signal: controller.signal
      })
    } catch (e) {
      if (!aborted) handlers.onError?.(ApiError.network())
      return
    }

    if (!response.ok || !response.body) {
      // 网关/服务在流式前失败：响应仍是统一 JSON
      let error = new ApiError(response.status, '请求失败', response.status)
      try {
        const json = (await response.json()) as Partial<ApiResult<unknown>>
        if (typeof json.code === 'number') error = new ApiError(json.code, json.message ?? '请求失败', response.status)
      } catch {
        /* body 不是 JSON，保留 status 兜底 */
      }
      if (!aborted) handlers.onError?.(error)
      return
    }

    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''

    try {
      for (;;) {
        const { done, value } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true })

        const { frames, rest } = splitFrames(buffer)
        buffer = rest
        for (const frame of frames) {
          dispatch(frame, handlers)
        }
      }
      // 流末尾可能有没有空行结尾的残帧，兜底冲一次
      if (buffer.trim()) {
        const { frames } = splitFrames(buffer + '\n\n')
        for (const frame of frames) dispatch(frame, handlers)
      }
    } catch (e) {
      if (!aborted) handlers.onError?.(ApiError.network('连接中断，请重试'))
    }
  })()

  return {
    finished,
    abort: () => {
      aborted = true
      controller.abort()
    }
  }
}

function dispatch<TDone>(frame: Frame, handlers: SseHandlers<TDone>): void {
  try {
    if (frame.event === 'delta') {
      const payload = JSON.parse(frame.data) as SseDeltaPayload
      if (payload.content) handlers.onDelta?.(payload.content)
    } else if (frame.event === 'done') {
      handlers.onDone?.(JSON.parse(frame.data) as TDone)
    } else if (frame.event === 'error') {
      const payload = JSON.parse(frame.data) as SseErrorPayload
      handlers.onError?.(new ApiError(payload.code, payload.message))
    }
  } catch {
    // 单帧 JSON 损坏：跳过而不是中断整个流（与服务端 SseDataParser 同一策略：
    // 静默丢弃会表现为「回复缺一截」，所以只跳过坏帧，不吞掉后续帧）
  }
}
