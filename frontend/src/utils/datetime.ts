/* 日期与格式化工具。时长口径：对外一律「秒」（plannedDuration 例外，是分钟）。 */

export function pad2(n: number): string {
  return String(n).padStart(2, '0')
}

/** yyyy-MM-dd（本地时区） */
export function dateStr(d: Date): string {
  return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`
}

export function todayStr(): string {
  return dateStr(new Date())
}

export function addDays(base: string, days: number): string {
  const d = new Date(`${base}T00:00:00`)
  d.setDate(d.getDate() + days)
  return dateStr(d)
}

/** 解析后端 ISO 字符串（无时区，按本地时间） */
export function parseLocal(dt: string | null | undefined): Date | null {
  if (!dt) return null
  const d = new Date(dt)
  return Number.isNaN(d.getTime()) ? null : d
}

/** HH:mm */
export function formatTime(dt: string | null | undefined): string {
  const d = parseLocal(dt)
  return d ? `${pad2(d.getHours())}:${pad2(d.getMinutes())}` : ''
}

/** M月D日 */
export function formatDateLabel(dt: string | null | undefined): string {
  const d = parseLocal(dt)
  return d ? `${d.getMonth() + 1}月${d.getDate()}日` : ''
}

export function formatDateTime(dt: string | null | undefined): string {
  const d = parseLocal(dt)
  return d ? `${formatDateLabel(dt)} ${formatTime(dt)}` : ''
}

const WEEKDAYS = ['日', '一', '二', '三', '四', '五', '六']

export function weekdayCn(d: Date): string {
  return `周${WEEKDAYS[d.getDay()]}`
}

/** 秒 → 人类可读（统计/时长展示用）：<1h 显示「N 分钟」，否则「X 小时 Y 分」 */
export function formatDuration(seconds: number | null | undefined): string {
  const s = Math.max(0, Math.floor(seconds ?? 0))
  const m = Math.floor(s / 60)
  if (m < 1) return s > 0 ? `${s} 秒` : '0 分钟'
  if (m < 60) return `${m} 分钟`
  const h = Math.floor(m / 60)
  const rest = m % 60
  return rest > 0 ? `${h} 小时 ${rest} 分` : `${h} 小时`
}

/** 秒 → HH:mm:ss（计时器，等宽显示） */
export function formatTimer(totalSeconds: number): string {
  const s = Math.max(0, Math.floor(totalSeconds))
  const h = Math.floor(s / 3600)
  const m = Math.floor((s % 3600) / 60)
  const sec = s % 60
  return `${pad2(h)}:${pad2(m)}:${pad2(sec)}`
}

/* ---------- 十二时辰（§13：装饰位，不影响 24 小时制主显示） ---------- */

const SHICHEN: Array<{ name: string; start: number }> = [
  { name: '子', start: 23 }, { name: '丑', start: 1 }, { name: '寅', start: 3 },
  { name: '卯', start: 5 }, { name: '辰', start: 7 }, { name: '巳', start: 9 },
  { name: '午', start: 11 }, { name: '未', start: 13 }, { name: '申', start: 15 },
  { name: '酉', start: 17 }, { name: '戌', start: 19 }, { name: '亥', start: 21 }
]

/** 当前时辰，如「巳时 · 09:00–11:00」 */
export function shichenLabel(now: Date = new Date()): string {
  const h = now.getHours()
  const hit = [...SHICHEN].reverse().find((s) => h >= s.start) ?? SHICHEN[0]
  const end = (hit.start + 2) % 24
  return `${hit.name}时 · ${pad2(hit.start)}:00–${pad2(end)}:00`
}

/* ---------- 二十四节气（§13：近似公式，装饰位） ---------- */

/** 21 世纪节气常数 C（1901–2100 通用公式：day = ⌊Y×0.2422 + C⌋ − ⌊(Y−1)/4⌋） */
const SOLAR_TERMS: Array<{ name: string; month: number; c: number }> = [
  { name: '小寒', month: 1, c: 6.11 }, { name: '大寒', month: 1, c: 20.84 },
  { name: '立春', month: 2, c: 3.87 }, { name: '雨水', month: 2, c: 18.73 },
  { name: '惊蛰', month: 3, c: 5.63 }, { name: '春分', month: 3, c: 20.646 },
  { name: '清明', month: 4, c: 4.81 }, { name: '谷雨', month: 4, c: 20.1 },
  { name: '立夏', month: 5, c: 5.52 }, { name: '小满', month: 5, c: 21.04 },
  { name: '芒种', month: 6, c: 5.678 }, { name: '夏至', month: 6, c: 21.37 },
  { name: '小暑', month: 7, c: 7.108 }, { name: '大暑', month: 7, c: 22.83 },
  { name: '立秋', month: 8, c: 7.5 }, { name: '处暑', month: 8, c: 23.13 },
  { name: '白露', month: 9, c: 7.646 }, { name: '秋分', month: 9, c: 23.042 },
  { name: '寒露', month: 10, c: 8.318 }, { name: '霜降', month: 10, c: 23.438 },
  { name: '立冬', month: 11, c: 7.438 }, { name: '小雪', month: 11, c: 22.36 },
  { name: '大雪', month: 12, c: 7.18 }, { name: '冬至', month: 12, c: 21.94 }
]

function solarTermDay(year: number, c: number): number {
  const y = year % 100
  return Math.floor(y * 0.2422 + c) - Math.floor((y - 1) / 4)
}

/** 给定日期（yyyy-MM-dd）若恰逢节气则返回节气名，否则 null */
export function solarTermOf(date: string): string | null {
  const d = new Date(`${date}T00:00:00`)
  if (Number.isNaN(d.getTime())) return null
  const year = d.getFullYear()
  const month = d.getMonth() + 1
  const day = d.getDate()
  for (const term of SOLAR_TERMS) {
    if (term.month === month && solarTermDay(year, term.c) === day) {
      return term.name
    }
  }
  return null
}
