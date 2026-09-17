// 主版前端（legacy 模块化入口）：模板见 index.html #app，逻辑见 legacy/app-logic.ts
import './legacy/style.css'
import './legacy/github.min.css'
import app from './legacy/app-logic'
app.mount('#app')
