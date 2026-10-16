import request from './request'

export const walletAPI = {
  getSummary() {
    return request.get('/wallet/summary')
  },
}
