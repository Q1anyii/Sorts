<script setup lang="ts">
import { watch } from 'vue'
import SIcon from './SIcon.vue'

/** 模态：teleport 到 body；打开时锁滚动；Esc 关闭 */
const props = withDefaults(defineProps<{ open: boolean; title?: string; width?: number }>(), {
  title: '',
  width: 480
})

const emit = defineEmits<{ close: [] }>()

watch(
  () => props.open,
  (v) => {
    document.body.style.overflow = v ? 'hidden' : ''
  }
)

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape') emit('close')
}
</script>

<template>
  <teleport to="body">
    <transition name="shuttle">
      <div v-if="open" class="s-modal__mask" @click.self="emit('close')" @keydown="onKeydown">
        <div class="s-modal seal-stamp" :style="{ maxWidth: `${width}px` }" role="dialog" aria-modal="true">
          <header class="s-modal__head">
            <h3 class="s-modal__title">{{ title }}</h3>
            <button class="s-modal__close" aria-label="关闭" @click="emit('close')">
              <SIcon name="close" :size="16" />
            </button>
          </header>
          <div class="s-modal__body"><slot /></div>
          <footer v-if="$slots.footer" class="s-modal__foot"><slot name="footer" /></footer>
        </div>
      </div>
    </transition>
  </teleport>
</template>

<style scoped>
.s-modal__mask {
  position: fixed;
  inset: 0;
  z-index: 900;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--sorts-space-4);
  background: rgba(27, 42, 65, .42);
}
.s-modal {
  width: 100%;
  background: var(--sorts-surface);
  border: 1px solid var(--sorts-border);
  border-radius: var(--sorts-radius-box);
  box-shadow: var(--sorts-shadow-3);
  display: flex;
  flex-direction: column;
  max-height: 85vh;
}
.s-modal__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--sorts-space-4);
  border-bottom: 1px solid var(--sorts-border);
}
.s-modal__title { font-size: var(--sorts-text-lg); color: var(--sorts-ink); }
.s-modal__close {
  border: none;
  background: none;
  color: var(--sorts-text-faint);
  cursor: pointer;
  padding: 4px;
  border-radius: var(--sorts-radius-ctl);
}
.s-modal__close:hover { color: var(--sorts-text); background: var(--sorts-color-line-100); }
.s-modal__body { padding: var(--sorts-space-4); overflow-y: auto; }
.s-modal__foot {
  padding: var(--sorts-space-3) var(--sorts-space-4);
  border-top: 1px solid var(--sorts-border);
  display: flex;
  justify-content: flex-end;
  gap: var(--sorts-space-2);
}
</style>
