import request from './request'

export const circuitSafetyAPI = {
  getOverview() {
    return request.get('/circuit-safety/overview')
  },
  getDevice(deviceId) {
    return request.get(`/circuit-safety/device/${deviceId}`)
  },
}
