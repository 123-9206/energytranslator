// pages/circuit-safety/circuit-safety.js
const httpApi = require('../../utils/api-http.js').httpApi;

Page({
  data: {
    loading: true,
    summary: {
      deviceCount: 0,
      safe: 0,
      warning: 0,
      danger: 0
    },
    devices: []
  },

  onShow() {
    if (!this.checkLogin()) return;
    this.loadData();
  },

  checkLogin() {
    const token = wx.getStorageSync('token');
    if (!token) {
      wx.navigateTo({ url: '/pages/profile/login/login' });
      return false;
    }
    return true;
  },

  async loadData() {
    this.setData({ loading: true });
    try {
      const res = await httpApi.getCircuitSafetyOverview().catch(() => null);
      if (res) {
        const d = res;
        const devices = (Array.isArray(d.items) ? d.items : []).map(item => ({
          deviceId: item.deviceId,
          deviceName: item.deviceName || '未命名设备',
          available: item.available,
          safetyScore: item.safetyScore != null ? item.safetyScore : '-',
          safetyLevel: item.safetyLevel || '未知',
          levelClass: this.levelClass(item.safetyLevel),
          maxCurrent: (item.maxCurrent != null ? item.maxCurrent : 0).toFixed(1),
          avgVoltage: (item.avgVoltage != null ? item.avgVoltage : 0).toFixed(1),
          maxTemperature: (item.maxTemperature != null ? item.maxTemperature : 0).toFixed(1),
          minPowerFactor: (item.minPowerFactor != null ? item.minPowerFactor : 0).toFixed(2),
          faultCount: item.faultCount || 0,
          faults: Array.isArray(item.faults) ? item.faults.map(f => ({
            type: f.type,
            severity: f.severity,
            detail: f.detail,
            sevClass: f.severity === 'critical' ? 'danger' : (f.severity === 'warning' ? 'warning' : '')
          })) : []
        }));
        this.setData({
          summary: {
            deviceCount: d.deviceCount || 0,
            safe: d.safe || 0,
            warning: d.warning || 0,
            danger: d.danger || 0
          },
          devices: devices,
          loading: false
        });
      } else {
        this.setData({ loading: false });
      }
    } catch (e) {
      console.error('加载电路安全数据失败:', e);
      this.setData({ loading: false });
      wx.showToast({ title: '加载失败', icon: 'none' });
    }
  },

  levelClass(level) {
    if (level === '安全') return 'safe';
    if (level === '注意') return 'warning';
    if (level === '警告' || level === '危险') return 'danger';
    return '';
  },

  onPullDownRefresh() {
    this.loadData().then(() => wx.stopPullDownRefresh());
  },

  onShareAppMessage() {
    return {
      title: '能耗翻译官 — 电路安全',
      path: '/pages/circuit-safety/circuit-safety'
    };
  }
});
