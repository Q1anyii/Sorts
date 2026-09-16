<script setup lang="ts">
/** 纸盒卡（§十一·容器）：纸面 + 1px 经纬边 + 暖灰投影 */
withDefaults(defineProps<{ hover?: boolean; padded?: boolean }>(), { hover: false, padded: true })
</script>

<template>
  <div class="s-card" :class="{ 's-card--hover': hover, 's-card--padded': padded }">
    <slot />
  </div>
</template>

<style scoped>
.s-card {
  background: var(--sorts-surface);
  border: 1px solid var(--sorts-border);
  border-radius: var(--sorts-radius-box);
  box-shadow: var(--sorts-shadow-1);
  transition: transform var(--sorts-dur-fast) var(--sorts-ease-out),
    box-shadow var(--sorts-dur-fast) var(--sorts-ease-out);
}
.s-card--padded { padding: var(--sorts-space-4); }
.s-card--hover:hover {
  transform: translateY(-2px);
  box-shadow: var(--sorts-shadow-2);
}
</style>
