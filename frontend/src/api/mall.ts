import { get, post, put } from './http'
import {
  ItemType,
  MallItemDetailVO,
  MallItemPageVO,
  PurchaseResultVO,
  WardrobeItemVO
} from '@/types'

export function listMallItems(type?: ItemType | string, page = 1, pageSize = 20): Promise<MallItemPageVO> {
  return get<MallItemPageVO>('/mall/items', { type: type || undefined, page, pageSize })
}

export function getMallItem(id: number): Promise<MallItemDetailVO> {
  return get<MallItemDetailVO>(`/mall/items/${id}`)
}

/** 购买；错误分支：409 积分不足/售罄/已拥有，429 抢锁失败 */
export function purchaseItem(itemId: number): Promise<PurchaseResultVO> {
  return post<PurchaseResultVO>('/mall/purchase', { itemId })
}

/* ---------- 装扮仓库（路由在商城服务，但路径挂在 /users/wardrobe） ---------- */

export function listWardrobe(): Promise<WardrobeItemVO[]> {
  return get<WardrobeItemVO[]>('/users/wardrobe')
}

/** 切换装扮；type 只做一致性校验，互斥判定以仓库记录为准（服务端语义） */
export function activateWardrobeItem(itemId: number, type: ItemType | string): Promise<void> {
  return put<void>('/users/wardrobe/active', { itemId, type })
}
