import request from './request'

export const vppSettlementAPI = {
  // ========== 企业侧 ==========
  getMySettlements(params = {}) {
    return request.get('/vpp/settlements/my', { params })
  },
  generateSettlement(period) {
    return request.post('/vpp/settlements/generate', { period })
  },

  // ========== 平台侧（admin） ==========
  getAllSettlements(params = {}) {
    return request.get('/vpp/settlements', { params })
  },
  approveSettlement(id) {
    return request.post(`/vpp/settlements/${id}/approve`)
  },
  paySettlement(id) {
    return request.post(`/vpp/settlements/${id}/pay`)
  },
  deleteSettlement(id) {
    return request.delete(`/vpp/settlements/${id}`)
  },
}
