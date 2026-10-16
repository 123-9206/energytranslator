import { defineStore } from 'pinia'
import { authAPI } from '../api/auth'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem('admin_token') || '',
    refreshToken: localStorage.getItem('admin_refresh') || '',
    user: JSON.parse(localStorage.getItem('admin_user') || 'null'),
    loading: false,
    error: '',
  }),

  getters: {
    isLoggedIn: (state) => !!state.token,
    username: (state) => state.user?.username || '管理员',
    role: (state) => state.user?.role || 'unknown',
  },

  actions: {
    async login(username, password) {
      this.loading = true
      this.error = ''
      try {
        const res = await authAPI.login(username, password)
        const data = res.data || res
        this.token = data.token || res.token
        this.refreshToken = data.refreshToken || res.refreshToken
        this.user = data.userInfo || { username, role: 'admin' }
        localStorage.setItem('admin_token', this.token)
        localStorage.setItem('admin_refresh', this.refreshToken)
        localStorage.setItem('admin_user', JSON.stringify(this.user))
        return true
      } catch (e) {
        this.error = e.response?.data?.message || e.message || '登录失败'
        return false
      } finally {
        this.loading = false
      }
    },

    logout() {
      this.token = ''
      this.refreshToken = ''
      this.user = null
      localStorage.removeItem('admin_token')
      localStorage.removeItem('admin_refresh')
      localStorage.removeItem('admin_user')
    },

    async refresh() {
      try {
        const res = await authAPI.refresh(this.refreshToken)
        const data = res.data || res
        this.token = data.token
        this.refreshToken = data.refreshToken
        localStorage.setItem('admin_token', this.token)
        localStorage.setItem('admin_refresh', this.refreshToken)
      } catch {
        this.logout()
      }
    },
  },
})
