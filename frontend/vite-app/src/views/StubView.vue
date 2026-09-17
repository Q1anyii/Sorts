<script setup lang="ts">
/** 占位视图：模块尚未实现时的临时页面（随 M6 各任务逐个替换） */
defineProps<{ title?: string }>()
</script>

<template>
  <section class="stub-view view-in">
    <h1 class="stub-title">{{ title ?? '模块' }}</h1>
    <p class="stub-hint">此模块正在织造中，敬请期待。</p>
  </section>
</template>

<style scoped>
.stub-view {
  padding: var(--sorts-space-8, 32px);
  display: flex;
  flex-direction: column;
  gap: var(--sorts-space-3, 12px);
  align-items: flex-start;
}
.stub-title {
  font-size: var(--sorts-text-2xl, 24px);
  color: var(--sorts-ink);
}
.stub-hint {
  color: var(--sorts-text-faint);
}
</style>
