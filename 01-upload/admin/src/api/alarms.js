import request from './request'

export const alarmAPI = {
  list(params = {}) {
    return request.get('/alarms', { params })
  },
  getById(id) {
    return request.get(`/alarms/${id}`)
  },
  acknowledge(id) {
    return request.put(`/alarms/${id}/acknowledge`)
  },
  resolve(id, solution = '') {
    return request.put(`/alarms/${id}/resolve`, { solution })
  },
  ignore(id) {
    return request.put(`/alarms/${id}/ignore`)
  },
  batchAcknowledge(ids) {
    return request.put('/alarms/batch/acknowledge', { ids })
  },
  statistics() {
    return request.get('/alarms/statistics')
  },
  // Rules
  getRules(params = {}) {
    return request.get('/alarms/rules', { params })
  },
  createRule(data) {
    return request.post('/alarms/rules', data)
  },
  updateRule(id, data) {
    return request.put(`/alarms/rules/${id}`, data)
  },
  deleteRule(id) {
    return request.delete(`/alarms/rules/${id}`)
  },
  toggleRule(id) {
    return request.put(`/alarms/rules/${id}/toggle`)
  },
}
