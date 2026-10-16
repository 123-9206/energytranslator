// pages/quota/quota.js
const httpApi = require('../../utils/api-http.js').httpApi;

Page({
  data: {
    loading: true,
    quota: {
      year: new Date().getFullYear(),
      allocated: 0,
      purchased: 0,
      consumed: 0,
      total: 0,
      remaining: 0,
      alertThreshold: 80,
      usageRate: 0,
      statusText: '正常',
      warning: ''
    },
    history: []
  },

  onLoad() {
    if (!this.checkLogin()) return;
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
      const [quotaRes, historyRes] = await Promise.all([
        httpApi.getCurrentQuota().catch(() => null),
        httpApi.getQuotaHistory().catch(() => [])
      ]);

      // 处理当前配额
      if (quotaRes) {
        const q = quotaRes;
        const allocated = q.allocatedQuota || 0;
        const purchased = q.purchasedQuota || 0;
        const consumed = q.consumedQuota || 0;
        const total = allocated + purchased;
        const remaining = Math.max(0, total - consumed);
        const usageRate = total > 0 ? Math.round((consumed / total) * 100) : 0;
        const threshold = q.alertThreshold || 80;

        let statusText = '正常';
        let warning = '';
        if (usageRate >= 100) {
          statusText = '超额';
          warning = '配额已用完！请联系管理员追加配额或购买碳汇。';
        } else if (usageRate >= threshold) {
          statusText = '预警';
          warning = `配额使用率已达${usageRate}%，超过预警阈值(${threshold}%)，建议提前购买碳汇。`;
        }

        this.setData({
          quota: {
            year: q.year || new Date().getFullYear(),
            allocated: allocated.toFixed(1),
            purchased: purchased.toFixed(1),
            consumed: consumed.toFixed(1),
            total: total.toFixed(1),
            remaining: remaining.toFixed(1),
            alertThreshold: threshold,
            usageRate: usageRate,
            statusText: statusText,
            warning: warning
          }
        });
      }

      // 处理历史记录
      const historyList = (Array.isArray(historyRes) ? historyRes : []);
      const history = historyList.map(h => ({
        id: h.id,
        year: h.year,
        allocatedQuota: (h.allocatedQuota || 0).toFixed(1),
        purchasedQuota: (h.purchasedQuota || 0).toFixed(1),
        consumedQuota: (h.consumedQuota || 0).toFixed(1),
        usageRate: ((h.allocatedQuota || 0) + (h.purchasedQuota || 0)) > 0
          ? Math.round((h.consumedQuota || 0) / ((h.allocatedQuota || 0) + (h.purchasedQuota || 0)) * 100)
          : 0
      }));

      this.setData({ history, loading: false });
    } catch (e) {
      console.error('加载碳配额数据失败:', e);
      this.setData({ loading: false });
      wx.showToast({ title: '加载失败', icon: 'none' });
    }
  },

  onShareAppMessage() {
    return {
      title: '能耗翻译官 — 碳配额管理',
      path: '/pages/quota/quota'
    };
  }
});
