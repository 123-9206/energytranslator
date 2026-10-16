// pages/carbon/carbon.js
const httpApi = require('../../utils/api-http.js').httpApi;

Page({
  data: {
    loading: true,
    period: 'month',
    periods: [
      { value: 'week', label: '本周' },
      { value: 'month', label: '本月' },
      { value: 'year', label: '本年' }
    ],
    periodIndex: 1,
    report: {
      totalCarbon: 0,
      carbonReduction: 0,
      vppEarnings: 0,
      carbonCredits: 0,
      reductionRate: 0
    },
    trend: [],
    maxTrend: 1,
    suggestions: []
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
    this.setData({ loading: true });

    httpApi.getCarbonReport(this.data.period).then(res => {
      const data = res.data || res;
      // 后端返回: totalCarbon/todayCarbon/monthCarbon/yearCarbon/trends[{date,value}]
      const trendRaw = data.trends || data.trend || [];
      const trend = trendRaw.map(t => ({
        date: t.date,
        label: t.label || t.date,
        value: Number((t.value || 0).toFixed(1))
      }));
      const maxTrend = Math.max.apply(null, trend.map(t => t.value).concat([1]));
      this.setData({
        report: {
          totalCarbon: data.totalCarbon || 0,
          carbonReduction: data.carbonReduction || 0,
          vppEarnings: data.vppEarnings || 0,
          carbonCredits: data.carbonCredits || 0,
          reductionRate: data.reductionRate || 0
        },
        trend,
        maxTrend,
        suggestions: data.suggestions || this.getDefaultSuggestions(),
        loading: false
      });
    }).catch(() => {
      this.setData({
        loading: false,
        suggestions: this.getDefaultSuggestions()
      });
    });
  },

  getDefaultSuggestions() {
    return [
      '优化生产排程，减少设备空载运行时间',
      '推广使用节能设备，降低单位产品能耗',
      '合理安排峰谷用电，降低用能成本',
      '参与VPP需求响应活动，获取额外补贴收益'
    ];
  },

  onPeriodChange(e) {
    const idx = Number(e.detail.value);
    this.setData({
      periodIndex: idx,
      period: this.data.periods[idx].value
    });
    this.loadData();
  }
});
