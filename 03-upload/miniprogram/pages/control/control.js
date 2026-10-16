// pages/control/control.js
const httpApi = require('../../utils/api-http.js').httpApi;

Page({
  data: {
    loading: true,
    devices: [],
    selectedDevice: null,
    showControl: false,
    controlValue: '',
    controlOn: true,
    statusLabels: {
      online: '在线',
      offline: '离线'
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
    this.loadDevices();
  },

  checkLogin() {
    const token = wx.getStorageSync('token');
    if (!token) {
      wx.switchTab({ url: '/pages/profile/profile' });
    }
  },

  loadDevices() {
    this.setData({ loading: true });
    httpApi.getDevices().then(res => {
      const data = res.data || res;
      // 后端返回 Spring Boot Page 格式: {content: [...], totalElements, ...}
      const list = Array.isArray(data)
        ? data
        : (data.content || data.list || data.records || []);
      this.setData({ devices: list, loading: false });
    }).catch(() => {
      this.setData({ loading: false });
      wx.showToast({ title: '加载失败', icon: 'none' });
    });
  },

  onAddDevice() {
    wx.navigateTo({ url: '/pages/control/add/add' });
  },

  onDeviceTap(e) {
    const id = e.currentTarget.dataset.id;
    const device = this.data.devices.find(d => d.id === id);
    if (!device || device.status !== 'online') return;

    this.setData({
      selectedDevice: device,
      showControl: true,
      controlOn: device.state !== 'off',
      controlValue: device.currentValue || ''
    });
  },

  onCloseControl() {
    this.setData({ showControl: false, selectedDevice: null });
  },

  onToggleSwitch(e) {
    this.setData({ controlOn: e.detail.value });
  },

  onControlValueInput(e) {
    this.setData({ controlValue: e.detail.value });
  },

  onSendControl() {
    if (!this.data.selectedDevice) return;

    const params = {
      deviceId: this.data.selectedDevice.id,
      command: {
        action: this.data.controlOn ? 'on' : 'off',
        value: this.data.controlValue
      }
    };

    wx.showModal({
      title: '确认控制',
      content: `确定要${this.data.controlOn ? '开启' : '关闭'} ${this.data.selectedDevice.name} 吗？`,
      success: (res) => {
        if (res.confirm) {
          httpApi.controlDevice(params.deviceId, params.command)
            .then(() => {
              wx.showToast({ title: '指令已发送', icon: 'success' });
              this.setData({ showControl: false });
              this.loadDevices();
            })
            .catch(() => {
              wx.showToast({ title: '控制失败', icon: 'none' });
            });
        }
      }
    });
  },

  onRefresh() {
    this.loadDevices();
  }
});
