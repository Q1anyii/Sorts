<script setup lang="ts">
import SIcon from './SIcon.vue'

export interface SSelectOption {
  value: string
  label: string
}

withDefaults(
  defineProps<{ modelValue: string; options: SSelectOption[]; disabled?: boolean }>(),
  { disabled: false }
)

const emit = defineEmits<{ 'update:modelValue': [v: string] }>()
</script>

<template>
  <span class="s-select">
    <select
      class="s-select__native"
      :value="modelValue"
      :disabled="disabled"
      @change="emit('update:modelValue', ($event.target as HTMLSelectElement).value)"
    >
      <option v-for="o in options" :key="o.value" :value="o.value">{{ o.label }}</option>
    </select>
    <SIcon name="chevron-down" :size="14" class="s-select__arrow" />
  </span>
</template>

<style scoped>
.s-select { position: relative; display: inline-flex; width: 100%; }
.s-select__native {
  width: 100%;
  height: 36px;
  padding: 0 30px 0 12px;
  border: 1px solid var(--sorts-border);
  border-radius: var(--sorts-radius-ctl);
  background: var(--sorts-surface);
  color: var(--sorts-text);
  font-size: var(--sorts-text-sm);
  appearance: none;
  cursor: pointer;
}
.s-select__native:focus { outline: none; border-color: var(--sorts-color-arrow-500); box-shadow: 0 0 0 2px var(--sorts-color-arrow-100); }
.s-select__arrow {
  position: absolute;
  right: 10px;
  top: 50%;
  transform: translateY(-50%);
  color: var(--sorts-text-faint);
  pointer-events: none;
}
</style>
