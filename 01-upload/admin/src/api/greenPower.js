import request from './request'

export const greenPowerAPI = {
  // ========== 企业侧 ==========
  getMyContracts(params = {}) {
    return request.get('/green-power/my', { params })
  },
  getSummary() {
    return request.get('/green-power/summary')
  },

  // ========== 平台侧（admin） ==========
  getAllContracts(params = {}) {
    return request.get('/green-power', { params })
  },
  getContract(id) {
    return request.get(`/green-power/${id}`)
  },
  createContract(data) {
    return request.post('/green-power', data)
  },
  updateContract(id, data) {
    return request.put(`/green-power/${id}`, data)
  },
  deleteContract(id) {
    return request.delete(`/green-power/${id}`)
  },
  getPlatformSummary() {
    return request.get('/green-power/platform-summary')
  },
}
