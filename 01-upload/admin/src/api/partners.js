import request from './request'

export const partnersAPI = {
  list(params = {}) {
    return request.get('/partners', { params })
  },
  create(data) {
    return request.post('/partners', data)
  },
  getRevenueShares(params = {}) {
    return request.get('/partners/revenue-shares', { params })
  },
  calculateRevenueShare(params = {}) {
    return request.post('/partners/revenue-shares/calculate', null, { params })
  },
}
