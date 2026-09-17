<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { getMallItem, listMallItems, purchaseItem } from '@/api/mall'
import type { MallItemDetailVO, MallItemPageVO, MallItemVO } from '@/types'
import { useAppStore } from '@/stores/app'
import { useAuthStore } from '@/stores/auth'
import { ApiError } from '@/api/error'
import { ITEM_TYPE_META, itemTypeTone, stockLabel } from '@/utils/status'
import SCard from '@/components/ui/SCard.vue'
import SButton from '@/components/ui/SButton.vue'
import SSelect from '@/components/ui/SSelect.vue'
import SModal from '@/components/ui/SModal.vue'
import SSkeleton from '@/components/ui/SSkeleton.vue'
import SEmpty from '@/components/ui/SEmpty.vue'
import SIcon from '@/components/ui/SIcon.vue'

/** 锦市：商品货架 + 积分余额 + 购买确认 */
const app = useAppStore()
const auth = useAuthStore()

const TYPE_OPTIONS = [
  { value: '', label: '全部品类' },
  { value: 'SKIN', label: '皮肤' },
  { value: 'AVATAR', label: '头像' },
  { value: 'BADGE', label: '徽章' },
  { value: 'STICKER', label: '贴纸' }
]

const typeFilter = ref('')
const page = ref(1)
const data = ref<MallItemPageVO | null>(null)
const loading = ref(true)

const buying = ref<MallItemVO | null>(null)
const buyingDetail = ref<MallItemDetailVO | null>(null)
const purchaseBusy = ref(false)

async function load() {
  loading.value = true
  try {
    data.value = await listMallItems(typeFilter.value || undefined, page.value, 12)
  } catch (e) {
    app.toast(e instanceof ApiError ? e.message : '加载失败', 'error')
  } finally {
    loading.value = false
  }
}

function onFilterChange() {
  page.value = 1
  load()
}

async function openBuy(item: MallItemVO) {
  buying.value = item
  buyingDetail.value = null
  try {
    buyingDetail.value = await getMallItem(item.id)
  } catch {
    /* 详情降级：仍可用列表信息购买 */
  }
}

async function confirmBuy() {
  if (!buying.value) return
  purchaseBusy.value = true
  try {
    const result = await purchaseItem(buying.value.id)
    app.toast(`「${result.item.name}」已入衣橱`, 'success')
    auth.setPoints(result.remainingPoints)
    buying.value = null
    await load()
  } catch (e) {
    // 409 积分不足/售罄/已拥有、429 抢锁失败：文案直接用服务端 message
    app.toast(e instanceof ApiError ? e.message : '购买失败', 'error')
  } finally {
    purchaseBusy.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="mall">
    <div class="mall__head">
      <p class="mall__points num">
        <SIcon name="sun" :size="16" /> 光阴砂：<strong>{{ data?.userPoints ?? auth.points }}</strong>
        <span v-if="data?.userPoints == null" class="mall__points-degraded">（积分服务暂不可用）</span>
      </p>
      <SSelect v-model="typeFilter" :options="TYPE_OPTIONS" class="mall__filter" @update:model-value="onFilterChange" />
    </div>

    <SSkeleton v-if="loading" :lines="4" />
    <SEmpty v-else-if="!data || !data.list.length" text="货架空空如也" icon="shop" />

    <div v-else class="mall__grid">
      <SCard v-for="item in data.list" :key="item.id" hover class="mall__item weave-in">
        <div class="mall__item-preview" :style="{ background: itemTypeTone(item.type).bg, color: itemTypeTone(item.type).fg }">
          <img v-if="item.imageUrl" :src="item.imageUrl" :alt="item.name" class="mall__item-img" />
          <span v-else class="mall__item-glyph">{{ ITEM_TYPE_META[item.type] ?? item.type }}</span>
        </div>
        <div class="mall__item-info">
          <h4 class="mall__item-name">{{ item.name }}</h4>
          <p class="mall__item-stock num">{{ stockLabel(item.stock) }}</p>
        </div>
        <div class="mall__item-foot">
          <span class="mall__item-price num">{{ item.price }} 砂</span>
          <SButton size="sm" variant="gold" :disabled="item.stock === 0" @click="openBuy(item)">购入</SButton>
        </div>
      </SCard>
    </div>

    <div v-if="data && data.total > 12" class="mall__pager num">
      <SButton variant="ghost" size="sm" :disabled="page <= 1" @click="page -= 1; load()">上一页</SButton>
      <span>第 {{ page }} 页</span>
      <SButton variant="ghost" size="sm" :disabled="data.list.length < 12" @click="page += 1; load()">下一页</SButton>
    </div>

    <!-- 购买确认 -->
    <SModal :open="!!buying" title="购入确认" :width="420" @close="buying = null">
      <div v-if="buying" class="mall__confirm">
        <p class="mall__confirm-name">{{ buying.name }}</p>
        <p v-if="buying.description" class="mall__confirm-desc">{{ buying.description }}</p>
        <p class="mall__confirm-price num">
          价格 {{ buying.price }} 砂
          <template v-if="buyingDetail?.owned"> · <span class="mall__confirm-owned">已拥有</span></template>
        </p>
      </div>
      <template #footer>
        <SButton variant="ghost" @click="buying = null">再看看</SButton>
        <SButton variant="gold" :loading="purchaseBusy" :disabled="buyingDetail?.owned" @click="confirmBuy">
          确认购入
        </SButton>
      </template>
    </SModal>
  </div>
</template>

<style scoped>
.mall { display: flex; flex-direction: column; gap: var(--sorts-space-4); }
.mall__head { display: flex; align-items: center; justify-content: space-between; gap: var(--sorts-space-3); flex-wrap: wrap; }
.mall__points { margin: 0; display: inline-flex; align-items: center; gap: 6px; color: var(--sorts-color-gold-700); font-size: var(--sorts-text-base); }
.mall__points-degraded { font-size: var(--sorts-text-xs); color: var(--sorts-text-faint); }
.mall__filter { width: 140px; }
.mall__grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(200px, 1fr)); gap: var(--sorts-space-3); }
.mall__item { display: flex; flex-direction: column; gap: var(--sorts-space-2); padding: var(--sorts-space-3); }
.mall__item-preview {
  aspect-ratio: 4 / 3;
  border-radius: var(--sorts-radius-ctl);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}
.mall__item-img { width: 100%; height: 100%; object-fit: cover; }
.mall__item-glyph { font-family: var(--sorts-font-serif); font-size: var(--sorts-text-xl); }
.mall__item-info { display: flex; flex-direction: column; gap: 2px; }
.mall__item-name { margin: 0; font-size: var(--sorts-text-base); color: var(--sorts-ink); }
.mall__item-stock { margin: 0; font-size: var(--sorts-text-xs); color: var(--sorts-text-faint); }
.mall__item-foot { display: flex; align-items: center; justify-content: space-between; }
.mall__item-price { color: var(--sorts-color-gold-700); font-size: var(--sorts-text-sm); font-weight: 600; }
.mall__pager { display: flex; align-items: center; justify-content: center; gap: var(--sorts-space-3); color: var(--sorts-text-faint); font-size: var(--sorts-text-sm); }
.mall__confirm { display: flex; flex-direction: column; gap: 8px; }
.mall__confirm-name { margin: 0; font-size: var(--sorts-text-lg); color: var(--sorts-ink); }
.mall__confirm-desc { margin: 0; font-size: var(--sorts-text-sm); color: var(--sorts-text-faint); }
.mall__confirm-price { margin: 0; color: var(--sorts-color-gold-700); }
.mall__confirm-owned { color: var(--sorts-color-arrow-700); }
</style>
