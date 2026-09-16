<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { ApiError } from '@/api/error'
import SButton from '@/components/ui/SButton.vue'
import SInput from '@/components/ui/SInput.vue'
import SField from '@/components/ui/SField.vue'
import STabs from '@/components/ui/STabs.vue'

/** 入梭：登录 / 注册一体页（纸张底 + 冰裂纹理，详见 §五/§八） */
const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const mode = ref<'login' | 'register'>('login')
const loading = ref(false)
const errorText = ref('')

const form = reactive({
  username: '',
  password: '',
  confirm: '',
  nickname: '',
  email: ''
})

const fieldErrors = reactive<Record<string, string>>({})

function validate(): boolean {
  fieldErrors.username = form.username.trim() ? '' : '请输入用户名'
  fieldErrors.password = form.password.length >= 6 ? '' : '密码至少 6 位'
  if (mode.value === 'register') {
    fieldErrors.confirm = form.confirm === form.password ? '' : '两次输入的密码不一致'
    fieldErrors.email = !form.email || /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email) ? '' : '邮箱格式不正确'
  }
  return !Object.values(fieldErrors).some(Boolean)
}

async function submit() {
  errorText.value = ''
  if (!validate()) return
  loading.value = true
  try {
    if (mode.value === 'login') {
      await auth.login({ username: form.username.trim(), password: form.password })
    } else {
      await auth.register({
        username: form.username.trim(),
        password: form.password,
        nickname: form.nickname.trim() || undefined,
        email: form.email.trim() || undefined
      })
    }
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
    router.push(redirect)
  } catch (e) {
    errorText.value = e instanceof ApiError ? e.message : '网络异常，请稍后再试'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login crackle-bg">
    <section class="login__card view-in">
      <header class="login__brand">
        <span class="login__seal">梭</span>
        <div>
          <h1 class="login__title">梭子 SORTS</h1>
          <p class="login__slogan">光阴似箭，日月如梭</p>
        </div>
      </header>

      <STabs
        v-model="mode"
        :items="[
          { value: 'login', label: '登录' },
          { value: 'register', label: '注册' }
        ]"
        class="login__tabs"
      />

      <form class="login__form" @submit.prevent="submit">
        <SField label="用户名" :error="fieldErrors.username" required>
          <SInput v-model="form.username" placeholder="请输入用户名" :maxlength="32" @enter="submit" />
        </SField>

        <SField v-if="mode === 'register'" label="昵称">
          <SInput v-model="form.nickname" placeholder="可选，默认为用户名" :maxlength="32" @enter="submit" />
        </SField>

        <SField v-if="mode === 'register'" label="邮箱" :error="fieldErrors.email">
          <SInput v-model="form.email" type="email" placeholder="可选" :maxlength="64" @enter="submit" />
        </SField>

        <SField label="密码" :error="fieldErrors.password" required>
          <SInput v-model="form.password" type="password" placeholder="至少 6 位" :maxlength="64" @enter="submit" />
        </SField>

        <SField v-if="mode === 'register'" label="确认密码" :error="fieldErrors.confirm" required>
          <SInput v-model="form.confirm" type="password" placeholder="再输入一次" :maxlength="64" @enter="submit" />
        </SField>

        <p v-if="errorText" class="login__error" role="alert">{{ errorText }}</p>

        <SButton type="submit" variant="gold" size="lg" :loading="loading" class="login__submit">
          {{ mode === 'login' ? '入 梭' : '织 新 机' }}
        </SButton>
      </form>
    </section>
  </div>
</template>

<style scoped>
.login {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--sorts-space-4);
  background: var(--sorts-bg);
}
.login__card {
  width: 100%;
  max-width: 400px;
  background: var(--sorts-surface);
  border: 1px solid var(--sorts-border);
  border-radius: var(--sorts-radius-box);
  box-shadow: var(--sorts-shadow-2);
  padding: var(--sorts-space-6);
}
.login__brand {
  display: flex;
  align-items: center;
  gap: var(--sorts-space-3);
  margin-bottom: var(--sorts-space-5);
}
.login__seal {
  width: 48px;
  height: 48px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: var(--sorts-color-late-600);
  color: #fff;
  border-radius: var(--sorts-radius-seal);
  font-family: var(--sorts-font-serif);
  font-size: var(--sorts-text-2xl);
  font-weight: 600;
}
.login__title {
  font-family: var(--sorts-font-serif);
  font-size: var(--sorts-text-2xl);
  color: var(--sorts-ink);
}
.login__slogan {
  font-size: var(--sorts-text-sm);
  color: var(--sorts-text-faint);
  letter-spacing: .1em;
}
.login__tabs { margin-bottom: var(--sorts-space-4); }
.login__form { display: flex; flex-direction: column; gap: var(--sorts-space-4); }
.login__error {
  margin: 0;
  font-size: var(--sorts-text-sm);
  color: var(--sorts-color-late-600);
  background: var(--sorts-color-late-100);
  border-radius: var(--sorts-radius-ctl);
  padding: 8px 12px;
}
.login__submit { width: 100%; letter-spacing: .2em; }
</style>
