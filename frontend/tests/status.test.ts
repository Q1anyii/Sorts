import { describe, expect, it } from 'vitest'
import { allowedActions, statusMeta, stockLabel } from '../src/utils/status'

describe('状态机与状态语义', () => {
  it('状态机允许的动作与后端 TimerService 对齐', () => {
    expect(allowedActions('PENDING')).toEqual(['start', 'cancel'])
    expect(allowedActions('IN_PROGRESS')).toEqual(['pause', 'end', 'cancel'])
    expect(allowedActions('PAUSED')).toEqual(['resume', 'end', 'cancel'])
    expect(allowedActions('COMPLETED')).toEqual([])
    expect(allowedActions('CANCELLED')).toEqual([])
    expect(allowedActions('TIMEOUT')).toEqual([])
  })

  it('印章形态映射（§6.3/§9.2）', () => {
    expect(statusMeta('IN_PROGRESS').seal).toBe('solid-gold')
    expect(statusMeta('COMPLETED').seal).toBe('outline-arrow')
    expect(statusMeta('TIMEOUT').seal).toBe('solid-late')
    expect(statusMeta('PENDING').seal).toBeNull()
  })

  it('未知状态降级为 PENDING', () => {
    expect(statusMeta('SOMETHING_ELSE').label).toBe('待开始')
  })

  it('库存文案', () => {
    expect(stockLabel(-1)).toBe('不限量')
    expect(stockLabel(0)).toBe('已售罄')
    expect(stockLabel(3)).toBe('仅剩 3 件')
  })
})
