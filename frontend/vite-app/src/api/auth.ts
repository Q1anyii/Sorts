import { post } from './http'
import { tokenStore } from './token'
import { LoginRequest, RegisterRequest, TokenVO } from '@/types'

export function login(data: LoginRequest): Promise<TokenVO> {
  return post<TokenVO>('/auth/login', data)
}

export function register(data: RegisterRequest): Promise<TokenVO> {
  return post<TokenVO>('/auth/register', data)
}

export function logout(): Promise<void> {
  // 失败也要清空本地令牌（网关可能只是会话过期）
  return post<void>('/auth/logout').finally(() => tokenStore.clear())
}
