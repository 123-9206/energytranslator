import request from './request'

export const deviceAPI = {
  list(params = {}) {
    return request.get('/devices', { params })
  },
  getById(id) {
    return request.get(`/devices/${id}`)
  },
  create(data) {
    return request.post('/devices', data)
  },
  update(id, data) {
    return request.put(`/devices/${id}`, data)
  },
  delete(id) {
    return request.delete(`/devices/${id}`)
  },
  statusSummary() {
    return request.get('/devices/status-summary')
  },
  getStatistics(id, period = 'today') {
    return request.get(`/devices/${id}/statistics`, { params: { period } })
  },
  control(id, command) {
    return request.post(`/devices/${id}/control`, command)
  },
  collect(id) {
    return request.post(`/devices/${id}/collect`)
  },
}
