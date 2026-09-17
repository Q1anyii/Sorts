<script setup lang="ts">
/** 小加载圈：只用 transform/opacity 的旋转 */
withDefaults(defineProps<{ size?: number }>(), { size: 18 })
</script>

<template>
  <span class="s-spinner" :style="{ width: `${size}px`, height: `${size}px` }" role="status" aria-label="加载中" />
</template>

<style scoped>
.s-spinner {
  display: inline-block;
  border: 2px solid currentColor;
  border-right-color: transparent;
  border-radius: 50%;
  opacity: .9;
  animation: s-spin .7s linear infinite;
}
@keyframes s-spin {
  to { transform: rotate(360deg); }
}
@media (prefers-reduced-motion: reduce) {
  .s-spinner { animation: none; border-right-color: currentColor; }
}
</style>
