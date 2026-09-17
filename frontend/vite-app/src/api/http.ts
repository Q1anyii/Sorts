import axios, {
  AxiosError,
  AxiosInstance,
  AxiosRequestConfig,
  InternalAxiosRequestConfig
} from 'axios'
import { ApiError } from './error'
import { tokenStore } from './token'
import { ErrorCode, TokenVO, ApiResult } from '@/types'

/**
 * HTTP 层约定（与后端 GlobalExceptionHandler / Result 对齐）：
 * - 业务成功：HTTP 200 + body.code === 0 → 解包返回 data
 * - 业务失败：HTTP 200/4xx + body.code !== 0 → 抛 ApiError(code, message)
 * - HTTP 401（网关签发）：body 同样带 code（401/40101/40102）→ 尝试无感续期后重放
 * - 续期：单飞（single-flight），并发 401 只刷一次，其余请求挂同一 Promise
 * - 续期失败：清空令牌，触发 onSessionExpired（由 router 注册，跳登录页）
 */

// 注意：必须用 `||` 而非 `??`——Docker 构建时 VITE_API_BASE 为空字符串，
// `??` 对 "" 不兜底会得到空 baseURL，所有请求丢掉 /api/v1 前缀被 nginx 当静态路径返回 405
const BASE_URL = import.meta.env.VITE_API_BASE || '/api/v1'

/** 白名单：这些路径不附加令牌，也不参与 401 续期重放 */
const AUTH_PATHS = ['/auth/login', '/auth/register', '/auth/refresh']

type SessionExpiredHandler = () => void
let sessionExpiredHandler: SessionExpiredHandler = () => {}

/** 由应用入口注册：会话彻底失效（refresh 也被拒）时的跳转逻辑 */
export function onSessionExpired(handler: SessionExpiredHandler): void {
  sessionExpiredHandler = handler
}

export const http: AxiosInstance = axios.create({
  baseURL: BASE_URL,
  timeout: 15_000,
  headers: { 'Content-Type': 'application/json' }
})

/* ---------- 请求：附加 access token ---------- */
http.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const access = tokenStore.access
  const url = config.url ?? ''
  if (access && !AUTH_PATHS.some((p) => url.includes(p))) {
    // 后端按契约返回裸 JWT；存量令牌（旧版本后端签发）可能已带 "Bearer " 前缀，
    // 二次拼接会变成 "Bearer Bearer ..." 被网关按 40102 拒绝，这里做幂等保护
    config.headers.Authorization = access.startsWith('Bearer ') ? access : `Bearer ${access}`
  }
  return config
})

/* ---------- 续期单飞 ---------- */
let refreshPromise: Promise<string> | null = null

async function refreshAccessToken(): Promise<string> {
  if (!refreshPromise) {
    const refreshToken = tokenStore.refresh
    if (!refreshToken) {
      return Promise.reject(new ApiError(ErrorCode.REFRESH_TOKEN_INVALID, '登录状态已失效，请重新登录', 401))
    }
    // 用裸 axios，避免进入本实例的拦截器造成递归
    refreshPromise = axios
      .post<ApiResult<TokenVO>>(`${BASE_URL}/auth/refresh`, { refreshToken })
      .then((res) => {
        const body = res.data
        if (body.code !== 0) {
          throw new ApiError(body.code, body.message, res.status)
        }
        tokenStore.save(body.data)
        return body.data.accessToken
      })
      .finally(() => {
        refreshPromise = null
      })
  }
  return refreshPromise
}

interface RetryableConfig extends InternalAxiosRequestConfig {
  _retried?: boolean
}

async function handleUnauthorized(error: AxiosError): Promise<unknown> {
  const config = error.config as RetryableConfig | undefined
  const url = config?.url ?? ''

  // 已重试过 / 白名单路径 / 没有 refresh 可用 → 直接失败
  if (!config || config._retried || AUTH_PATHS.some((p) => url.includes(p))) {
    return Promise.reject(toHttpError(error))
  }
  config._retried = true

  try {
    const newAccess = await refreshAccessToken()
    config.headers.Authorization = `Bearer ${newAccess}`
    return http.request(config)
  } catch (e) {
    tokenStore.clear()
    sessionExpiredHandler()
    return Promise.reject(e instanceof ApiError ? e : toHttpError(error))
  }
}

/* ---------- 错误归一化 ---------- */
function toHttpError(error: AxiosError): ApiError {
  const status = error.response?.status
  const body = error.response?.data as Partial<ApiResult<unknown>> | undefined

  if (body && typeof body.code === 'number') {
    return new ApiError(body.code, body.message ?? '请求失败', status)
  }
  if (!error.response) {
    return ApiError.network()
  }
  // 网关限流返回统一 JSON（code=429），这里兜底非 JSON 情况
  const fallback: Record<number, [number, string]> = {
    400: [ErrorCode.PARAM_ERROR, '参数错误'],
    401: [ErrorCode.UNAUTHORIZED, '未登录或登录状态已失效'],
    403: [ErrorCode.FORBIDDEN, '无权访问该资源'],
    404: [ErrorCode.NOT_FOUND, '资源不存在'],
    429: [ErrorCode.RATE_LIMITED, '操作过于频繁，请稍后再试'],
    500: [ErrorCode.SYSTEM_ERROR, '系统繁忙，请稍后再试'],
    503: [ErrorCode.SERVICE_UNAVAILABLE, '依赖服务暂时不可用']
  }
  const [code, message] = fallback[status ?? 0] ?? [ErrorCode.SYSTEM_ERROR, '系统繁忙，请稍后再试']
  return new ApiError(code, message, status)
}

/* ---------- 响应：解包 Result / 归一化错误 ---------- */
http.interceptors.response.use(
  (response) => {
    const body = response.data as ApiResult<unknown> | unknown
    // 统一响应体：解包 data
    if (body && typeof body === 'object' && 'code' in body && typeof (body as ApiResult<unknown>).code === 'number') {
      const result = body as ApiResult<unknown>
      if (result.code === 0) {
        return result.data
      }
      return Promise.reject(new ApiError(result.code, result.message, response.status))
    }
    return body
  },
  (error: AxiosError) => {
    if (error.response?.status === 401) {
      return handleUnauthorized(error)
    }
    return Promise.reject(toHttpError(error))
  }
)

/* ---------- 便捷方法（直接拿解包后的 data） ---------- */
export function get<T>(url: string, params?: Record<string, unknown>, config?: AxiosRequestConfig): Promise<T> {
  return http.get(url, { params, ...config }) as unknown as Promise<T>
}

export function post<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
  return http.post(url, data, config) as unknown as Promise<T>
}

export function put<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
  return http.put(url, data, config) as unknown as Promise<T>
}

export function del<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
  return http.delete(url, config) as unknown as Promise<T>
}

/** 供 SSE 复用的完整地址拼装（fetch 不走 axios 实例） */
export function absoluteApiUrl(path: string): string {
  return `${BASE_URL}${path}`
}
