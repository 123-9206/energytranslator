// pages/green/add/add.js
const httpApi = require('../../../utils/api-http.js').httpApi;

Page({
  data: {
    form: {
      supplierName: '',
      energyVolume: '',
      pricePremium: '0.03',
      startDate: '',
      endDate: '',
      notes: ''
    },
    submitting: false,
    today: ''
  },

  onLoad() {
    const now = new Date();
    const pad = (n) => String(n).padStart(2, '0');
    const today = `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
    const endOfYear = `${now.getFullYear()}-12-31`;
    this.setData({
      today,
      'form.startDate': today,
      'form.endDate': endOfYear
    });
  },

  onInput(e) {
    const field = e.currentTarget.dataset.field;
    this.setData({ [`form.${field}`]: e.detail.value });
  },

  onStartDateChange(e) {
    this.setData({ 'form.startDate': e.detail.value });
  },

  onEndDateChange(e) {
    this.setData({ 'form.endDate': e.detail.value });
  },

  validate() {
    const f = this.data.form;
    if (!f.supplierName.trim()) return '请填写绿电供应商（如风电场名称）';
    if (!f.energyVolume || Number(f.energyVolume) <= 0) return '请填写合同电量';
    if (!f.startDate) return '请选择开始日期';
    if (!f.endDate) return '请选择结束日期';
    return '';
  },

  onSubmit() {
    const err = this.validate();
    if (err) {
      wx.showToast({ title: err, icon: 'none' });
      return;
    }
    if (this.data.submitting) return;
    this.setData({ submitting: true });

    const f = this.data.form;
    httpApi.createGreenPowerContract({
      supplierName: f.supplierName.trim(),
      energyVolume: Number(f.energyVolume),
      pricePremium: Number(f.pricePremium) || 0,
      startDate: f.startDate,
      endDate: f.endDate,
      notes: f.notes,
      status: 'active'
    }).then(() => {
      this.setData({ submitting: false });
      wx.showToast({ title: '合同创建成功', icon: 'success' });
      setTimeout(() => {
        // 页面栈有上一页才 navigateBack，否则回退到绿电台账页
        const pages = getCurrentPages();
        if (pages.length > 1) {
          wx.navigateBack();
        } else {
          wx.navigateTo({ url: '/pages/green/green' });
        }
      }, 1200);
    }).catch((errMsg) => {
      this.setData({ submitting: false });
      wx.showToast({ title: errMsg || '创建失败', icon: 'none' });
    });
  }
});
