<script setup lang="ts">
import { nextTick, ref } from 'vue'
import { chatStream } from '@/api/ai'
import type { SuggestedAction } from '@/types'
import type { SseSession } from '@/api/sse'
import { ApiError } from '@/api/error'
import SButton from '@/components/ui/SButton.vue'
import SSwitch from '@/components/ui/SSwitch.vue'
import SModal from '@/components/ui/SModal.vue'
import SIcon from '@/components/ui/SIcon.vue'

interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
  streaming?: boolean
  failed?: boolean
  actions?: SuggestedAction[] | null
}

/**
 * 对话助手（SSE 流式）。
 * 关键点：
 * - /ai/chat 默认 SSE；增量事件 delta 逐字上屏，done 带 conversationId 续上下文
 * - 双钥匙写入：allowWrite 开关打开前必须经用户确认（SModal）
 * - 生成中可中止（abort 静默结束，不当错误处理）
 */
const messages = ref<ChatMessage[]>([])
const input = ref('')
const conversationId = ref<string | undefined>()
const streaming = ref(false)
const allowWrite = ref(false)
const confirmOpen = ref(false)
const listRef = ref<HTMLElement | null>(null)

let session: SseSession | null = null

async function scrollToBottom() {
  await nextTick()
  listRef.value?.scrollTo({ top: listRef.value.scrollHeight })
}

function send(text?: string, write = allowWrite.value) {
  const content = (text ?? input.value).trim()
  if (!content || streaming.value) return
  input.value = ''

  messages.value.push({ role: 'user', content })
  const assistant: ChatMessage = { role: 'assistant', content: '', streaming: true }
  messages.value.push(assistant)
  streaming.value = true
  scrollToBottom()

  session = chatStream(
    { message: content, conversationId: conversationId.value, allowWrite: write },
    {
      onDelta: (c) => {
        assistant.content += c
        scrollToBottom()
      },
      onDone: (res) => {
        assistant.streaming = false
        // done 载荷的完整回复优先（防 delta 丢帧造成的缺字）
        if (res.reply) assistant.content = res.reply
        assistant.actions = res.suggestedActions
        conversationId.value = res.conversationId
        streaming.value = false
        scrollToBottom()
      },
      onError: (e: ApiError) => {
        assistant.streaming = false
        assistant.failed = true
        assistant.content = assistant.content || `织师暂不可用：${e.message}`
        streaming.value = false
      }
    }
  )
  session.finished.finally(() => {
    streaming.value = false
  })
}

function stop() {
  session?.abort()
  streaming.value = false
  const last = messages.value[messages.value.length - 1]
  if (last?.streaming) last.streaming = false
}

/** 点击建议动作：写操作先确认，再以动作说明作为新消息发出 */
function runAction(action: SuggestedAction) {
  if (action.type !== 'READ') {
    allowWrite.value = true
    send(`请执行：${action.label}`, true)
  } else {
    send(action.label)
  }
}

function onToggleAllowWrite(v: boolean) {
  if (v) {
    confirmOpen.value = true
  } else {
    allowWrite.value = false
  }
}

function confirmAllowWrite() {
  allowWrite.value = true
  confirmOpen.value = false
}
</script>

<template>
  <div class="chat">
    <div ref="listRef" class="chat__list">
      <div v-if="!messages.length" class="chat__welcome">
        <p class="chat__welcome-title">AI 织师在此</p>
        <p class="chat__welcome-hint">问排程、问总结、问怎么织更顺手，都可以。</p>
      </div>

      <div v-for="(m, i) in messages" :key="i" class="chat__msg" :class="`chat__msg--${m.role}`">
        <div class="chat__bubble" :class="{ 'is-failed': m.failed }">
          <span class="chat__text">{{ m.content }}</span>
          <span v-if="m.streaming" class="chat__cursor" />
        </div>
        <div v-if="m.actions?.length" class="chat__actions">
          <button
            v-for="(a, j) in m.actions"
            :key="j"
            class="chat__action"
            @click="runAction(a)"
          >
            {{ a.label }}
          </button>
        </div>
      </div>
    </div>

    <div class="chat__composer">
      <label class="chat__write-toggle" title="允许 AI 代为创建/修改日程">
        <SSwitch :model-value="allowWrite" @update:model-value="onToggleAllowWrite" />
        <span>允许代织</span>
      </label>
      <textarea
        v-model="input"
        class="chat__input"
        rows="2"
        placeholder="和织师说点什么…（Enter 发送，Shift+Enter 换行）"
        @keydown.enter.exact.prevent="send()"
      />
      <SButton v-if="streaming" variant="ghost" @click="stop">
        <SIcon name="stop" :size="13" /> 停止
      </SButton>
      <SButton v-else variant="gold" :disabled="!input.trim()" @click="send()">发送</SButton>
    </div>

    <!-- 双钥匙确认 -->
    <SModal :open="confirmOpen" title="允许 AI 代为织入" :width="420" @close="confirmOpen = false">
      <p class="chat__confirm-text">
        打开后，织师可以代为创建或修改你的日程。服务端还有第二道开关把关，但请知悉：每一次写入都来自你的指令。
      </p>
      <template #footer>
        <SButton variant="ghost" @click="confirmOpen = false">先不</SButton>
        <SButton variant="gold" @click="confirmAllowWrite">确认允许</SButton>
      </template>
    </SModal>
  </div>
</template>

<style scoped>
.chat { display: flex; flex-direction: column; gap: var(--sorts-space-3); }
.chat__list {
  min-height: 320px;
  max-height: 56vh;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: var(--sorts-space-3);
  padding: var(--sorts-space-4);
  background: var(--sorts-surface);
  border: 1px solid var(--sorts-border);
  border-radius: var(--sorts-radius-box);
}
.chat__welcome { margin: auto; text-align: center; }
.chat__welcome-title { font-family: var(--sorts-font-serif); font-size: var(--sorts-text-xl); color: var(--sorts-ink); margin: 0 0 8px; }
.chat__welcome-hint { color: var(--sorts-text-faint); font-size: var(--sorts-text-sm); margin: 0; }
.chat__msg { display: flex; flex-direction: column; gap: 6px; }
.chat__msg--user { align-items: flex-end; }
.chat__msg--assistant { align-items: flex-start; }
.chat__bubble {
  max-width: 78%;
  padding: 10px 14px;
  border-radius: var(--sorts-radius-box);
  font-size: var(--sorts-text-sm);
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}
.chat__msg--user .chat__bubble { background: var(--sorts-color-arrow-600); color: #fff; border-bottom-right-radius: 4px; }
.chat__msg--assistant .chat__bubble { background: var(--sorts-color-line-100); color: var(--sorts-text); border-bottom-left-radius: 4px; }
.chat__bubble.is-failed { background: var(--sorts-color-late-100); color: var(--sorts-color-late-700); }
.chat__cursor {
  display: inline-block;
  width: 8px;
  height: 14px;
  margin-left: 2px;
  vertical-align: text-bottom;
  background: currentColor;
  opacity: .7;
  animation: chat-blink 1s steps(2) infinite;
}
@keyframes chat-blink { 50% { opacity: 0; } }
.chat__actions { display: flex; gap: 6px; flex-wrap: wrap; }
.chat__action {
  border: 1px solid var(--sorts-color-gold-500);
  background: var(--sorts-color-gold-100);
  color: var(--sorts-color-gold-800);
  font-size: var(--sorts-text-xs);
  padding: 4px 10px;
  border-radius: var(--sorts-radius-ctl);
  cursor: pointer;
}
.chat__action:hover { background: var(--sorts-color-gold-200); }
.chat__composer { display: flex; align-items: flex-end; gap: var(--sorts-space-2); }
.chat__write-toggle {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: var(--sorts-text-faint);
  cursor: pointer;
  padding-bottom: 4px;
}
.chat__input {
  flex: 1;
  resize: none;
  border: 1px solid var(--sorts-border);
  border-radius: var(--sorts-radius-ctl);
  padding: 8px 12px;
  font-size: var(--sorts-text-sm);
  font-family: inherit;
  background: var(--sorts-surface);
  color: var(--sorts-text);
}
.chat__input:focus { outline: none; border-color: var(--sorts-color-arrow-500); box-shadow: 0 0 0 2px var(--sorts-color-arrow-100); }
.chat__confirm-text { margin: 0; color: var(--sorts-text); line-height: 1.7; }
@media (prefers-reduced-motion: reduce) {
  .chat__cursor { animation: none; }
}
</style>
