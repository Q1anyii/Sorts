<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { activateWardrobeItem, listWardrobe } from '@/api/mall'
import type { WardrobeItemVO } from '@/types'
import { useAppStore } from '@/stores/app'
import { ApiError } from '@/api/error'
import { ITEM_TYPE_META, itemTypeTone } from '@/utils/status'
import { formatDateLabel } from '@/utils/datetime'
import SCard from '@/components/ui/SCard.vue'
import SButton from '@/components/ui/SButton.vue'
import SSkeleton from '@/components/ui/SSkeleton.vue'
import SEmpty from '@/components/ui/SEmpty.vue'
import STag from '@/components/ui/STag.vue'

/** 衣橱：已购装扮 + 启用/卸下（服务端互斥判定） */
const app = useAppStore()

const items = ref<WardrobeItemVO[]>([])
const loading = ref(true)
const busyId = ref(0)

/** 按类型分组展示 */
const grouped = computed(() => {
  const groups = new Map<string, WardrobeItemVO[]>()
  for (const it of items.value) {
    const key = String(it.item.type)
    if (!groups.has(key)) groups.set(key, [])
    groups.get(key)!.push(it)
  }
  return [...groups.entries()]
})

async function load() {
  loading.value = true
  try {
    items.value = await listWardrobe()
  } catch (e) {
    app.toast(e instanceof ApiError ? e.message : '加载失败', 'error')
  } finally {
    loading.value = false
  }
}

async function toggle(it: WardrobeItemVO) {
  busyId.value = it.id
  try {
    // 服务端语义：已启用再调即卸下，同类型互斥自动处理
    await activateWardrobeItem(it.item.id, it.item.type)
    app.toast(it.isActive ? `已卸下「${it.item.name}」` : `已启用「${it.item.name}」`, 'success')
    await load()
  } catch (e) {
    app.toast(e instanceof ApiError ? e.message : '操作失败', 'error')
  } finally {
    busyId.value = 0
  }
}

onMounted(load)
</script>

<template>
  <div class="wardrobe">
    <SSkeleton v-if="loading" :lines="4" />
    <SEmpty v-else-if="!items.length" text="衣橱还空着" hint="去「锦市」用光阴砂换几匹好料子" icon="wardrobe">
      <router-link to="/mall"><SButton variant="gold" size="sm">去锦市</SButton></router-link>
    </SEmpty>

    <template v-else>
      <section v-for="[type, list] in grouped" :key="type" class="wardrobe__group">
        <h3 class="wardrobe__group-title">{{ ITEM_TYPE_META[type] ?? type }}</h3>
        <div class="wardrobe__grid">
          <SCard v-for="it in list" :key="it.id" class="wardrobe__item weave-in" :class="{ 'is-active': it.isActive }">
            <div class="wardrobe__item-preview" :style="{ background: itemTypeTone(it.item.type).bg, color: itemTypeTone(it.item.type).fg }">
              <img v-if="it.item.imageUrl" :src="it.item.imageUrl" :alt="it.item.name" class="wardrobe__item-img" />
              <span v-else class="wardrobe__item-glyph">{{ ITEM_TYPE_META[it.item.type] ?? it.item.type }}</span>
            </div>
            <div class="wardrobe__item-info">
              <h4 class="wardrobe__item-name">{{ it.item.name }}</h4>
              <p class="wardrobe__item-meta num">购于 {{ formatDateLabel(it.purchasedAt) }}</p>
            </div>
            <div class="wardrobe__item-foot">
              <STag v-if="it.isActive" color="var(--sorts-color-arrow-700)">启用中</STag>
              <span v-else />
              <SButton size="sm" :variant="it.isActive ? 'ghost' : 'primary'" :loading="busyId === it.id" @click="toggle(it)">
                {{ it.isActive ? '卸下' : '启用' }}
              </SButton>
            </div>
          </SCard>
        </div>
      </section>
    </template>
  </div>
</template>

<style scoped>
.wardrobe { display: flex; flex-direction: column; gap: var(--sorts-space-5); }
.wardrobe__group { display: flex; flex-direction: column; gap: var(--sorts-space-3); }
.wardrobe__group-title { margin: 0; font-size: var(--sorts-text-base); color: var(--sorts-ink); }
.wardrobe__grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(200px, 1fr)); gap: var(--sorts-space-3); }
.wardrobe__item { display: flex; flex-direction: column; gap: var(--sorts-space-2); padding: var(--sorts-space-3); }
.wardrobe__item.is-active { border-color: var(--sorts-color-arrow-500); }
.wardrobe__item-preview {
  aspect-ratio: 4 / 3;
  border-radius: var(--sorts-radius-ctl);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}
.wardrobe__item-img { width: 100%; height: 100%; object-fit: cover; }
.wardrobe__item-glyph { font-family: var(--sorts-font-serif); font-size: var(--sorts-text-xl); }
.wardrobe__item-name { margin: 0; font-size: var(--sorts-text-base); color: var(--sorts-ink); }
.wardrobe__item-meta { margin: 0; font-size: var(--sorts-text-xs); color: var(--sorts-text-faint); }
.wardrobe__item-foot { display: flex; align-items: center; justify-content: space-between; }
</style>
