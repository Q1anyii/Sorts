<script setup lang="ts">
import { ref } from 'vue'
import { adoptPlan, generatePlanStream } from '@/api/ai'
import type { AIPlanResponse } from '@/types'
import type { SseSession } from '@/api/sse'
import { ApiError } from '@/api/error'
import { useAppStore } from '@/stores/app'
import { PRIORITY_META } from '@/utils/status'
import { todayStr } from '@/utils/datetime'
import SButton from '@/components/ui/SButton.vue'
import SInput from '@/components/ui/SInput.vue'
import SField from '@/components/ui/SField.vue'
import SCard from '@/components/ui/SCard.vue'
import STag from '@/components/ui/STag.vue'
import SEmpty from '@/components/ui/SEmpty.vue'

/**
 * 日程规划：流式生成（/ai/plan?stream=true）→ 勾选建议 → 采纳落库。
 * 采纳 = 写操作：由用户勾选并点击「采纳织入」显式触发。
 */
const app = useAppStore()

const prompt = ref('')
const targetDate = ref(todayStr())
const thinking = ref('')
const generating = ref(false)
const plan = ref<AIPlanResponse | null>(null)
const selected = ref<Set<number>>(new Set())
const adopting = ref(false)

let session: SseSession | null = null

function generate() {
  if (!prompt.value.trim() || generating.value) return
  thinking.value = ''
  plan.value = null
  selected.value = new Set()
  generating.value = true

  session = generatePlanStream(
    { userPrompt: prompt.value.trim(), targetDate: targetDate.value || undefined },
    {
      onDelta: (c) => {
        thinking.value += c
      },
      onDone: (res) => {
        plan.value = res
        generating.value = false
        // 默认全选，用户再按需取消
        selected.value = new Set(res.suggestions.map((_, i) => i))
      },
      onError: (e: ApiError) => {
        generating.value = false
        app.toast(`规划失败：${e.message}`, 'error')
      }
    }
  )
  session.finished.finally(() => {
    generating.value = false
  })
}

function toggle(i: number) {
  const next = new Set(selected.value)
  if (next.has(i)) next.delete(i)
  else next.add(i)
  selected.value = next
}

async function adopt() {
  if (!plan.value || !selected.value.size || adopting.value) return
  adopting.value = true
  try {
    const created = await adoptPlan(plan.value.planId, {
      selectedIndices: [...selected.value].sort((a, b) => a - b)
    })
    app.toast(`已织入 ${created.length} 条日程`, 'success')
    plan.value = null
    prompt.value = ''
    thinking.value = ''
  } catch (e) {
    app.toast(e instanceof ApiError ? e.message : '采纳失败', 'error')
  } finally {
    adopting.value = false
  }
}
</script>

<template>
  <div class="plan">
    <SCard class="plan__form">
      <SField label="想怎么安排？" hint="例如：明天上午写方案两小时，下午开会，晚上复盘">
        <textarea
          v-model="prompt"
          class="plan__prompt"
          rows="3"
          placeholder="用自然语言描述你的安排…"
        />
      </SField>
      <div class="plan__form-row">
        <SField label="目标日期" class="plan__date">
          <SInput v-model="targetDate" type="date" />
        </SField>
        <SButton variant="gold" :loading="generating" :disabled="!prompt.trim()" @click="generate">
          {{ generating ? '梭灵思考中…' : '生成规划' }}
        </SButton>
      </div>
    </SCard>

    <!-- 流式思考过程 -->
    <SCard v-if="generating && thinking" class="plan__thinking">
      <p class="plan__thinking-text">{{ thinking }}</p>
    </SCard>

    <!-- 建议清单 -->
    <template v-if="plan">
      <p class="plan__hint">勾选要采纳的建议（已选 {{ selected.size }} / {{ plan.suggestions.length }}）</p>
      <div class="plan__suggestions">
        <SCard
          v-for="(s, i) in plan.suggestions"
          :key="i"
          class="plan__suggestion weave-in"
          :class="{ 'is-selected': selected.has(i) }"
          @click="toggle(i)"
        >
          <div class="plan__suggestion-head">
            <input type="checkbox" :checked="selected.has(i)" @click.stop="toggle(i)" />
            <h4 class="plan__suggestion-title">{{ s.title }}</h4>
            <STag :color="PRIORITY_META[s.priority].color">{{ PRIORITY_META[s.priority].label }}</STag>
          </div>
          <p class="plan__suggestion-meta num">{{ s.suggestedStart }} · {{ s.duration }} 分钟</p>
          <p v-if="s.description" class="plan__suggestion-desc">{{ s.description }}</p>
          <p v-if="s.reason" class="plan__suggestion-reason">梭灵按：{{ s.reason }}</p>
          <div v-if="s.tags.length" class="plan__suggestion-tags">
            <STag v-for="t in s.tags" :key="t">#{{ t }}</STag>
          </div>
        </SCard>
      </div>
      <div class="plan__adopt-bar">
        <SButton variant="gold" size="lg" :loading="adopting" :disabled="!selected.size" @click="adopt">
          采纳织入（{{ selected.size }}）
        </SButton>
      </div>
    </template>

    <SEmpty
      v-else-if="!generating"
      text="还没有规划"
      hint="描述你的安排，梭灵会给出带理由的建议"
      icon="ai"
    />
  </div>
</template>

<style scoped>
.plan { display: flex; flex-direction: column; gap: var(--sorts-space-4); }
.plan__form { display: flex; flex-direction: column; gap: var(--sorts-space-3); }
.plan__prompt {
  width: 100%;
  resize: vertical;
  border: 1px solid var(--sorts-border);
  border-radius: var(--sorts-radius-ctl);
  padding: 8px 12px;
  font-size: var(--sorts-text-sm);
  font-family: inherit;
  background: var(--sorts-surface);
  color: var(--sorts-text);
}
.plan__prompt:focus { outline: none; border-color: var(--sorts-color-arrow-500); box-shadow: 0 0 0 2px var(--sorts-color-arrow-100); }
.plan__form-row { display: flex; align-items: flex-end; gap: var(--sorts-space-3); }
.plan__date { width: 170px; }
.plan__thinking-text { margin: 0; font-size: var(--sorts-text-sm); color: var(--sorts-text-faint); white-space: pre-wrap; }
.plan__hint { margin: 0; font-size: var(--sorts-text-sm); color: var(--sorts-text-faint); }
.plan__suggestions { display: flex; flex-direction: column; gap: var(--sorts-space-2); }
.plan__suggestion { cursor: pointer; transition: border-color var(--sorts-dur-fast) var(--sorts-ease-out); }
.plan__suggestion.is-selected { border-color: var(--sorts-color-gold-500); }
.plan__suggestion-head { display: flex; align-items: center; gap: 8px; }
.plan__suggestion-title { margin: 0; flex: 1; font-size: var(--sorts-text-base); color: var(--sorts-ink); }
.plan__suggestion-meta { margin: 6px 0 0; font-size: var(--sorts-text-xs); color: var(--sorts-text-faint); }
.plan__suggestion-desc { margin: 6px 0 0; font-size: var(--sorts-text-sm); color: var(--sorts-text); }
.plan__suggestion-reason { margin: 6px 0 0; font-size: var(--sorts-text-xs); color: var(--sorts-color-gold-800); }
.plan__suggestion-tags { display: flex; gap: 6px; margin-top: 8px; flex-wrap: wrap; }
.plan__adopt-bar { display: flex; justify-content: flex-end; }
</style>
