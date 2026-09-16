/// <reference types="vite/client" />

interface ImportMetaEnv {
  /**
   * API 基地址。留空时前端走同源相对路径 `/api/v1`
   * —— dev 由 Vite 代理到 localhost:8080，容器由 Nginx 反代到 gateway:8080。
   * 需要前后端分离部署（独立域名 / CDN）时才在构建期注入，例如 https://api.example.com/api/v1。
   */
  readonly VITE_API_BASE?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
