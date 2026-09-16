/* 令牌存取：独立成无依赖模块，避免 http 拦截器与 Pinia store 循环引用 */
import type { TokenVO } from '@/types'

const ACCESS_KEY = 'sorts.access'
const REFRESH_KEY = 'sorts.refresh'

export const tokenStore = {
  get access(): string | null {
    return localStorage.getItem(ACCESS_KEY)
  },
  get refresh(): string | null {
    return localStorage.getItem(REFRESH_KEY)
  },
  save(tokens: Pick<TokenVO, 'accessToken' | 'refreshToken'>): void {
    localStorage.setItem(ACCESS_KEY, tokens.accessToken)
    localStorage.setItem(REFRESH_KEY, tokens.refreshToken)
  },
  clear(): void {
    localStorage.removeItem(ACCESS_KEY)
    localStorage.removeItem(REFRESH_KEY)
  }
}
