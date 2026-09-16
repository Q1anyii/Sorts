import { defineStore } from 'pinia'

export type ThemeMode = 'day' | 'night'

export interface Toast {
  id: number
  kind: 'info' | 'success' | 'error'
  text: string
}

const THEME_KEY = 'sorts-theme'
let toastSeq = 0

/** 全局外观与轻提示 */
export const useAppStore = defineStore('app', {
  state: () => ({
    theme: (localStorage.getItem(THEME_KEY) as ThemeMode | null) ?? 'day',
    sidebarCollapsed: false,
    toasts: [] as Toast[]
  }),
  actions: {
    initTheme(): void {
      this.applyTheme(this.theme)
    },
    toggleTheme(): void {
      this.applyTheme(this.theme === 'day' ? 'night' : 'day')
    },
    applyTheme(mode: ThemeMode): void {
      this.theme = mode
      document.documentElement.dataset.theme = mode
      localStorage.setItem(THEME_KEY, mode)
    },
    toggleSidebar(): void {
      this.sidebarCollapsed = !this.sidebarCollapsed
    },
    toast(text: string, kind: Toast['kind'] = 'info', duration = 2600): void {
      const id = ++toastSeq
      this.toasts.push({ id, kind, text })
      window.setTimeout(() => this.dismissToast(id), duration)
    },
    dismissToast(id: number): void {
      this.toasts = this.toasts.filter((t) => t.id !== id)
    }
  }
})
