// pages/carbon-asset/carbon-asset.js
const httpApi = require('../../utils/api-http.js').httpApi;

Page({
  data: {
    loading: true,
    asset: {
      allocatedQuota: '0',
      purchasedQuota: '0',
      actualEmission: '0',
      surplus: '0',
      status: '正常',
      alertThreshold: 80,
      statusClass: '',
      unitNote: ''
    }
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

  fmt(n) {
    const v = Number(n || 0);
    return v.toLocaleString('zh-CN', { maximumFractionDigits: 2 });
  },

  statusClass(status) {
    if (status === '超排') return 'danger';
    if (status === '预警') return 'warning';
    return 'safe';
  },

  async loadData() {
    this.setData({ loading: true });
    try {
      const res = await httpApi.getCarbonAsset().catch(() => null);
      if (res) {
        const d = res;
        const surplus = Number(d.surplus || 0);
        this.setData({
          asset: {
            allocatedQuota: this.fmt(d.allocatedQuota),
            purchasedQuota: this.fmt(d.purchasedQuota),
            actualEmission: this.fmt(d.actualEmission),
            surplus: this.fmt(Math.abs(surplus)),
            surplusSign: surplus < 0 ? '-' : '',
            status: d.status || '正常',
            statusClass: this.statusClass(d.status),
            alertThreshold: d.alertThreshold || 80,
            unitNote: d.unitNote || ''
          },
          loading: false
        });
      } else {
        this.setData({ loading: false });
      }
    } catch (e) {
      console.error('加载碳资产数据失败:', e);
      this.setData({ loading: false });
      wx.showToast({ title: '加载失败', icon: 'none' });
    }
  },

  onPullDownRefresh() {
    this.loadData().then(() => wx.stopPullDownRefresh());
  },

  onShareAppMessage() {
    return {
      title: '能耗翻译官 — 碳资产',
      path: '/pages/carbon-asset/carbon-asset'
    };
  }
});
