<script setup lang="ts">
/** 开关：滑块只走 transform */
defineProps<{ modelValue: boolean; disabled?: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [v: boolean] }>()
</script>

<template>
  <button
    type="button"
    class="s-switch"
    :class="{ 'is-on': modelValue }"
    :disabled="disabled"
    role="switch"
    :aria-checked="modelValue"
    @click="emit('update:modelValue', !modelValue)"
  >
    <span class="s-switch__thumb" />
  </button>
</template>

<style scoped>
.s-switch {
  width: 40px;
  height: 22px;
  border: none;
  border-radius: 11px;
  background: var(--sorts-color-line-300);
  cursor: pointer;
  padding: 2px;
  transition: background-color var(--sorts-dur-fast) var(--sorts-ease-out);
}
.s-switch.is-on { background: var(--sorts-color-arrow-600); }
.s-switch__thumb {
  display: block;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: #fff;
  transition: transform var(--sorts-dur-fast) var(--sorts-ease-out);
}
.is-on .s-switch__thumb { transform: translateX(18px); }
.s-switch:disabled { opacity: .5; cursor: not-allowed; }
</style>
