<script setup lang="ts">
/** 标签：默认经纬灰底，可用 color 指定文字色（底自动取低阶） */
withDefaults(defineProps<{ color?: string }>(), { color: '' })
</script>

<template>
  <span class="s-tag" :style="color ? { color, borderColor: color } : {}">
    <slot />
  </span>
</template>

<style scoped>
.s-tag {
  display: inline-flex;
  align-items: center;
  height: 22px;
  padding: 0 8px;
  font-size: var(--sorts-text-xs);
  color: var(--sorts-color-line-800);
  background: var(--sorts-color-line-100);
  border: 1px solid transparent;
  border-radius: var(--sorts-radius-ctl);
}
</style>
