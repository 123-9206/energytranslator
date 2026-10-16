// pages/vpp/earnings/earnings.js
const httpApi = require('../../../utils/api-http.js').httpApi;

Page({
  data: {
    loading: true,
    totalEarnings: 0,
    monthEarnings: 0,
    selectedMonth: '',
    earningsList: [],
    months: [],
    currentYearMonth: ''
  },

  onLoad() {
    this.checkLogin();
    const now = new Date();
    const ym = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
    this.setData({ currentYearMonth: ym, selectedMonth: ym });
  },

  onShow() {
    // 未登录时直接回"我的"页，不发请求（避免 401 竞态与重复跳转）
    const token = wx.getStorageSync('token');
    if (!token) {
      wx.switchTab({ url: '/pages/profile/profile' });
      return;
    }
    this.loadEarnings();
  },

  checkLogin() {
    const token = wx.getStorageSync('token');
    if (!token) {
      wx.switchTab({ url: '/pages/profile/profile' });
    }
  },

  loadEarnings() {
    this.setData({ loading: true });
    httpApi.getMySettlements({ page: 1, pageSize: 20 }).then(res => {
      const list = (res && (res.content || res.list)) || [];
      const totalEarnings = list.reduce((sum, item) => {
        const summary = item.summary || {};
        return sum + (Number(summary.totalAmount) || 0);
      }, 0);
      // 本月结算
      const monthItems = list.filter(item => (item.period || '') === this.data.selectedMonth);
      const monthEarnings = monthItems.reduce((sum, item) => {
        const summary = item.summary || {};
        return sum + (Number(summary.totalAmount) || 0);
      }, 0);

      this.setData({
        earningsList: list,
        totalEarnings: totalEarnings.toFixed(2),
        monthEarnings: monthEarnings.toFixed(2),
        loading: false
      });
    }).catch(() => {
      this.setData({ loading: false });
      wx.showToast({ title: '加载失败', icon: 'none' });
    });
  },

  onItemTap(e) {
    const id = e.currentTarget.dataset.id;
    if (!id) return;
    wx.navigateTo({ url: '/pages/vpp/settlement/settlement?id=' + id });
  },

  onGenerateSettlement() {
    wx.showModal({
      title: '生成结算单',
      content: `为本月（${this.data.selectedMonth}）生成VPP收益结算单？`,
      success: (res) => {
        if (res.confirm) {
          wx.showLoading({ title: '生成中...' });
          httpApi.generateSettlement(this.data.selectedMonth).then(() => {
            wx.hideLoading();
            wx.showToast({ title: '结算单已生成', icon: 'success' });
            this.loadEarnings();
          }).catch(() => {
            wx.hideLoading();
            wx.showToast({ title: '本期暂无可结算活动', icon: 'none' });
          });
        }
      }
    });
  },

  onMonthChange(e) {
    this.setData({ selectedMonth: e.detail.value });
  },

  // 状态文案
  statusText(status) {
    const map = { pending: '待审批', approved: '已审批', paid: '已付款', withdrawn: '已提现' };
    return map[status] || status || '未知';
  }
});
