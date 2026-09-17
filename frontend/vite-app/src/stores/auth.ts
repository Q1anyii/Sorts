import { defineStore } from 'pinia'
import { login as apiLogin, logout as apiLogout, register as apiRegister } from '@/api/auth'
import { getMe } from '@/api/user'
import { tokenStore } from '@/api/token'
import type { LoginRequest, RegisterRequest, UserVO } from '@/types'

/**
 * 会话与当前用户。
 * 令牌持久化在 tokenStore（localStorage），本 store 只持有用户对象。
 */
export const useAuthStore = defineStore('auth', {
  state: () => ({
    user: null as UserVO | null,
    /** 首次 fetchMe 是否完成过（区分「未加载」与「加载后为空」） */
    ready: false
  }),
  getters: {
    isAuthenticated: () => !!tokenStore.access,
    points: (s): number => s.user?.points ?? 0,
    nickname: (s): string => s.user?.nickname || s.user?.username || ''
  },
  actions: {
    async login(data: LoginRequest): Promise<void> {
      const token = await apiLogin(data)
      tokenStore.save(token)
      this.user = token.user
      this.ready = true
    },
    async register(data: RegisterRequest): Promise<void> {
      const token = await apiRegister(data)
      tokenStore.save(token)
      this.user = token.user
      this.ready = true
    },
    /** 启动时恢复会话；401 交给 http 拦截器续期，续不上会走 onSessionExpired */
    async fetchMe(): Promise<void> {
      this.user = await getMe()
      this.ready = true
    },
    async logout(): Promise<void> {
      // 服务端注销失败也要保证本地登出
      await apiLogout().catch(() => {})
      this.user = null
      this.ready = false
    },
    /** 其他模块（资料编辑 / 装扮激活 / 积分变动）同步本地用户 */
    applyUser(user: UserVO): void {
      this.user = user
    },
    setPoints(points: number): void {
      if (this.user) this.user.points = points
    }
  }
})
