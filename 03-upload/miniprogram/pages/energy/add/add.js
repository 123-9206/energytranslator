// pages/energy/add/add.js
const httpApi = require('../../../utils/api-http.js').httpApi;

Page({
  data: {
    formData: {
      recordDate: '',
      type: 'electricity',
      value: '',
      unit: 'kWh',
      note: ''
    },
    types: [
      { value: 'electricity', label: '电力', unit: 'kWh' },
      { value: 'gas', label: '天然气', unit: 'm³' },
      { value: 'water', label: '用水', unit: '吨' }
    ],
    typeIndex: 0,
    submitting: false,
    today: ''
  },

  onLoad() {
    this.checkLogin();
    const today = this.formatDate(new Date());
    this.setData({ 'formData.recordDate': today, today });
  },

  checkLogin() {
    const token = wx.getStorageSync('token');
    if (!token) {
      wx.switchTab({ url: '/pages/profile/profile' });
    }
  },

  formatDate(date) {
    const y = date.getFullYear();
    const m = String(date.getMonth() + 1).padStart(2, '0');
    const d = String(date.getDate()).padStart(2, '0');
    return `${y}-${m}-${d}`;
  },

  onDateChange(e) {
    this.setData({ 'formData.recordDate': e.detail.value });
  },

  onTypeChange(e) {
    const idx = Number(e.detail.value);
    const type = this.data.types[idx];
    this.setData({
      typeIndex: idx,
      'formData.type': type.value,
      'formData.unit': type.unit,
      'formData.value': ''
    });
  },

  onValueInput(e) {
    this.setData({ 'formData.value': e.detail.value });
  },

  onNoteInput(e) {
    this.setData({ 'formData.note': e.detail.value });
  },

  validate() {
    const { recordDate, value } = this.data.formData;
    if (!recordDate) {
      wx.showToast({ title: '请选择日期', icon: 'none' });
      return false;
    }
    if (!value || Number(value) <= 0) {
      wx.showToast({ title: '请输入有效的数值', icon: 'none' });
      return false;
    }
    return true;
  },

  onSubmit() {
    if (this.data.submitting) return;
    if (!this.validate()) return;

    this.setData({ submitting: true });

    httpApi.addEnergyRecord(this.data.formData).then(() => {
      wx.showToast({ title: '添加成功', icon: 'success' });
      setTimeout(() => {
        // 页面栈有上一页才 navigateBack，否则回退到能耗页（tabBar 页用 switchTab）
        const pages = getCurrentPages();
        if (pages.length > 1) {
          wx.navigateBack();
        } else {
          wx.switchTab({ url: '/pages/energy/energy' });
        }
      }, 1500);
    }).catch(() => {
      wx.showToast({ title: '添加失败，请重试', icon: 'none' });
    }).finally(() => {
      this.setData({ submitting: false });
    });
  }
});
