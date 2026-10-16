// pages/vpp/add/add.js
const httpApi = require('../../../utils/api-http.js').httpApi;

Page({
  data: {
    submitting: false,
    selectedTypeIndex: 0,
    resourceTypes: [
      { value: 'storage', label: '储能设备' },
      { value: 'solar', label: '光伏发电' },
      { value: 'adjustable_load', label: '可调负荷' },
      { value: 'generator', label: '发电机' },
      { value: 'wind', label: '风力发电' },
      { value: 'diesel', label: '柴油发电' },
      { value: 'grid', label: '电网关口' },
    ],
    form: {
      name: '',
      type: 'storage',
      ratedPower: '',
      location: '',
      socMin: '',
      socMax: '',
      ratedCapacity: '',
      panelCapacity: '',
      responseTime: '',
      continuousTime: '',
    },
    devices: [],
    selectedDeviceId: '',
  },

  onLoad() {
    if (!this.checkLogin()) return;
    // 加载已有设备列表供关联
    this.loadDevices();
  },

  checkLogin() {
    const token = wx.getStorageSync('token');
    if (!token) {
      wx.navigateTo({ url: '/pages/profile/login/login' });
      return false;
    }
    return true;
  },

  loadDevices() {
    httpApi.getDevices({ page: 1, size: 100 }).then(res => {
      const data = res.data || res;
      const list = data.list || data.records || (Array.isArray(data) ? data : []);
      this.setData({ devices: list });
    }).catch(() => {
      wx.showToast({ title: '设备列表加载失败', icon: 'none' });
    });
  },

  onTypeChange(e) {
    const index = e.detail.value;
    const type = this.data.resourceTypes[index].value;
    this.setData({
      selectedTypeIndex: index,
      'form.type': type
    });
  },

  onFieldInput(e) {
    const { field } = e.currentTarget.dataset;
    this.setData({ [`form.${field}`]: e.detail.value });
  },

  onDeviceChange(e) {
    this.setData({ selectedDeviceId: this.data.devices[e.detail.value]?.id || '' });
  },

  validateForm() {
    const { name, type, ratedPower } = this.data.form;
    if (!name.trim()) {
      wx.showToast({ title: '请输入资源名称', icon: 'none' });
      return false;
    }
    if (!type) {
      wx.showToast({ title: '请选择资源类型', icon: 'none' });
      return false;
    }
    if (!ratedPower || Number(ratedPower) <= 0) {
      wx.showToast({ title: '请输入有效的额定功率', icon: 'none' });
      return false;
    }
    return true;
  },

  submit() {
    if (!this.validateForm()) return;

    this.setData({ submitting: true });
    const { name, type, ratedPower, location, socMin, socMax, ratedCapacity, panelCapacity, responseTime, continuousTime } = this.data.form;

    const payload = {
      name: name.trim(),
      type,
      ratedPower: Number(ratedPower),
    };

    if (location.trim()) payload.location = location.trim();
    if (responseTime) payload.responseTime = Number(responseTime);
    if (continuousTime) payload.continuousTime = Number(continuousTime);

    // 储能专用字段
    if (type === 'storage') {
      if (socMin) payload.socMin = Number(socMin);
      if (socMax) payload.socMax = Number(socMax);
      if (ratedCapacity) payload.ratedCapacity = Number(ratedCapacity);
    }

    // 光伏专用字段
    if (type === 'solar') {
      if (panelCapacity) payload.panelCapacity = Number(panelCapacity);
    }

    // 关联设备
    if (this.data.selectedDeviceId) {
      payload.deviceId = this.data.selectedDeviceId;
    }

    httpApi.registerResource(payload).then(res => {
      wx.showToast({ title: '资源添加成功', icon: 'success' });
      setTimeout(() => {
        wx.navigateBack();
      }, 1500);
    }).catch(err => {
      wx.showToast({ title: '添加失败: ' + (err.message || '网络错误'), icon: 'none' });
    }).finally(() => {
      this.setData({ submitting: false });
    });
  },

  cancel() {
    wx.navigateBack();
  }
});
