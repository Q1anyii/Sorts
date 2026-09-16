import { describe, expect, it } from 'vitest'
import {
  addDays,
  dateStr,
  formatDuration,
  formatTime,
  formatTimer,
  parseLocal,
  shichenLabel,
  solarTermOf,
  todayStr
} from '../src/utils/datetime'

describe('datetime 工具', () => {
  it('dateStr/todayStr 输出 yyyy-MM-dd', () => {
    expect(dateStr(new Date(2026, 8, 16))).toBe('2026-09-16')
    expect(todayStr()).toMatch(/^\d{4}-\d{2}-\d{2}$/)
  })

  it('addDays 跨月进位', () => {
    expect(addDays('2026-01-31', 1)).toBe('2026-02-01')
    expect(addDays('2026-03-01', -1)).toBe('2026-02-28')
  })

  it('parseLocal 容忍空值与坏串', () => {
    expect(parseLocal(null)).toBeNull()
    expect(parseLocal('')).toBeNull()
    expect(parseLocal('not-a-date')).toBeNull()
    expect(parseLocal('2026-09-16T09:00:00')?.getHours()).toBe(9)
  })

  it('formatTime 输出 HH:mm', () => {
    expect(formatTime('2026-09-16T09:05:00')).toBe('09:05')
    expect(formatTime(null)).toBe('')
  })

  it('formatDuration 以秒为口径', () => {
    expect(formatDuration(0)).toBe('0 分钟')
    expect(formatDuration(45)).toBe('45 秒')
    expect(formatDuration(600)).toBe('10 分钟')
    expect(formatDuration(3600)).toBe('1 小时')
    expect(formatDuration(3660)).toBe('1 小时 1 分')
    expect(formatDuration(null)).toBe('0 分钟')
  })

  it('formatTimer 输出 HH:mm:ss', () => {
    expect(formatTimer(0)).toBe('00:00:00')
    expect(formatTimer(3661)).toBe('01:01:01')
    expect(formatTimer(-5)).toBe('00:00:00')
  })

  it('shichenLabel 命中时辰段', () => {
    expect(shichenLabel(new Date(2026, 8, 16, 10))).toContain('巳时')
    expect(shichenLabel(new Date(2026, 8, 16, 23))).toContain('子时')
  })

  it('solarTermOf 命中已知节气（2026 春分 = 3 月 20 日）', () => {
    expect(solarTermOf('2026-03-20')).toBe('春分')
    expect(solarTermOf('2026-03-21')).toBeNull()
  })
})
