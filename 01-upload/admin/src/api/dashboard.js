import request from './request'

export const dashboardAPI = {
  getDashboard(period = 'today') {
    return request.get('/dashboard', { params: { period } })
  },
  getCarbonDashboard(period = 'today') {
    return request.get('/dashboard/carbon', { params: { period } })
  },
}
