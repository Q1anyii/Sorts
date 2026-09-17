<script setup lang="ts">
import { computed } from 'vue'

/** 红点角标：count>0 显示数字（99+ 封顶），dot 模式只显示点 */
const props = withDefaults(defineProps<{ count?: number; dot?: boolean }>(), { count: 0, dot: false })

const visible = computed(() => (props.dot ? props.count > 0 : props.count > 0))
const text = computed(() => (props.count > 99 ? '99+' : String(props.count)))
</script>

<template>
  <span v-if="visible" class="s-badge" :class="{ 's-badge--dot': dot }">{{ dot ? '' : text }}</span>
</template>

<style scoped>
.s-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  border-radius: 8px;
  background: var(--sorts-color-late-600);
  color: #fff;
  font-size: 10px;
  line-height: 1;
}
.s-badge--dot {
  min-width: 8px;
  width: 8px;
  height: 8px;
  padding: 0;
}
</style>
