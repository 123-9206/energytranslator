import request from './request'

export const energyAPI = {
  getRecords(params = {}) {
    return request.get('/energy/records', { params })
  },
  addRecord(data) {
    return request.post('/energy/records', data)
  },
  getTodayRecords() {
    return request.get('/energy/records/today')
  },
  // Analysis endpoints
  getTrend(params = {}) {
    return request.get('/analysis/trend', { params })
  },
  getRanking(params = {}) {
    return request.get('/analysis/ranking', { params })
  },
  getSummary(params = {}) {
    return request.get('/analysis/summary', { params })
  },
  getDetail(params = {}) {
    return request.get('/analysis/detail', { params })
  },
  // Advanced analytics
  getComprehensiveReport(params = {}) {
    return request.get('/analytics/comprehensive-report', { params })
  },
  getPeakLoad(params = {}) {
    return request.get('/analytics/peak-load', { params })
  },
  getDeviceEfficiency(params = {}) {
    return request.get('/analytics/device-efficiency', { params })
  },
  getConsumptionTrend(params = {}) {
    return request.get('/analytics/consumption-trend', { params })
  },
}
