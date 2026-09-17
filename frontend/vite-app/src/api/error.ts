import { ErrorCode } from '@/types'

/**
 * 统一业务错误。所有 API 层抛出的错误都是 ApiError，
 * 视图层只需要看 code，不需要分辨是 HTTP 错误还是 Result 业务错误。
 */
export class ApiError extends Error {
  readonly code: number
  readonly status?: number

  constructor(code: number, message: string, status?: number) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.status = status
  }

  /** 网络层失败（无响应 / 跨域 / 断网），code 固定 -1 */
  static network(message = '网络异常，请检查连接后重试'): ApiError {
    return new ApiError(-1, message)
  }

  get isUnauthorized(): boolean {
    return (
      this.code === ErrorCode.UNAUTHORIZED ||
      this.code === ErrorCode.TOKEN_EXPIRED ||
      this.code === ErrorCode.TOKEN_INVALID ||
      this.code === ErrorCode.REFRESH_TOKEN_INVALID
    )
  }

  /** 429：网关限流 / 购买抢锁失败 */
  get isRateLimited(): boolean {
    return this.code === ErrorCode.RATE_LIMITED
  }

  /** 409：积分不足 / 售罄 / 非法状态流转 / 用户名重复 */
  get isConflict(): boolean {
    return this.code === ErrorCode.CONFLICT
  }

  /** 503：AI 未配密钥 / 依赖服务不可用 */
  get isUnavailable(): boolean {
    return this.code === ErrorCode.SERVICE_UNAVAILABLE
  }

  get isNetwork(): boolean {
    return this.code === -1
  }
}

/** 把任意异常收敛为 ApiError（视图层兜底用） */
export function toApiError(e: unknown, fallback = '系统繁忙，请稍后再试'): ApiError {
  if (e instanceof ApiError) return e
  if (e instanceof Error) return new ApiError(ErrorCode.SYSTEM_ERROR, e.message || fallback)
  return new ApiError(ErrorCode.SYSTEM_ERROR, fallback)
}
