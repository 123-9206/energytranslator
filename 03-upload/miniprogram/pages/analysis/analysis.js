// pages/analysis/analysis.js
const httpApi = require('../../utils/api-http.js').httpApi;

Page({
  data: {
    loading: true,
    period: 'day',
    periods: [
      { value: 'day', label: '日' },
      { value: 'week', label: '周' },
      { value: 'month', label: '月' },
      { value: 'year', label: '年' }
    ],
    periodIndex: 0,
    records: [],
    summary: {
      totalEnergy: 0,
      totalCarbon: 0,
      totalCost: 0,
      avgEnergy: 0
    },
    topConsumers: [],
    chartData: [],
    maxValue: 1
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
    const params = { period: this.data.period, page: 1, size: 100 };

    Promise.all([
      httpApi.getEnergyRecords(params),
      httpApi.getDashboard(this.data.period)
    ]).then(([recordsRes, dashRes]) => {
      const recordsData = recordsRes.data || recordsRes;
      const dashData = dashRes.data || dashRes;
      const list = recordsData.list || recordsData.records || [];

      // 汇总从记录真实求和（后端 dashboard 无 totalEnergy/totalCarbon/totalCost 字段）
      const totalEnergy = list.reduce((s, it) => s + (Number(it.value) || 0), 0);
      const totalCarbon = list.reduce((s, it) => s + (Number(it.carbonEmission) || 0), 0);
      // 成本字段映射：dashboard 返回 todayCost / monthCost
      const costMap = { day: 'todayCost', week: 'monthCost', month: 'monthCost', year: 'monthCost' };
      const totalCost = Number(dashData[costMap[this.data.period]] || dashData.monthCost || 0);
      const avgEnergy = list.length > 0 ? (totalEnergy / this.getUniqueDays(list)).toFixed(1) : 0;

      // Build chart data from records
      const chartData = this.buildChartData(list);
      const topConsumers = this.buildTopConsumers(list);

      this.setData({
        records: list,
        summary: { totalEnergy, totalCarbon, totalCost, avgEnergy },
        chartData,
        topConsumers,
        maxValue: Math.max(...chartData.map(d => d.value), 1),
        loading: false
      });
    }).catch(() => {
      this.setData({ loading: false });
    });
  },

  getUniqueDays(list) {
    const days = new Set();
    list.forEach(item => {
      days.add(this.getRecordDate(item));
    });
    return days.size || 1;
  },

  // 兼容后端返回的 timestamp / recordDate / date 字段
  getRecordDate(item) {
    const raw = item.timestamp || item.recordDate || item.date || '';
    return raw.substring(0, 10);
  },

  buildChartData(list) {
    const grouped = {};
    list.forEach(item => {
      const date = this.getRecordDate(item);
      if (!date) return;
      if (!grouped[date]) grouped[date] = 0;
      grouped[date] += Number(item.value) || 0;
    });

    return Object.keys(grouped).sort().map(date => ({
      date,
      label: date.substring(5),
      value: Number(grouped[date].toFixed(1))
    }));
  },

  buildTopConsumers(list) {
    const devices = {};
    list.forEach(item => {
      const name = item.deviceName || item.name || '其他';
      if (!devices[name]) devices[name] = 0;
      devices[name] += Number(item.value) || 0;
    });

    return Object.entries(devices)
      .sort((a, b) => b[1] - a[1])
      .slice(0, 5)
      .map(([name, value], index) => ({
        rank: index + 1,
        name,
        value: Number(value.toFixed(1))
      }));
  },

  onPeriodChange(e) {
    const idx = Number(e.detail.value);
    this.setData({
      periodIndex: idx,
      period: this.data.periods[idx].value
    });
    this.loadData();
  },

  goToDetail(e) {
    const date = e.currentTarget.dataset.date;
    wx.navigateTo({ url: `/pages/analysis/detail/detail?date=${date}&period=${this.data.period}` });
  }
});
