<script setup lang="ts">
import { computed } from 'vue'

/**
 * 图标体系（docs/theme-design.md §十二）：24×24、2px 描边、currentColor。
 * 全部为静态可信内容，v-html 仅注入本文件内置的 path 片段。
 */
const ICONS: Record<string, string> = {
  home: '<path d="M3 10.5 12 3l9 7.5"/><path d="M5 9.5V21h14V9.5"/>',
  calendar: '<rect x="3" y="4" width="18" height="18" rx="2"/><path d="M16 2v4M8 2v4M3 10h18"/>',
  list: '<path d="M8 6h13M8 12h13M8 18h13"/><path d="M3.5 6h.01M3.5 12h.01M3.5 18h.01"/>',
  timer: '<circle cx="12" cy="13" r="8"/><path d="M12 9.5v3.5l2.5 2.5M9 2h6"/>',
  chart: '<path d="M3 3v18h18"/><path d="M18 17V9M13 17V5M8 17v-3"/>',
  ai: '<path d="M12 3l1.9 5.8 5.8 1.9-5.8 1.9L12 18.4l-1.9-5.8-5.8-1.9 5.8-1.9z"/><path d="M19 3v4M17 5h4"/>',
  shop: '<path d="M3 9l1.5-5h15L21 9"/><path d="M3 9v11a1 1 0 0 0 1 1h16a1 1 0 0 0 1-1V9"/><path d="M3 9h18M9 21v-8h6v8"/>',
  wardrobe: '<rect x="4" y="3" width="16" height="18" rx="1"/><path d="M12 3v18"/><path d="M9 8v3M15 8v3"/>',
  bell: '<path d="M18 8a6 6 0 1 0-12 0c0 7-3 9-3 9h18s-3-2-3-9"/><path d="M10.3 21a1.94 1.94 0 0 0 3.4 0"/>',
  gear: '<circle cx="12" cy="12" r="3"/><path d="M12 2v3M12 19v3M2 12h3M19 12h3M4.9 4.9l2.1 2.1M17 17l2.1 2.1M19.1 4.9 17 7M7 17l-2.1 2.1"/>',
  plus: '<path d="M12 5v14M5 12h14"/>',
  play: '<path d="M7 4.5l13 7.5-13 7.5z" fill="currentColor" stroke="none"/>',
  pause: '<path d="M6.5 4h3.5v16H6.5zM14 4h3.5v16H14z" fill="currentColor" stroke="none"/>',
  stop: '<rect x="6" y="6" width="12" height="12" rx="1" fill="currentColor" stroke="none"/>',
  check: '<path d="M4 12.5l5 5L20 6.5"/>',
  close: '<path d="M6 6l12 12M18 6L6 18"/>',
  'chevron-left': '<path d="M15 18l-6-6 6-6"/>',
  'chevron-right': '<path d="M9 6l6 6-6 6"/>',
  'chevron-down': '<path d="M6 9l6 6 6-6"/>',
  search: '<circle cx="11" cy="11" r="7"/><path d="M21 21l-4.3-4.3"/>',
  logout: '<path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><path d="M16 17l5-5-5-5M21 12H9"/>',
  moon: '<path d="M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z"/>',
  sun: '<circle cx="12" cy="12" r="4"/><path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4"/>',
  user: '<circle cx="12" cy="8" r="4"/><path d="M4 21c0-4 3.6-6 8-6s8 2 8 6"/>'
}

export type SIconName = keyof typeof ICONS

const props = withDefaults(defineProps<{ name: string; size?: number }>(), { size: 20 })

const markup = computed(() => ICONS[props.name] ?? ICONS.close)
</script>

<template>
  <svg
    class="s-icon"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    :stroke-width="2"
    stroke-linecap="round"
    stroke-linejoin="round"
    :width="size"
    :height="size"
    aria-hidden="true"
    v-html="markup"
  />
</template>

<style scoped>
.s-icon {
  display: inline-block;
  vertical-align: middle;
  flex: none;
}
</style>
