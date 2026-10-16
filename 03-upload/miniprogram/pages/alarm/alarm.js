// pages/alarm/alarm.js
const httpApi = require('../../utils/api-http.js').httpApi;

Page({
  data: {
    loading: true,
    alarms: [],
    page: 1,
    size: 20,
    noMore: false,
    filterSeverity: '',
    severities: [
      { value: '', label: '全部级别' },
      { value: 'critical', label: '严重' },
      { value: 'warning', label: '警告' },
      { value: 'info', label: '提示' }
    ],
    severityIndex: 0,
    severityLabels: {
      critical: '严重',
      warning: '警告',
      info: '提示'
    },
    severityColors: {
      critical: 'red',
      warning: 'orange',
      info: 'blue'
    }
  },

  onLoad() {
    this.checkLogin();
  },

  onShow() {
    // 未登录时直接回"我的"页，不发请求（避免 401 竞态与重复跳转）
    const token = wx.getStorageSync('token');
    if (!token) {
      wx.switchTab({ url: '/pages/profile/profile' });
      return;
    }
    this.setData({ page: 1, alarms: [], noMore: false });
    this.loadAlarms();
  },

  checkLogin() {
    const token = wx.getStorageSync('token');
    if (!token) {
      wx.switchTab({ url: '/pages/profile/profile' });
    }
  },

  loadAlarms() {
    if (this.data.noMore) return;
    this.setData({ loading: true });

    const params = {
      page: this.data.page,
      size: this.data.size
    };
    if (this.data.filterSeverity) {
      params.level = this.data.filterSeverity;
    }

    httpApi.getAlarms(params).then(res => {
      const data = res.data || res;
      const list = Array.isArray(data) ? data : (data.list || data.records || []);
      const alarms = list.map(item => ({
        id: item.id || item._id,
        type: item.type || 'unknown',
        level: item.level || 'info',
        message: item.message || item.description || '未知告警',
        deviceName: item.deviceName || item.device?.name || '未关联设备',
        occurredAt: item.occurredAt || item.createdAt || '',
        status: item.status || 'active'
      }));

      this.setData({
        alarms: this.data.page === 1 ? alarms : this.data.alarms.concat(alarms),
        noMore: alarms.length < this.data.size,
        loading: false
      });
    }).catch(() => {
      this.setData({ loading: false });
      wx.showToast({ title: '加载失败', icon: 'none' });
    });
  },

  onReachBottom() {
    if (!this.data.noMore && !this.data.loading) {
      this.setData({ page: this.data.page + 1 });
      this.loadAlarms();
    }
  },

  onPullDownRefresh() {
    this.setData({ page: 1, alarms: [], noMore: false });
    this.loadAlarms();
    wx.stopPullDownRefresh();
  },

  onSeverityChange(e) {
    const idx = Number(e.detail.value);
    this.setData({
      severityIndex: idx,
      filterSeverity: this.data.severities[idx].value,
      page: 1,
      alarms: [],
      noMore: false
    });
    this.loadAlarms();
  }
});
