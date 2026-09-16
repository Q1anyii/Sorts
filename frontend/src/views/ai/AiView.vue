<script setup lang="ts">
import { ref } from 'vue'
import STabs from '@/components/ui/STabs.vue'
import AiChat from './AiChat.vue'
import AiPlan from './AiPlan.vue'
import AiReports from './AiReports.vue'

/** AI 织师：对话 / 规划 / 梭影报告 三合一 */
const tab = ref('chat')
</script>

<template>
  <div class="ai">
    <STabs
      v-model="tab"
      :items="[
        { value: 'chat', label: '对话' },
        { value: 'plan', label: '规划' },
        { value: 'reports', label: '梭影报告' }
      ]"
    />
    <keep-alive>
      <AiChat v-if="tab === 'chat'" />
      <AiPlan v-else-if="tab === 'plan'" />
      <AiReports v-else />
    </keep-alive>
  </div>
</template>

<style scoped>
.ai { display: flex; flex-direction: column; gap: var(--sorts-space-4); max-width: 880px; }
</style>
