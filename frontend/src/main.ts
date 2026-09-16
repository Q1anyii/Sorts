import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import { router } from './router'
import { onSessionExpired } from './api/http'
import { tokenStore } from './api/token'
import './styles/index.css'

const app = createApp(App)

app.use(createPinia())
app.use(router)

// 会话彻底失效（refresh 也被拒）：清令牌 → 回登录页并记住来路
onSessionExpired(() => {
  tokenStore.clear()
  const current = router.currentRoute.value
  if (current.name !== 'login') {
    router.push({ name: 'login', query: { redirect: current.fullPath } })
  }
})

app.mount('#app')
