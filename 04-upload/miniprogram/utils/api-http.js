/**
 * 能耗翻译官 - HTTP API服务层
 * =====================================================
 * 用于连接Spring Boot后端API
 * 替换云函数调用方式
 */

/**
 * HTTP API配置
 * 从 config.js 读取 baseUrl，保持单一配置源
 */
const config = require('./config.js');
const API_BASE_URL = config.http.baseUrl;

/**
 * HTTP API服务类
 */
class HttpAPIService {
  constructor() {
    this.baseUrl = API_BASE_URL;
    this.token = null;
    this._loadingCount = 0; // 并发请求计数，保证 showLoading/hideLoading 配对
    this._loadingShown = false; // loading 是否真实显示在 UI 上，避免重复 hideLoading 触发"未配对"警告
    this._loginRedirecting = false; // 401/403 跳登录页防抖，避免并发多个 401 重复跳转
    this._refreshPromise = null; // 刷新 token 共享 Promise，并发多个 401 只刷新一次
    this._loadingTimer = null; // loading 兜底定时器：超时强制关闭遮罩，防止 mask 卡死按钮（"按钮点不开"根因）
    this._requestQueue = []; // 并发控制：排队中的请求
    this._activeCount = 0; // 并发控制：当前活跃请求数
    this.MAX_CONCURRENT = 6; // 最大并发（微信真机/模拟器并发上限约10，留余量防排队超时）
  }

  /**
   * 设置Token
   */
  setToken(token) {
    this.token = token;
    this._loginRedirecting = false; // 登录成功后复位防抖标志，允许后续真实 401 再次引导登录
    wx.setStorageSync('token', token);
  }

  /**
   * 获取Token
   */
  getToken() {
    if (!this.token) {
      this.token = wx.getStorageSync('token');
    }
    return this.token;
  }

  /**
   * 通用请求方法（含并发控制：超过上限自动排队，避免快速切换页面时的请求风暴触发微信并发限制导致超时）
   */
  async request(method, path, data = {}, options = {}) {
    // 并发控制：活跃请求达上限时排队等待；刷新重放/网络重试请求跳过队列（避免自我阻塞）
    if (this._activeCount >= this.MAX_CONCURRENT && !options._retried && !options._retryCount) {
      return new Promise((resolve, reject) => {
        this._requestQueue.push(() => {
          this.request(method, path, data, { ...options, _queued: true }).then(resolve, reject);
        });
      });
    }
    this._activeCount++;
    try {
      return await this._doRequest(method, path, data, options);
    } finally {
      this._activeCount--;
      this._drainQueue();
    }
  }

  /**
   * 队列调度：有空闲并发位时取出排队请求执行
   */
  _drainQueue() {
    while (this._activeCount < this.MAX_CONCURRENT && this._requestQueue.length > 0) {
      const next = this._requestQueue.shift();
      next();
    }
  }

  /**
   * 实际请求执行（超时自动重试 + token 自动续期）
   */
  async _doRequest(method, path, data = {}, options = {}) {
    const url = `${this.baseUrl}${path}`;
    const token = this.getToken();

    const header = {
      'Content-Type': 'application/json'
    };

    if (token) {
      header['Authorization'] = `Bearer ${token}`;
    }

    return new Promise((resolve, reject) => {
      // silent 模式（token 刷新后重放请求）不再弹 loading，避免闪烁
      if (!options.silent) this._showLoading();

      wx.request({
        url,
        method,
        data,
        header,
        // 15s 超时：覆盖开发者工具/真机首次冷连接经代理握手的高延迟，
        // 绝大多数请求 <1s 返回，仅在代理/VPN 残留时才需要这几秒余量
        timeout: 15000,
        success: (res) => {
          if (!options.silent) this._hideLoading();
          if (res.statusCode === 200) {
            if (res.data.success) {
              resolve(res.data.data);
            } else {
              wx.showToast({
                title: res.data.error || '请求失败',
                icon: 'none'
              });
              reject(new Error(res.data.error));
            }
          } else if (res.statusCode === 401 || res.statusCode === 403) {
            // 401=未登录/登录过期，403=无权限/凭证失效 → 统一引导重新登录
            // 只在实际显示 loading 时 hide：并发多个 401 时第一个已 hide 掉，
            // 若第二个再调 wx.hideLoading() 会报"showLoading 与 hideLoading 必须配对"警告
            if (this._loadingShown) {
              wx.hideLoading();
              this._loadingShown = false;
            }
            this._loadingCount = 0;
            // 401 且有 refreshToken → 静默刷新 token 后自动重放原请求（用户无感知续期）
            if (res.statusCode === 401 && !options._retried && wx.getStorageSync('refreshToken')) {
              this._refreshWithLock().then(() => {
                // 刷新成功，用新 token 重放（silent 模式：不再弹 loading）
                return this.request(method, path, data, { _retried: true, silent: true });
              }).then((retryData) => {
                resolve(retryData);
              }).catch(() => {
                // 刷新失败：refreshToken 也已失效 → 清除登录态并引导重新登录
                this.clearToken();
                wx.removeStorageSync('refreshToken');
                this._redirectToLogin(res.statusCode);
                reject(new Error(res.data.error || '未登录'));
              });
              return;
            }
            // 无 refreshToken（首次未登录）或 403 → 直接清除并引导登录
            this.clearToken();
            // 防抖：并发多个 401/403 只提示并跳转一次
            if (!this._loginRedirecting) {
              this._redirectToLogin(res.statusCode);
            }
            reject(new Error(res.data.error || '未登录'));
          } else {
            wx.showToast({
              title: (res.data && res.data.error) || '网络错误',
              icon: 'none'
            });
            reject(new Error(res.data.error || '网络错误'));
          }
        },
        fail: (err) => {
          // 网络异常（超时/断网/抖动）：沉默重试（最多2次，300ms退避）。
          // 关键：重试期间【保持 loading 不隐藏】，避免"死屏"割裂感；
          // 仅在全部重试耗尽后才关闭遮罩并提示，用户全程看到"加载中..."连续反馈。
          const retried = options._retryCount || 0;
          const MAX_RETRY = 2;
          if (retried < MAX_RETRY) {
            setTimeout(() => {
              this.request(method, path, data, { ...options, _retryCount: retried + 1, silent: true })
                .then(
                  (d) => { this._hideLoading(); resolve(d); },  // 重试成功：补关遮罩后交付数据
                  (e) => { this._hideLoading(); reject(e); }    // 重试命中业务错误(如400密码错)或已耗尽：补关遮罩后透传
                );
            }, 300);
            return;
          }
          // 全部重试失败：关闭遮罩 + 友好提示（不抛底层错误，避免控制台红色噪音）
          this._hideLoading();
          wx.showToast({
            title: '网络连接失败，请稍后重试',
            icon: 'none'
          });
          reject(err);
        }
      });
    });
  }

  /**
   * 清除本地Token（登录失效时调用）
   */
  clearToken() {
    this.token = null;
    wx.removeStorageSync('token');
  }

  /**
   * 401/403 统一引导登录（防抖：并发多个只提示+跳转一次）
   */
  _redirectToLogin(statusCode) {
    if (this._loginRedirecting) return;
    this._loginRedirecting = true;
    // 跳转前确保 loading 遮罩已强制关闭，否则 mask 会挡住登录页按钮（"按钮点不开"）
    if (this._loadingShown) {
      wx.hideLoading();
      this._loadingShown = false;
    }
    this._loadingCount = 0;
    if (this._loadingTimer) {
      clearTimeout(this._loadingTimer);
      this._loadingTimer = null;
    }
    wx.showToast({
      title: statusCode === 401 ? '请先登录' : '登录已过期，请重新登录',
      icon: 'none'
    });
    setTimeout(() => {
      // 若当前已在登录页则不重复跳转
      const pages = getCurrentPages();
      const current = pages[pages.length - 1];
      if (current && current.route === 'pages/profile/login/login') return;
      wx.navigateTo({ url: '/pages/profile/login/login' });
    }, 600);
  }

  // 并发请求计数，保证 showLoading/hideLoading 配对
  // 关键：加 10 秒兜底定时器——即使请求永不返回，loading 遮罩也会强制关闭，
  // 否则 mask:true 会一直挡住所有按钮点击（表现为"按钮点不开"）
  _showLoading() {
    this._loadingCount++;
    if (this._loadingCount === 1) {
      wx.showLoading({ title: '加载中...', mask: true });
      this._loadingShown = true;
      if (this._loadingTimer) clearTimeout(this._loadingTimer);
      this._loadingTimer = setTimeout(() => {
        if (this._loadingShown) {
          wx.hideLoading();
          this._loadingShown = false;
        }
        this._loadingCount = 0;
        this._loadingTimer = null;
      }, 35000); // 覆盖最坏情况：15s超时 × 2次重试 + 退避余量，避免遮罩提前消失卡死按钮
    }
  }

  _hideLoading() {
    // 只有计数>0时才算配对扣除；计数已为0时直接忽略，
    // 避免多余调用 wx.hideLoading() 触发"showLoading与hideLoading必须配对"警告
    if (this._loadingCount <= 0) return;
    this._loadingCount--;
    if (this._loadingCount === 0) {
      if (this._loadingShown) {
        wx.hideLoading();
        this._loadingShown = false;
      }
      if (this._loadingTimer) {
        clearTimeout(this._loadingTimer);
        this._loadingTimer = null;
      }
    }
  }

  /**
   * GET请求
   */
  async get(path, params = {}) {
    return this.request('GET', path, params);
  }

  /**
   * POST请求
   */
  async post(path, data = {}) {
    return this.request('POST', path, data);
  }

  /**
   * PUT请求
   */
  async put(path, data = {}) {
    return this.request('PUT', path, data);
  }

  /**
   * DELETE请求
   */
  async delete(path, data = {}) {
    return this.request('DELETE', path, data);
  }

  // =====================================================
  // 认证相关API
  // =====================================================

  /**
   * 用户登录
   */
  async login(username, password) {
    const data = await this.post('/api/auth/login', { username, password });
    if (data.token) {
      this.setToken(data.token);
      if (data.refreshToken) {
        wx.setStorageSync('refreshToken', data.refreshToken);
      }
    }
    return data;
  }

  /**
   * 免密体验登录（一键体验演示企业）
   * 后端以固定公开体验账号(demo)签发 token，前端无需写死口令，
   * 避免后端改密后体验入口失效。
   */
  async demoLogin() {
    const data = await this.post('/api/auth/demo-login', {});
    if (data.token) {
      this.setToken(data.token);
      if (data.refreshToken) {
        wx.setStorageSync('refreshToken', data.refreshToken);
      }
    }
    return data;
  }

  /**
   * 用户注册
   */
  async register(userInfo) {
    return this.post('/api/auth/register', userInfo);
  }

  /**
   * 获取用户信息
   */
  async getUserInfo() {
    return this.get('/api/auth/userinfo');
  }

  /**
   * 刷新Token（静默续期）
   * 注意：必须用裸 wx.request，不能走 this.post()——
   * 否则 refresh 接口自身 401 时会再次进入 401 分支触发递归刷新，造成死循环
   */
  refreshToken() {
    const refreshToken = wx.getStorageSync('refreshToken');
    if (!refreshToken) return Promise.reject(new Error('no refresh token'));
    return new Promise((resolve, reject) => {
      wx.request({
        url: `${this.baseUrl}/api/auth/refresh`,
        method: 'POST',
        data: { refreshToken },
        header: { 'Content-Type': 'application/json' },
        timeout: 15000,
        success: (res) => {
          if (res.statusCode === 200 && res.data && res.data.success && res.data.data && res.data.data.token) {
            const d = res.data.data;
            this.setToken(d.token);
            if (d.refreshToken) {
              wx.setStorageSync('refreshToken', d.refreshToken);
            }
            resolve(d);
          } else {
            reject(new Error('refresh failed'));
          }
        },
        fail: () => reject(new Error('network error'))
      });
    });
  }

  /**
   * 共享刷新锁：并发多个 401 时只发一次 refresh 请求，
   * 其余请求复用同一个 Promise，刷新成功后各自重放原请求
   */
  _refreshWithLock() {
    if (!this._refreshPromise) {
      this._refreshPromise = this.refreshToken().finally(() => {
        this._refreshPromise = null;
      });
    }
    return this._refreshPromise;
  }

  // =====================================================
  // VPP活动相关API
  // =====================================================

  /**
   * 获取活动列表
   */
  async getActivities(params = {}) {
    return this.get('/api/vpp/activities', params);
  }

  /**
   * 获取活动详情
   */
  async getActivityDetail(activityId) {
    return this.get(`/api/vpp/activities/${activityId}`);
  }

  /**
   * 报名活动
   */
  async enrollActivity(activityId, resourceIds, committedCapacity, contactName, contactPhone) {
    return this.post(`/api/vpp/activities/${activityId}/enroll`, {
      resourceIds,
      committedCapacity,
      contactName,
      contactPhone
    });
  }

  /**
   * 取消报名
   */
  async cancelEnroll(activityId) {
    return this.post(`/api/vpp/activities/${activityId}/cancelEnroll`);
  }

  /**
   * 获取我的报名
   */
  async getMyEnrollments(params = {}) {
    return this.get('/api/vpp/activities/myEnrollments', params);
  }

  // =====================================================
  // VPP资源相关API
  // =====================================================

  /**
   * 获取资源列表
   */
  async getResources(params = {}) {
    return this.get('/api/vpp/resources', params);
  }

  /**
   * 获取资源详情
   */
  async getResourceDetail(resourceId) {
    return this.get(`/api/vpp/resources/${resourceId}`);
  }

  /**
   * 注册资源
   */
  async registerResource(resourceData) {
    return this.post('/api/vpp/resources', resourceData);
  }

  /**
   * 更新资源
   */
  async updateResource(resourceId, updates) {
    return this.put(`/api/vpp/resources/${resourceId}`, updates);
  }

  /**
   * 删除资源
   */
  async deleteResource(resourceId) {
    return this.delete(`/api/vpp/resources/${resourceId}`);
  }

  /**
   * 获取资源状态
   */
  async getResourceStatus(resourceId) {
    return this.get(`/api/vpp/resources/${resourceId}/status`);
  }

  // =====================================================
  // 能耗相关API
  // =====================================================

  /**
   * 添加能耗记录
   */
  async addEnergyRecord(recordData) {
    return this.post('/api/energy/records', recordData);
  }

  /**
   * 获取能耗记录
   */
  async getEnergyRecords(params = {}) {
    return this.get('/api/energy/records', params);
  }

  /**
   * 获取今日能耗
   */
  async getTodayEnergy() {
    return this.get('/api/energy/records/today');
  }

  // =====================================================
  // 仪表盘相关API
  // =====================================================

  /**
   * 获取仪表盘数据
   */
  async getDashboard(period = 'today') {
    return this.get('/api/dashboard', { period });
  }

  /**
   * 获取碳排放报告
   */
  async getCarbonReport(params = {}) {
    return this.get('/api/dashboard/carbon', params);
  }

  // =====================================================
  // 设备相关API
  // =====================================================

  /**
   * 获取设备列表
   */
  async getDevices(params = {}) {
    return this.get('/api/devices', params);
  }

  /**
   * 获取设备详情
   */
  async getDeviceDetail(deviceId) {
    return this.get(`/api/devices/${deviceId}`);
  }

  /**
   * 创建设备
   */
  async createDevice(deviceData) {
    return this.post('/api/devices', deviceData);
  }

  /**
   * 更新设备
   */
  async updateDevice(deviceId, updates) {
    return this.put(`/api/devices/${deviceId}`, updates);
  }

  /**
   * 删除设备
   */
  async deleteDevice(deviceId) {
    return this.delete(`/api/devices/${deviceId}`);
  }

  /**
   * 获取设备状态汇总
   */
  async getDeviceStatusSummary() {
    return this.get('/api/devices/status-summary');
  }

  // =====================================================
  // 设备控制相关API
  // =====================================================

  /**
   * 下发设备控制指令
   */
  async controlDevice(deviceId, command) {
    return this.post(`/api/control/device/${deviceId}`, command);
  }

  /**
   * 批量控制设备
   */
  async batchControl(deviceIds, command) {
    return this.post('/api/control/batch', { deviceIds, command });
  }

  /**
   * 获取控制日志
   */
  async getControlLogs(params = {}) {
    return this.get('/api/control/logs', params);
  }

  /**
   * 获取控制规则列表
   */
  async getControlRules() {
    return this.get('/api/control/rules');
  }

  /**
   * 创建控制规则
   */
  async createControlRule(ruleData) {
    return this.post('/api/control/rules', ruleData);
  }

  /**
   * 更新控制规则
   */
  async updateControlRule(ruleId, updates) {
    return this.put(`/api/control/rules/${ruleId}`, updates);
  }

  /**
   * 删除控制规则
   */
  async deleteControlRule(ruleId) {
    return this.delete(`/api/control/rules/${ruleId}`);
  }

  /**
   * 启用/禁用控制规则
   */
  async toggleControlRule(ruleId) {
    return this.put(`/api/control/rules/${ruleId}/toggle`);
  }

  // =====================================================
  // 告警相关API
  // =====================================================

  /**
   * 获取告警列表
   */
  async getAlarms(params = {}) {
    return this.get('/api/alarms', params);
  }

  /**
   * 获取告警详情
   */
  async getAlarmDetail(alarmId) {
    return this.get(`/api/alarms/${alarmId}`);
  }

  /**
   * 确认告警
   */
  async acknowledgeAlarm(alarmId) {
    return this.put(`/api/alarms/${alarmId}/acknowledge`);
  }

  /**
   * 解决告警
   */
  async resolveAlarm(alarmId, solution) {
    return this.put(`/api/alarms/${alarmId}/resolve`, { solution });
  }

  /**
   * 忽略告警
   */
  async ignoreAlarm(alarmId) {
    return this.put(`/api/alarms/${alarmId}/ignore`);
  }

  /**
   * 获取告警统计
   */
  async getAlarmStatistics() {
    return this.get('/api/alarms/statistics');
  }

  /**
   * 批量确认告警
   */
  async batchAcknowledgeAlarms(alarmIds) {
    return this.put('/api/alarms/batch/acknowledge', { ids: alarmIds });
  }

  // =====================================================
  // 分析相关API
  // =====================================================

  /**
   * 获取能耗趋势分析
   */
  async getAnalysisTrend(params = {}) {
    return this.get('/api/analysis/trend', params);
  }

  /**
   * 获取能耗排名
   */
  async getAnalysisRanking(params = {}) {
    return this.get('/api/analysis/ranking', params);
  }

  /**
   * 获取能耗统计摘要
   */
  async getAnalysisSummary(params = {}) {
    return this.get('/api/analysis/summary', params);
  }

  /**
   * 获取分析详情
   */
  async getAnalysisDetail(params = {}) {
    return this.get('/api/analysis/detail', params);
  }

  // =====================================================
  // 碳排放报告API
  // =====================================================

  /**
   * 获取碳排放报告（独立接口）
   */
  async getCarbonReportDetail(params = {}) {
    return this.get('/api/carbon/report', params);
  }

  /**
   * 获取碳排放趋势
   */
  async getCarbonTrend(params = {}) {
    return this.get('/api/carbon/trend', params);
  }

  /**
   * 获取碳排放摘要
   */
  async getCarbonSummary(params = {}) {
    return this.get('/api/carbon/summary', params);
  }

  // ==================== 告警规则管理 ====================

  /**
   * 获取告警规则列表
   */
  async getAlarmRules(params = {}) {
    return this.get('/api/alarms/rules', params);
  }

  /**
   * 创建告警规则
   */
  async createAlarmRule(data) {
    return this.post('/api/alarms/rules', data);
  }

  /**
   * 更新告警规则
   */
  async updateAlarmRule(id, data) {
    return this.put(`/api/alarms/rules/${id}`, data);
  }

  /**
   * 删除告警规则
   */
  async deleteAlarmRule(id) {
    return this.delete(`/api/alarms/rules/${id}`);
  }

  /**
   * 启用/禁用告警规则
   */
  async toggleAlarmRule(id) {
    return this.put(`/api/alarms/rules/${id}/toggle`);
  }

  // =====================================================
  // 绿电交易台账 API
  // =====================================================

  /**
   * 我的绿电合同列表
   */
  async getGreenPowerContracts(params = {}) {
    return this.get('/api/green-power/my', params);
  }

  /**
   * 新增绿电合同
   */
  async createGreenPowerContract(data) {
    return this.post('/api/green-power', data);
  }

  /**
   * 更新绿电合同
   */
  async updateGreenPowerContract(id, data) {
    return this.put(`/api/green-power/${id}`, data);
  }

  /**
   * 删除绿电合同
   */
  async deleteGreenPowerContract(id) {
    return this.delete(`/api/green-power/${id}`);
  }

  /**
   * 绿电收益汇总（绿电消纳/碳减排/绿证/节约成本）
   */
  async getGreenPowerSummary() {
    return this.get('/api/green-power/summary');
  }

  // =====================================================
  // 政策福利中心 API
  // =====================================================

  /**
   * 可申报政策列表（含匹配标记）
   */
  async getAvailablePolicies(params = {}) {
    return this.get('/api/policies/available', params);
  }

  /**
   * 政策汇总（可用数/预估福利/类型分布）
   */
  async getPolicySummary() {
    return this.get('/api/policies/summary');
  }

  // =====================================================
  // VPP 结算 API
  // =====================================================

  /**
   * 我的VPP结算单列表
   */
  async getMySettlements(params = {}) {
    return this.get('/api/vpp/settlements/my', params);
  }

  /**
   * 生成我的月度结算单
   */
  async generateSettlement(period) {
    return this.post('/api/vpp/settlements/generate', { period });
  }

  // =====================================================
  // 以下方法由 v1.3.1 合并而来（2026-08-13 统一工程）。
  // 后端接口若暂未实现，页面调用时会友好提示，不影响其他功能。
  // =====================================================

  /**
   * 获取 VPP 结算详情（供 vpp/settlement 页调用）
   */
  async getSettlementDetail(settlementId) {
    return this.get(`/api/vpp/settlements/${settlementId}`);
  }

  /**
   * 发起 VPP 结算提现（paid -> withdrawn）
   */
  async withdrawSettlement(settlementId, account) {
    return this.post(`/api/vpp/settlements/${settlementId}/withdraw`, account ? { account } : {});
  }

  /**
   * 获取当前年度碳配额（后端 /api/carbon/quota/current，待实现）
   */
  async getCurrentQuota() {
    return this.get('/api/carbon/quota/current');
  }

  /**
   * 获取碳配额历史（后端 /api/carbon/quota/history，待实现）
   */
  async getQuotaHistory() {
    return this.get('/api/carbon/quota/history');
  }

  // =====================================================
  // 电路安全 API
  // =====================================================

  /**
   * 企业级电路安全总览（设备安全评分/等级/隐患）
   */
  async getCircuitSafetyOverview() {
    return this.get('/api/circuit-safety/overview');
  }

  /**
   * 单设备电路安全明细
   */
  async getCircuitSafetyDevice(deviceId) {
    return this.get(`/api/circuit-safety/device/${deviceId}`);
  }

  // =====================================================
  // 老板钱包账 API
  // =====================================================

  /**
   * 老板钱包账汇总（VPP净额+绿电年省+诊断年省）
   */
  async getWalletSummary() {
    return this.get('/api/wallet/summary');
  }

  // =====================================================
  // 碳资产 API
  // =====================================================

  /**
   * 碳资产盈余（核定配额+已购碳汇−实际排放）
   */
  async getCarbonAsset() {
    return this.get('/api/carbon/asset');
  }

  // =====================================================
  // AI 用电诊断 API（用电模式识别 + 需求响应策略）
  // =====================================================

  /**
   * 执行完整 AI 诊断（含 loadPatterns 用电模式、drStrategies 需求响应策略）
   */
  async getDiagnosis(params = {}) {
    return this.get('/api/diagnosis/run', params);
  }

  /**
   * 分布式协同调度 / 多企业可调负荷聚合优化器（吸收 CN120767796B 分布式协同，独立实现）
   * @param {{targetReductionKw?:number, windowMinutes?:number}} params
   */
  async getCoordination(params = {}) {
    const body = {
      targetReductionKw: params.targetReductionKw || 1500,
      windowMinutes: params.windowMinutes || 60,
    }
    return this.request('POST', '/api/coordination/aggregate', body);
  }

  // =====================================================
  // 合作售电分润 API
  // =====================================================

  /**
   * 合作售电公司列表
   */
  async getPartners(params = {}) {
    return this.get('/api/partners', params);
  }

  /**
   * 新增合作售电公司（平台运营在管理后台维护；企业端一般只读）
   */
  async createPartner(data) {
    return this.post('/api/partners', data);
  }

  /**
   * 分润结算单列表
   */
  async getRevenueShares(params = {}) {
    return this.get('/api/partners/revenue-shares', params);
  }

  /**
   * 试算售电分润（POST，参数走 query：partnerId、period）
   */
  async calculateRevenueShare(params = {}) {
    const qs = Object.keys(params)
      .filter(k => params[k] != null && params[k] !== '')
      .map(k => `${encodeURIComponent(k)}=${encodeURIComponent(params[k])}`)
      .join('&');
    const path = '/api/partners/revenue-shares/calculate' + (qs ? '?' + qs : '');
    return this.request('POST', path, {});
  }
}

// 导出单例
const httpApi = new HttpAPIService();

module.exports = {
  HttpAPIService,
  httpApi
};
