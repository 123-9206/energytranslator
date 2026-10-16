// pages/green/green.js
const httpApi = require('../../utils/api-http.js').httpApi;

Page({
  data: {
    loading: true,
    summary: {
      contractCount: 0,
      totalVolume: 0,
      actualVolume: 0,
      totalAmount: 0,
      carbonOffset: 0,
      estimatedSaving: 0,
      certificateCount: 0
    },
    contracts: [],
    page: 1,
    pageSize: 20,
    hasMore: true
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
    this.loadData();
  },

  checkLogin() {
    const token = wx.getStorageSync('token');
    if (!token) {
      wx.switchTab({ url: '/pages/profile/profile' });
    }
  },

  loadData() {
    this.loadSummary();
    this.loadContracts(1, true);
  },

  loadSummary() {
    httpApi.getGreenPowerSummary().then(res => {
      this.setData({ summary: res || this.data.summary });
    }).catch(() => {});
  },

  loadContracts(page, reset) {
    httpApi.getGreenPowerContracts({ page, pageSize: this.data.pageSize }).then(res => {
      const list = (res && res.list) || [];
      // 预处理 statusText —— WXML 数据绑定不支持函数调用，需在 JS 侧格式化
      const map = { draft: '草稿', active: '进行中', completed: '已完成', terminated: '已终止' };
      const formatted = list.map(item => ({
        ...item,
        statusText: map[item.status] || item.status || '未知'
      }));
      const contracts = reset ? formatted : this.data.contracts.concat(formatted);
      this.setData({
        contracts,
        page,
        hasMore: list.length >= this.data.pageSize,
        loading: false
      });
    }).catch(() => {
      this.setData({ loading: false });
    });
  },

  onReachBottom() {
    if (!this.data.hasMore) return;
    this.loadContracts(this.data.page + 1, false);
  },

  onAddContract() {
    wx.navigateTo({ url: '/pages/green/add/add' });
  },

  statusText(status) {
    const map = { draft: '草稿', active: '进行中', completed: '已完成', terminated: '已终止' };
    return map[status] || status || '未知';
  }
});
