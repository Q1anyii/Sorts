<script setup lang="ts">
/** 输入框：v-model + 错误态 */
withDefaults(
  defineProps<{
    modelValue: string
    type?: string
    placeholder?: string
    error?: string | null
    disabled?: boolean
    maxlength?: number
  }>(),
  { type: 'text', placeholder: '', error: null, disabled: false }
)

const emit = defineEmits<{ 'update:modelValue': [v: string]; enter: [] }>()
</script>

<template>
  <input
    class="s-input"
    :class="{ 's-input--error': error }"
    :type="type"
    :value="modelValue"
    :placeholder="placeholder"
    :disabled="disabled"
    :maxlength="maxlength"
    @input="emit('update:modelValue', ($event.target as HTMLInputElement).value)"
    @keyup.enter="emit('enter')"
  />
</template>

<style scoped>
.s-input {
  width: 100%;
  height: 36px;
  padding: 0 12px;
  border: 1px solid var(--sorts-border);
  border-radius: var(--sorts-radius-ctl);
  background: var(--sorts-surface);
  color: var(--sorts-text);
  font-size: var(--sorts-text-sm);
  transition: border-color var(--sorts-dur-fast) var(--sorts-ease-out);
}
.s-input::placeholder { color: var(--sorts-text-faint); }
.s-input:focus { outline: none; border-color: var(--sorts-color-arrow-500); box-shadow: 0 0 0 2px var(--sorts-color-arrow-100); }
.s-input--error { border-color: var(--sorts-color-late-500); }
.s-input:disabled { opacity: .55; cursor: not-allowed; }
</style>
