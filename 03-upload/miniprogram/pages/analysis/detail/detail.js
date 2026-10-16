// pages/analysis/detail/detail.js
const httpApi = require('../../../utils/api-http.js').httpApi;

Page({
  data: {
    loading: true,
    date: '',
    period: '',
    records: [],
    summary: {
      energy: 0,
      carbon: 0,
      cost: 0
    },
    prevSummary: {
      energy: 0,
      carbon: 0,
      cost: 0
    },
    energyChange: 0,
    carbonChange: 0,
    suggestions: []
  },

  onLoad(options) {
    this.checkLogin();
    if (options.date) this.setData({ date: options.date });
    if (options.period) this.setData({ period: options.period });
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

    const params = {
      startDate: this.data.date,
      endDate: this.data.date,
      page: 1,
      size: 1000
    };

    httpApi.getEnergyRecords(params).then(res => {
      const data = res.data || res;
      const list = data.list || data.records || [];

      let energy = 0, carbon = 0, cost = 0;
      list.forEach(item => {
        energy += Number(item.value) || 0;
        carbon += Number(item.carbon) || 0;
        cost += Number(item.cost) || 0;
      });

      const suggestions = this.generateSuggestions(energy, carbon, cost);

      this.setData({
        records: list,
        summary: { energy, carbon, cost },
        prevSummary: this.calcPrevSummary(),
        energyChange: this.data.summary.energy ? ((energy - this.data.summary.energy) / this.data.summary.energy * 100).toFixed(1) : 0,
        carbonChange: this.data.summary.carbon ? ((carbon - this.data.summary.carbon) / this.data.summary.carbon * 100).toFixed(1) : 0,
        suggestions,
        loading: false
      });
    }).catch(() => {
      this.setData({ loading: false });
    });
  },

  calcPrevSummary() {
    return { energy: 0, carbon: 0, cost: 0 };
  },

  generateSuggestions(energy, carbon, cost) {
    const tips = [];
    if (energy > 500) {
      tips.push('当前能耗较高，建议检查设备运行状态');
    }
    if (carbon > 200) {
      tips.push('碳排放偏高，建议优化用能结构');
    }
    if (tips.length === 0) {
      tips.push('能耗状况良好，请继续保持');
      tips.push('建议参与VPP活动获取额外收益');
    }
    return tips;
  }
});
