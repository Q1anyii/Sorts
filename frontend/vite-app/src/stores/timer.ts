import { defineStore } from 'pinia'
import {
  cancelSchedule,
  endSchedule,
  getActiveSchedule,
  pauseSchedule,
  resumeSchedule,
  startSchedule
} from '@/api/schedule'
import { parseLocal } from '@/utils/datetime'
import type { ScheduleVO } from '@/types'
import type { TimerAction } from '@/utils/status'

/**
 * 计时（穿梭）状态。
 * 只跟踪「当前进行中/暂停」的那一条日程；
 * 刷新或重开页面后通过 getActiveSchedule() 恢复，断线不丢秒。
 */
export const useTimerStore = defineStore('timer', {
  state: () => ({
    active: null as ScheduleVO | null,
    /** 每秒刷新一次的基准时间戳（驱动 elapsedSeconds 重算） */
    now: Date.now(),
    loading: false,
    _ticker: 0 as number // setInterval 句柄；0 表示未启动
  }),
  getters: {
    /** 已流逝秒数：IN_PROGRESS = 历史累计(actualDuration) + 本次(actualStartTime→now) */
    elapsedSeconds(): number {
      const s = this.active
      if (!s) return 0
      const base = s.actualDuration ?? 0
      if (s.status !== 'IN_PROGRESS' || !s.actualStartTime) return base
      const start = parseLocal(s.actualStartTime)
      if (!start) return base
      return base + Math.max(0, Math.floor((this.now - start.getTime()) / 1000))
    },
    isRunning: (s): boolean => s.active?.status === 'IN_PROGRESS',
    isPaused: (s): boolean => s.active?.status === 'PAUSED'
  },
  actions: {
    /** 恢复进行中的计时；无进行中则静默置空 */
    async recover(): Promise<void> {
      this.active = await getActiveSchedule()
      this.syncTicker()
    },
    /** 状态机五连；action 非法时后端 409，交由调用方提示 */
    async act(action: TimerAction): Promise<ScheduleVO> {
      const s = this.active
      if (!s) throw new Error('当前没有进行中的日程')
      this.loading = true
      try {
        const fn = { start: startSchedule, pause: pauseSchedule, resume: resumeSchedule, end: endSchedule, cancel: cancelSchedule }[action]
        const updated = await fn(s.id)
        // 结束/取消后不再有「活动日程」
        this.active = updated.status === 'IN_PROGRESS' || updated.status === 'PAUSED' ? updated : null
        this.now = Date.now()
        this.syncTicker()
        return updated
      } finally {
        this.loading = false
      }
    },
    /** 从列表页直接开梭一条 PENDING 日程 */
    async startFor(id: number): Promise<ScheduleVO> {
      this.loading = true
      try {
        const updated = await startSchedule(id)
        this.active = updated
        this.now = Date.now()
        this.syncTicker()
        return updated
      } finally {
        this.loading = false
      }
    },
    clear(): void {
      this.active = null
      this.syncTicker()
    },
    syncTicker(): void {
      const need = this.active?.status === 'IN_PROGRESS'
      if (need && !this._ticker) {
        this._ticker = window.setInterval(() => {
          this.now = Date.now()
        }, 1000)
      } else if (!need && this._ticker) {
        window.clearInterval(this._ticker)
        this._ticker = 0
      }
    }
  }
})
