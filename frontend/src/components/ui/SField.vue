<script setup lang="ts">
/** 表单项包装：label + 控件 + 错误/提示 */
defineProps<{ label?: string; error?: string | null; hint?: string; required?: boolean }>()
</script>

<template>
  <label class="s-field">
    <span v-if="label" class="s-field__label">
      {{ label }}<em v-if="required" class="s-field__req">*</em>
    </span>
    <slot />
    <span v-if="error" class="s-field__error">{{ error }}</span>
    <span v-else-if="hint" class="s-field__hint">{{ hint }}</span>
  </label>
</template>

<style scoped>
.s-field { display: flex; flex-direction: column; gap: 6px; }
.s-field__label { font-size: var(--sorts-text-sm); color: var(--sorts-text); font-weight: 500; }
.s-field__req { color: var(--sorts-color-late-600); font-style: normal; margin-left: 2px; }
.s-field__error { font-size: var(--sorts-text-xs); color: var(--sorts-color-late-600); }
.s-field__hint { font-size: var(--sorts-text-xs); color: var(--sorts-text-faint); }
</style>
