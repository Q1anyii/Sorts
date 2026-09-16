<script setup lang="ts">
export interface STabItem {
  value: string
  label: string
}

defineProps<{ modelValue: string; items: STabItem[] }>()
const emit = defineEmits<{ 'update:modelValue': [v: string] }>()
</script>

<template>
  <div class="s-tabs" role="tablist">
    <button
      v-for="item in items"
      :key="item.value"
      type="button"
      role="tab"
      class="s-tabs__item"
      :class="{ 'is-active': item.value === modelValue }"
      :aria-selected="item.value === modelValue"
      @click="emit('update:modelValue', item.value)"
    >
      {{ item.label }}
    </button>
  </div>
</template>

<style scoped>
.s-tabs {
  display: inline-flex;
  gap: 2px;
  padding: 3px;
  background: var(--sorts-color-line-100);
  border-radius: var(--sorts-radius-ctl);
}
.s-tabs__item {
  border: none;
  background: transparent;
  color: var(--sorts-text-faint);
  font-size: var(--sorts-text-sm);
  padding: 5px 14px;
  border-radius: calc(var(--sorts-radius-ctl) - 2px);
  cursor: pointer;
  transition: color var(--sorts-dur-fast) var(--sorts-ease-out),
    background-color var(--sorts-dur-fast) var(--sorts-ease-out);
}
.s-tabs__item.is-active {
  background: var(--sorts-surface);
  color: var(--sorts-ink);
  font-weight: 500;
  box-shadow: var(--sorts-shadow-1);
}
</style>
