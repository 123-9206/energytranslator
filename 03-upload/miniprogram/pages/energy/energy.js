// pages/energy/energy.js
const httpApi = require('../../utils/api-http.js').httpApi;

Page({
  data: {
    loading: true,
    records: [],
    page: 1,
    size: 20,
    total: 0,
    noMore: false,
    filterType: '',
    filterStart: '',
    filterEnd: '',
    currentTypeLabel: '全部类型',
    types: [
      { value: '', label: '全部类型' },
      { value: 'electricity', label: '电力' },
      { value: 'gas', label: '天然气' },
      { value: 'water', label: '用水' }
    ],
    typeLabels: {
      electricity: '电力',
      gas: '天然气',
      water: '用水'
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
    this.setData({ page: 1, records: [], noMore: false });
    this.loadRecords();
  },

  checkLogin() {
    const token = wx.getStorageSync('token');
    if (!token) {
      wx.switchTab({ url: '/pages/profile/profile' });
      return false;
    }
    return true;
  },

  loadRecords() {
    if (this.data.noMore) return;
    this.setData({ loading: true });

    const params = {
      page: this.data.page,
      size: this.data.size
    };
    if (this.data.filterType) params.type = this.data.filterType;
    if (this.data.filterStart) params.startDate = this.data.filterStart;
    if (this.data.filterEnd) params.endDate = this.data.filterEnd;

    httpApi.getEnergyRecords(params).then(res => {
      const data = res.data || res;
      const list = data.list || data.records || [];
      const total = data.total || 0;
      // 后端返回 timestamp(ISO) 字段，加工为展示用的 displayDate
      list.forEach(item => {
        const raw = item.timestamp || item.recordDate || item.date || '';
        item.displayDate = raw.substring(0, 10);
      });
      const newRecords = this.data.page === 1 ? list : this.data.records.concat(list);

      this.setData({
        records: newRecords,
        total,
        noMore: newRecords.length >= total,
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
      this.loadRecords();
    }
  },

  onPullDownRefresh() {
    this.setData({ page: 1, records: [], noMore: false });
    this.loadRecords();
    wx.stopPullDownRefresh();
  },

  onTypeChange(e) {
    const idx = e.detail.value;
    const type = this.data.types[idx];
    this.setData({
      filterType: type.value,
      currentTypeLabel: type.label,
      page: 1,
      records: [],
      noMore: false
    });
    this.loadRecords();
  },

  onStartChange(e) {
    this.setData({ filterStart: e.detail.value });
  },

  onEndChange(e) {
    this.setData({ filterEnd: e.detail.value });
  },

  onFilter() {
    this.setData({ page: 1, records: [], noMore: false });
    this.loadRecords();
  },

  onResetFilter() {
    this.setData({
      filterType: '',
      filterStart: '',
      filterEnd: '',
      currentTypeLabel: '全部类型',
      page: 1,
      records: [],
      noMore: false
    });
    this.loadRecords();
  },

  goToAdd() {
    wx.navigateTo({ url: '/pages/energy/add/add' });
  },

  goToAddRecord() {
    wx.navigateTo({ url: '/pages/energy/add/add' });
  }
});
