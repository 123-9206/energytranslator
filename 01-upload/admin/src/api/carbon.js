import request from './request'

export const carbonAPI = {
  getReport(params = {}) {
    return request.get('/carbon/report', { params })
  },
  getTrend(params = {}) {
    return request.get('/carbon/trend', { params })
  },
  getSummary() {
    return request.get('/carbon/summary')
  },
  getHistory(params = {}) {
    return request.get('/carbon/history', { params })
  },
  getAsset() {
    return request.get('/carbon/asset')
  },
  takeSnapshot() {
    return request.post('/carbon/snapshot')
  },
  // Admin: manual aggregate
  aggregate(data) {
    return request.post('/carbon/aggregate', null, { params: data })
  },
}
