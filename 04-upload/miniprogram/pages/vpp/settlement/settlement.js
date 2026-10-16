// pages/vpp/settlement/settlement.js
const httpApi = require('../../../utils/api-http.js').httpApi;

Page({
  data: {
    loading: true,
    settlementId: '',
    settlement: null,
    details: [],
    canWithdraw: false
  },

  onLoad(options) {
    if (!this.checkLogin()) return;
    if (options.id) {
      this.setData({ settlementId: options.id });
      this.loadDetail();
    }
  },

  checkLogin() {
    const token = wx.getStorageSync('token');
    if (!token) {
      wx.navigateTo({ url: '/pages/profile/login/login' });
      return false;
    }
    return true;
  },

  loadDetail() {
    this.setData({ loading: true });

    httpApi.getSettlementDetail(this.data.settlementId).then(res => {
      const data = res.data || res;
      const summary = data.summary || {};

      this.setData({
        settlement: {
          id: data.id,
          period: data.period,
          status: data.status,
          startDate: data.startDate,
          endDate: data.endDate,
          totalCapacityCompensation: summary.totalCapacityCompensation || 0,
          totalEnergyCompensation: summary.totalEnergyCompensation || 0,
          totalCompensation: summary.totalCompensation || data.totalAmount || 0,
          totalCarbonReduction: summary.totalCarbonReduction || data.totalCarbonRed || 0,
          responseCount: summary.totalResponseCount || data.responseCount || 0,
          completedCount: summary.completedResponseCount || data.completedCount || 0,
          avgResponseRate: summary.avgResponseRate || data.avgResponseRate || 0,
          createdAt: data.createdAt,
          remarks: data.remarks || ''
        },
        canWithdraw: data.status === 'paid',
        loading: false
      });
    }).catch(() => {
      wx.showToast({ title: '加载结算详情失败', icon: 'none' });
      this.setData({ loading: false });
    });
  },

  onWithdraw() {
    if (!this.data.settlementId) return;
    wx.showModal({
      title: '申请提现',
      content: '确认将本结算单收益提现至绑定账户？',
      success: (res) => {
        if (res.confirm) {
          wx.showLoading({ title: '提交中...' });
          httpApi.withdrawSettlement(this.data.settlementId)
            .then(() => {
              wx.hideLoading();
              wx.showToast({ title: '提现申请已提交', icon: 'success' });
              this.setData({ canWithdraw: false });
              this.loadDetail();
            })
            .catch(() => {
              wx.hideLoading();
              wx.showToast({ title: '提现失败', icon: 'none' });
            });
        }
      }
    });
  },

  formatCurrency(value) {
    const num = Number(value);
    return isNaN(num) ? '0.00' : num.toFixed(2);
  },

  getStatusText(status) {
    const map = {
      'pending': '待结算',
      'approved': '已确认',
      'paid': '已支付',
      'withdrawn': '已提现',
      'cancelled': '已取消'
    };
    return map[status] || status || '未知';
  }
});
