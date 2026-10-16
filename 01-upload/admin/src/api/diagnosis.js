import request from './request'

export const diagnosisAPI = {
  // 企业级完整 AI 诊断（含用电模式识别 loadPatterns + 需求响应策略 drStrategies）
  run(params = {}) {
    return request.get('/diagnosis/run', { params })
  },
  // 管理端企业列表（用于选择诊断对象）
  getEnterprises(params = {}) {
    return request.get('/admin/enterprises', { params })
  },
  // 分布式协同调度 / 多企业可调负荷聚合优化器（吸收 CN120767796B 分布式协同，独立实现）
  aggregateCoordination(body = {}) {
    return request.post('/coordination/aggregate', body)
  },
}
