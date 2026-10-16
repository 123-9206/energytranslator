import request from './request'

export const policyAPI = {
  // ========== 企业侧 ==========
  getAvailablePolicies(params = {}) {
    return request.get('/policies/available', { params })
  },
  getPolicySummary() {
    return request.get('/policies/summary')
  },

  // ========== 平台侧（admin） ==========
  getPolicies(params = {}) {
    return request.get('/policies', { params })
  },
  createPolicy(data) {
    return request.post('/policies', data)
  },
  updatePolicy(id, data) {
    return request.put(`/policies/${id}`, data)
  },
  deletePolicy(id) {
    return request.delete(`/policies/${id}`)
  },
}
