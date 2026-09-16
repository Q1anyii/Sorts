import { get, put } from './http'
import { PointsVO, UpdateUserRequest, UserVO } from '@/types'

export function getMe(): Promise<UserVO> {
  return get<UserVO>('/users/me')
}

export function updateMe(data: UpdateUserRequest): Promise<UserVO> {
  return put<UserVO>('/users/me', data)
}

/** 积分余额 + 最近流水；limit 缺省 20（服务端默认） */
export function getPoints(limit = 20): Promise<PointsVO> {
  return get<PointsVO>('/users/points', { limit })
}
