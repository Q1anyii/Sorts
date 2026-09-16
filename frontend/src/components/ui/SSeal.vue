<script setup lang="ts">
import { computed } from 'vue'
import type { SealForm } from '@/utils/status'

/** 印章徽记（§九）：缃金实心 / 箭青描边 / 朱砂实心；breath 仅用于缃金印 */
const props = withDefaults(
  defineProps<{ form: Exclude<SealForm, null>; text: string; breath?: boolean; size?: number }>(),
  { breath: false, size: 44 }
)

const cls = computed(() => [`s-seal--${props.form}`, { 'seal-breath': props.breath && props.form === 'solid-gold' }])
</script>

<template>
  <span class="s-seal" :class="cls" :style="{ width: `${size}px`, height: `${size}px` }">
    <span class="s-seal__text" :style="{ fontSize: `${Math.round(size * 0.26)}px` }">{{ text }}</span>
  </span>
</template>

<style scoped>
.s-seal {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--sorts-radius-seal);
  flex: none;
  user-select: none;
}
.s-seal__text {
  font-family: var(--sorts-font-serif);
  font-weight: 600;
  line-height: 1.1;
  text-align: center;
  letter-spacing: .05em;
  writing-mode: vertical-rl;
}
.s-seal--solid-gold { background: var(--sorts-color-gold-500); color: var(--sorts-color-ink-800); }
.s-seal--outline-arrow { border: 1.5px solid var(--sorts-color-arrow-600); color: var(--sorts-color-arrow-700); background: transparent; }
.s-seal--solid-late { background: var(--sorts-color-late-600); color: #fff; }
</style>
