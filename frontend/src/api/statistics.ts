import { get } from './http'
import { StatisticsPeriod, StatisticsSummaryVO, TagStatVO, TrendPointVO } from '@/types'

/** 统计汇总；period 缺省 week，date 缺省今天（服务端兜底） */
export function getStatisticsSummary(period?: StatisticsPeriod, date?: string): Promise<StatisticsSummaryVO> {
  return get<StatisticsSummaryVO>('/statistics/summary', { period, date })
}

/** 最近 N 天趋势；days 缺省 30，服务端上限 365 */
export function getStatisticsTrend(days?: number): Promise<TrendPointVO[]> {
  return get<TrendPointVO[]>('/statistics/trend', { days })
}

/** 标签分布；period 缺省 month，支持 all */
export function getStatisticsTags(period?: StatisticsPeriod | 'all'): Promise<TagStatVO[]> {
  return get<TagStatVO[]>('/statistics/tags', { period })
}
