<script setup lang="ts">
import SIcon from './SIcon.vue'

/** 空态：一支闲搁的梭子 */
withDefaults(defineProps<{ text?: string; hint?: string; icon?: string }>(), {
  text: '这里还空着',
  hint: '',
  icon: 'list'
})
</script>

<template>
  <div class="s-empty">
    <SIcon :name="icon" :size="40" class="s-empty__icon" />
    <p class="s-empty__text">{{ text }}</p>
    <p v-if="hint" class="s-empty__hint">{{ hint }}</p>
    <slot />
  </div>
</template>

<style scoped>
.s-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--sorts-space-2);
  padding: var(--sorts-space-8) var(--sorts-space-4);
  text-align: center;
}
.s-empty__icon { color: var(--sorts-color-line-400); }
.s-empty__text { color: var(--sorts-text); font-size: var(--sorts-text-base); }
.s-empty__hint { color: var(--sorts-text-faint); font-size: var(--sorts-text-sm); }
</style>
