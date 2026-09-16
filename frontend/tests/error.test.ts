import { describe, expect, it } from 'vitest'
import { ApiError } from '../src/api/error'
import { ErrorCode } from '../src/types'

describe('ApiError 语义判定', () => {
  it('按 code 归类', () => {
    expect(new ApiError(ErrorCode.TOKEN_EXPIRED, 'x').isUnauthorized).toBe(true)
    expect(new ApiError(ErrorCode.RATE_LIMITED, 'x').isRateLimited).toBe(true)
    expect(new ApiError(ErrorCode.CONFLICT, 'x').isConflict).toBe(true)
    expect(new ApiError(ErrorCode.SERVICE_UNAVAILABLE, 'x').isUnavailable).toBe(true)
    expect(new ApiError(ErrorCode.PARAM_ERROR, 'x').isUnauthorized).toBe(false)
  })

  it('network() 工厂', () => {
    const e = ApiError.network()
    expect(e.isNetwork).toBe(true)
    expect(e.message).toBeTruthy()
  })
})
