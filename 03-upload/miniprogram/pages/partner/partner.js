// pages/partner/partner.js
const httpApi = require('../../utils/api-http.js').httpApi;

Page({
  data: {
    loading: true,
    partners: [],
    shares: [],
    calc: {
      savingBase: '',
      ratio: '30'
    },
    calcResult: null
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
      const [pRes, sRes] = await Promise.all([
        httpApi.getPartners().catch(() => null),
        httpApi.getRevenueShares().catch(() => null)
      ]);

      const partners = (pRes ? (Array.isArray(pRes) ? pRes : (pRes.list || [])) : []).map(p => ({
        id: p.id,
        companyName: p.partnerCompanyName || '合作售电公司',
        licenseRegion: p.licenseRegion || '',
        licenseNo: p.licenseNo || '',
        contactName: p.contactName || '',
        ratioPercent: p.platformShareRatio != null ? Math.round(p.platformShareRatio * 100) : null,
        statusText: p.status === 'active' ? '合作中' : (p.status || '待启用')
      }));

      const shares = (sRes ? (Array.isArray(sRes) ? sRes : (sRes.list || [])) : []).map(s => ({
        id: s.id,
        partnerCompanyName: s.partnerCompanyName || '合作售电公司',
        period: s.period || '',
        savingBase: this.fmt(s.savingBase),
        platformShareAmount: this.fmt(s.platformShareAmount),
        partnerShareAmount: this.fmt(s.partnerShareAmount),
        statusText: s.status === 'settled' ? '已结算' : (s.status === 'pending' ? '待结算' : (s.status || '-'))
      }));

      this.setData({ partners, shares, loading: false });
    } catch (e) {
      console.error('加载合作售电数据失败:', e);
      this.setData({ loading: false });
      wx.showToast({ title: '加载失败', icon: 'none' });
    }
  },

  onCalcInput(e) {
    const field = e.currentTarget.dataset.field;
    this.setData({ [`calc.${field}`]: e.detail.value });
  },

  doCalc() {
    const base = Number(this.data.calc.savingBase);
    const ratio = Number(this.data.calc.ratio);
    if (!base || base <= 0) {
      wx.showToast({ title: '请输入省电费基数', icon: 'none' });
      return;
    }
    if (isNaN(ratio) || ratio < 0 || ratio > 100) {
      wx.showToast({ title: '比例需 0-100', icon: 'none' });
      return;
    }
    const platformAmount = base * ratio / 100;
    const partnerAmount = base - platformAmount;
    this.setData({
      calcResult: {
        base: this.fmt(base),
        platformAmount: this.fmt(platformAmount.toFixed(2)),
        partnerAmount: this.fmt(partnerAmount.toFixed(2)),
        ratio: ratio
      }
    });
  },

  resetCalc() {
    this.setData({ calcResult: null });
  },

  onPullDownRefresh() {
    this.loadData().then(() => wx.stopPullDownRefresh());
  },

  onShareAppMessage() {
    return {
      title: '能耗翻译官 — 合作售电分润',
      path: '/pages/partner/partner'
    };
  }
});
