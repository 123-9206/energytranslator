import request from './request'

export const authAPI = {
  login(username, password) {
    return request.post('/auth/login', { username, password })
  },
  refresh(refreshToken) {
    return request.post('/auth/refresh', { refreshToken })
  },
  getUserInfo() {
    return request.get('/auth/userinfo')
  },
  register(data) {
    return request.post('/auth/register', data)
  },
}
