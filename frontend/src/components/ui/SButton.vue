<script setup lang="ts">
import SSpinner from './SSpinner.vue'

/** 按钮（§十一·控件）：primary=箭青实心 / gold=缃金主行动 / ghost=描边 / danger=朱砂 */
withDefaults(
  defineProps<{
    variant?: 'primary' | 'gold' | 'ghost' | 'danger'
    size?: 'sm' | 'md' | 'lg'
    loading?: boolean
    disabled?: boolean
    type?: 'button' | 'submit'
  }>(),
  { variant: 'primary', size: 'md', loading: false, disabled: false, type: 'button' }
)

const emit = defineEmits<{ click: [e: MouseEvent] }>()

function onClick(e: MouseEvent) {
  emit('click', e)
}
</script>

<template>
  <button
    class="s-btn"
    :class="[`s-btn--${variant}`, `s-btn--${size}`, { 'is-loading': loading }]"
    :type="type"
    :disabled="disabled || loading"
    @click="onClick"
  >
    <SSpinner v-if="loading" :size="14" class="s-btn__spinner" />
    <span class="s-btn__content"><slot /></span>
  </button>
</template>

<style scoped>
.s-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  border: 1px solid transparent;
  border-radius: var(--sorts-radius-ctl);
  font-size: var(--sorts-text-sm);
  font-weight: 500;
  cursor: pointer;
  transition: transform var(--sorts-dur-fast) var(--sorts-ease-out),
    opacity var(--sorts-dur-fast) var(--sorts-ease-out),
    background-color var(--sorts-dur-fast) var(--sorts-ease-out);
  user-select: none;
}
.s-btn:active:not(:disabled) { transform: scale(.97); }
.s-btn:disabled { opacity: .5; cursor: not-allowed; }

.s-btn--sm { height: 28px; padding: 0 10px; }
.s-btn--md { height: 34px; padding: 0 14px; }
.s-btn--lg { height: 42px; padding: 0 20px; font-size: var(--sorts-text-base); }

.s-btn--primary { background: var(--sorts-color-arrow-600); color: #fff; }
.s-btn--primary:hover:not(:disabled) { background: var(--sorts-color-arrow-700); }
.s-btn--gold { background: var(--sorts-color-gold-500); color: var(--sorts-color-ink-800); }
.s-btn--gold:hover:not(:disabled) { background: var(--sorts-color-gold-600); }
.s-btn--ghost { background: transparent; border-color: var(--sorts-border); color: var(--sorts-text); }
.s-btn--ghost:hover:not(:disabled) { border-color: var(--sorts-color-arrow-500); color: var(--sorts-color-arrow-600); }
.s-btn--danger { background: var(--sorts-color-late-600); color: #fff; }
.s-btn--danger:hover:not(:disabled) { background: var(--sorts-color-late-700); }

.is-loading .s-btn__content { opacity: .7; }
</style>
