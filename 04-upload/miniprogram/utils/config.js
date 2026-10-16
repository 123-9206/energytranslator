/**
 * =====================================================
 * 能耗翻译官 - 配置文件
 * =====================================================
 * 配置API连接方式：
 * - mode: 'cloud' 使用云开发（云函数）
 * - mode: 'http' 使用HTTP API（Spring Boot后端）
 */

module.exports = {
  // API模式: 'cloud' | 'http'
  mode: 'http',
  
  // HTTP API配置
  http: {
    // baseUrl: 'http://localhost:8080',  // 开发环境（需要在微信公众平台配置request合法域名）
    baseUrl: 'http://127.0.0.1:8080',  // 本地开发者工具；真机需自己的后端地址
    timeout: 30000
  },
  
  // 云开发配置
  cloud: {
    env: '',
    cloudFunctions: {
      auth: 'vpp-auth',
      activities: 'vpp-activity',
      resources: 'vpp-resource',
      settlements: 'vpp-settlement',
      energy: 'addEnergyRecord',
      dashboard: 'getDashboard',
      carbon: 'getCarbonReport'
    }
  },
  
  // 系统配置
  system: {
    // 默认电价(元/kWh)
    defaultElectricityPrice: 0.6,
    // 默认碳排放因子(kg CO2/kWh)
    defaultCarbonFactor: 0.785,
    // VPP配置
    vpp: {
      defaultCapacityPrice: 50,   // 容量补贴(元/kW)
      defaultEnergyPrice: 2,      // 电量补贴(元/kWh)
      minCommitmentCapacity: 50    // 最低承诺容量(kW)
    }
  },

  // 微信订阅消息模板ID（请在微信公众平台 > 订阅消息 > 我的模板 中获取）
  // 操作路径：登录 mp.weixin.qq.com → 功能 → 订阅消息 → 选用模板
  subscribeTemplates: {
    alarm: '',           // 故障告警通知
    quotaWarning: '',     // 站点超额预警通知
    vppActivity: ''      // 活动参与通知
  },

  // ==========================================
  // 版本信息（每次发版必须同步修改！）
  // code   = 当前客户端版本号（与上传版本一致）
  // ==========================================
  version: {
    code: '1.5.0',                // 客户端当前版本，与后端 /api/public/version 对比
    name: 'v1.5.0 全域能力+分布式协同版'  // 展示名
  }
};
