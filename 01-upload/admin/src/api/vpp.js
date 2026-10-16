import request from './request'

export const vppAPI = {
  getActivities(params = {}) {
    return request.get('/vpp/activities', { params })
  },
  createActivity(data) {
    return request.post('/vpp/activities', data)
  },
  cancelActivity(id, reason = '') {
    return request.post(`/vpp/activities/${id}/cancel`, { reason })
  },
  startActivity(id) {
    return request.post(`/vpp/activities/${id}/start`)
  },
  endActivity(id) {
    return request.post(`/vpp/activities/${id}/end`)
  },
  getResources(params = {}) {
    return request.get('/vpp/resources', { params })
  },
}
