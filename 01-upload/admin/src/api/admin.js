import request from './request'

export const adminAPI = {
  // ========== 用户管理 ==========
  getUsers(params = {}) {
    return request.get('/admin/users', { params })
  },
  getUserDetail(id) {
    return request.get(`/admin/users/${id}`)
  },
  updateUser(id, data) {
    return request.put(`/admin/users/${id}`, data)
  },
  deleteUser(id) {
    return request.delete(`/admin/users/${id}`)
  },

  // ========== 系统统计 ==========
  getStatistics() {
    return request.get('/admin/statistics')
  },

  // ========== 全局设备 ==========
  getAllDevices(params = {}) {
    return request.get('/admin/devices', { params })
  },

  // ========== 全局能耗 ==========
  getAllEnergy(params = {}) {
    return request.get('/admin/energy', { params })
  },

  // ========== 系统健康 ==========
  getSystemHealth() {
    return request.get('/admin/system/health')
  },

  // ========== 企业列表 ==========
  getEnterprises(params = {}) {
    return request.get('/admin/enterprises', { params })
  },
  // ========== 企业审核 ==========
  approveEnterprise(id) {
    return request.post(`/admin/enterprises/${id}/approve`)
  },
  rejectEnterprise(id) {
    return request.post(`/admin/enterprises/${id}/reject`)
  },

  // ========== 系统设置 ==========
  getSettings() {
    return request.get('/admin/settings')
  },
  saveSettings(data) {
    return request.put('/admin/settings', data)
  },

  // ========== AI 智能分析 ==========
  aiAnalyze(text) {
    return request.post('/admin/ai/analyze', { text })
  },
  // 驾驶舱 AI 聚合分析：诊断摘要 + 未来7天预测 + 24h功率曲线 + 采集状态
  getAIAnalysis(params = {}) {
    return request.get('/admin/ai/analysis', { params })
  },

  // ========== 设备控制 ==========
  getControlStats() {
    return request.get('/control/stats')
  },
  getAllControlRules() {
    return request.get('/control/admin/rules')
  },
  triggerControlRule(ruleId) {
    return request.post(`/control/rules/${ruleId}/trigger`)
  },
  getControlLogs(params = {}) {
    return request.get('/control/logs', { params })
  },

  // ========== 数据管理 ==========
  cleanupOldData(daysBefore = 90) {
    return request.delete('/admin/data/cleanup', { params: { daysBefore } })
  },

  // ========== 审计日志 ==========
  getAuditLogs(params = {}) {
    return request.get('/admin/audit-logs', { params })
  },
}
