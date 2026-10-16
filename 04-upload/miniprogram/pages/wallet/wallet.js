// pages/wallet/wallet.js
const httpApi = require('../../utils/api-http.js').httpApi;

Page({
  data: {
    loading: true,
    wallet: {
      vppSettled: '0',
      vppWithdrawn: '0',
      vppWithdrawable: '0',
      greenSavingAnnual: '0',
      diagnosisSavingAnnual: '0',
      totalSavedAnnual: '0',
      currency: '元',
      updatedAt: ''
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

  async loadData() {
    this.setData({ loading: true });
    try {
      const res = await httpApi.getWalletSummary().catch(() => null);
      if (res) {
        const d = res;
        this.setData({
          wallet: {
            vppSettled: this.fmt(d.vppSettled),
            vppWithdrawn: this.fmt(d.vppWithdrawn),
            vppWithdrawable: this.fmt(d.vppWithdrawable),
            greenSavingAnnual: this.fmt(d.greenSavingAnnual),
            diagnosisSavingAnnual: this.fmt(d.diagnosisSavingAnnual),
            totalSavedAnnual: this.fmt(d.totalSavedAnnual),
            currency: d.currency || '元',
            updatedAt: d.updatedAt || ''
          },
          loading: false
        });
      } else {
        this.setData({ loading: false });
      }
    } catch (e) {
      console.error('加载钱包账数据失败:', e);
      this.setData({ loading: false });
      wx.showToast({ title: '加载失败', icon: 'none' });
    }
  },

  onPullDownRefresh() {
    this.loadData().then(() => wx.stopPullDownRefresh());
  },

  goVpp() {
    wx.navigateTo({ url: '/pages/vpp/earnings/earnings' });
  },

  onShareAppMessage() {
    return {
      title: '能耗翻译官 — 老板钱包账',
      path: '/pages/wallet/wallet'
    };
  }
});
