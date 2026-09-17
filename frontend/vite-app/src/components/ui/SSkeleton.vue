<script setup lang="ts">
/** 骨架屏（§十·加载）：微光脉冲，支持多行 */
withDefaults(defineProps<{ lines?: number; height?: number }>(), { lines: 3, height: 14 })
</script>

<template>
  <div class="s-skeleton" aria-busy="true">
    <div
      v-for="i in lines"
      :key="i"
      class="s-skeleton__line skeleton-pulse"
      :style="{ height: `${height}px`, width: i === lines ? '62%' : '100%' }"
    />
  </div>
</template>

<style scoped>
.s-skeleton { display: flex; flex-direction: column; gap: 10px; }
.s-skeleton__line {
  background: var(--sorts-color-line-200);
  border-radius: var(--sorts-radius-ctl);
}
</style>
