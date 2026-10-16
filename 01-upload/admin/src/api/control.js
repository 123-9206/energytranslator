import request from './request'

export const controlAPI = {
  // 设备控制
  controlDevice(deviceId, command) {
    return request.post(`/control/device/${deviceId}`, command)
  },
  batchControl(deviceIds, command) {
    return request.post('/control/batch', { deviceIds, command })
  },

  // 控制日志
  getLogs(params) {
    return request.get('/control/logs', { params })
  },

  // 控制规则
  getRules() {
    return request.get('/control/rules')
  },
  createRule(data) {
    return request.post('/control/rules', data)
  },
  updateRule(id, data) {
    return request.put(`/control/rules/${id}`, data)
  },
  deleteRule(id) {
    return request.delete(`/control/rules/${id}`)
  },
  toggleRule(id) {
    return request.put(`/control/rules/${id}/toggle`)
  },
  triggerRule(id) {
    return request.post(`/control/rules/${id}/trigger`)
  },
}
