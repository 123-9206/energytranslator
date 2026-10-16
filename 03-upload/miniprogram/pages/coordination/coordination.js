// pages/coordination/coordination.js
const httpApi = require('../../utils/api-http.js').httpApi;

Page({
  data: {
    loading: true,
    target: 1500,
    coord: null
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

  /** 预格式化 coord 数据（WXML 不支持函数调用） */
  _fmtCoord(raw) {
    if (!raw) return null;
    raw._regionalAdjKw = this.fmt(raw.regionalAdjustableCapacityKw);
    raw._totalAllocKw = this.fmt(raw.totalAllocatedKw);
    raw._shortfallKw = this.fmt(raw.shortfallKw);
    raw._fulfilledPct = this.fmt(raw.fulfilledPct);
    if (raw.nodes && raw.nodes.length) {
      raw.nodes.forEach(n => {
        n._adjLoadKw = this.fmt(n.adjustableLoadKw);
        n._allocKw = this.fmt(n.allocatedKw);
      });
    }
    return raw;
  },

  onTargetInput(e) {
    const v = Number(e.detail.value) || 0;
    this.setData({ target: v });
  },

  async loadData() {
    this.setData({ loading: true });
    try {
      const res = await httpApi.getCoordination({ targetReductionKw: this.data.target, windowMinutes: 60 }).catch(() => null);
      if (res) {
        this.setData({ coord: this._fmtCoord(res), loading: false });
      } else {
        this.setData({ loading: false });
      }
    } catch (e) {
      console.error('加载协同调度失败:', e);
      this.setData({ loading: false });
      wx.showToast({ title: '加载失败', icon: 'none' });
    }
  },

  rerun() {
    this.loadData();
  },

  onPullDownRefresh() {
    this.loadData().then(() => wx.stopPullDownRefresh());
  },

  onShareAppMessage() {
    return {
      title: '能耗翻译官 — 分布式协同调度',
      path: '/pages/coordination/coordination'
    };
  }
});
