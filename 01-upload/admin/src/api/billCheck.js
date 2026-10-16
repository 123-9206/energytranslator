import request from './request'

export const billCheckAPI = {
  // 列表（status 可选：READING/PENDING_REVIEW/NEEDS_MANUAL_READ/APPROVED/DELIVERED/REJECTED）
  list(params = {}) {
    return request.get('/admin/bill-check', { params })
  },
  // 漏斗看板：上传 → 读单成功 → 复核通过 → 已交付
  funnel() {
    return request.get('/admin/bill-check/funnel')
  },
  detail(id) {
    return request.get(`/admin/bill-check/${id}`)
  },
  // 人工录入/修正账单字段后重跑校验+分析
  rerun(id, billData) {
    return request.post(`/admin/bill-check/${id}/rerun`, billData)
  },
  approve(id, note, finalReportText) {
    return request.post(`/admin/bill-check/${id}/approve`, { note, finalReportText })
  },
  deliver(id) {
    return request.post(`/admin/bill-check/${id}/deliver`)
  },
  reject(id, note) {
    return request.post(`/admin/bill-check/${id}/reject`, { note })
  },
  // 原图（带鉴权头取 blob）
  imageUrl(id) {
    return `/api/admin/bill-check/${id}/image`
  },
}
