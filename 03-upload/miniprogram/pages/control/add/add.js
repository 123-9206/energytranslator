// pages/control/add/add.js - 添加设备
const httpApi = require('../../../utils/api-http.js').httpApi;

Page({
  data: {
    submitting: false,
    name: '',
    type: '',
    typeIndex: 0,
    // 仅电力相关设备类型（用户要求：不需要非电力类型）
    // 总进线/分路出线=配电计量点，其余为典型用电设备
    types: [
      '总进线', '分路出线', '变压器', '厂房电器', '空调系统',
      '照明系统', '制冷机组', '空压机', '水泵', '电机', '风机',
      '电梯', '充电桩', '电热设备', '储能柜', '光伏逆变器', '其他电力设备'
    ],
    protocol: '',
    protocolIndex: 0,
    protocols: [
      { value: 'mqtt', label: 'MQTT' },
      { value: 'modbus', label: 'Modbus' },
      { value: 'http', label: 'HTTP' }
    ],
    siteId: '',
    ipAddress: '',
    port: '',
    model: '',
    manufacturer: ''
  },

  onNameInput(e) {
    this.setData({ name: e.detail.value });
  },

  onTypeChange(e) {
    const idx = Number(e.detail.value);
    this.setData({ typeIndex: idx, type: this.data.types[idx] });
  },

  onProtocolChange(e) {
    const idx = Number(e.detail.value);
    this.setData({ protocolIndex: idx, protocol: this.data.protocols[idx].value });
  },

  onSiteInput(e) {
    this.setData({ siteId: e.detail.value });
  },

  onIpInput(e) {
    this.setData({ ipAddress: e.detail.value });
  },

  onPortInput(e) {
    this.setData({ port: e.detail.value });
  },

  onModelInput(e) {
    this.setData({ model: e.detail.value });
  },

  onManufacturerInput(e) {
    this.setData({ manufacturer: e.detail.value });
  },

  onSubmit() {
    const { name, type, protocol, siteId, ipAddress, port, model, manufacturer } = this.data;

    if (!name.trim()) {
      wx.showToast({ title: '请填写设备名称', icon: 'none' });
      return;
    }
    if (!siteId.trim()) {
      wx.showToast({ title: '请填写安装位置', icon: 'none' });
      return;
    }

    const deviceData = {
      name: name.trim(),
      type: type || '其他电力设备',
      protocol: protocol || 'mqtt',
      siteId: siteId.trim(),
      status: 'offline'
    };
    if (ipAddress.trim()) deviceData.ipAddress = ipAddress.trim();
    if (port) deviceData.port = Number(port);
    if (model.trim()) deviceData.model = model.trim();
    if (manufacturer.trim()) deviceData.manufacturer = manufacturer.trim();

    this.setData({ submitting: true });
    httpApi.createDevice(deviceData).then(() => {
      wx.showToast({ title: '添加成功', icon: 'success' });
      setTimeout(() => {
        // 页面栈有上一页才 navigateBack，否则回退到设备列表页
        const pages = getCurrentPages();
        if (pages.length > 1) {
          wx.navigateBack();
        } else {
          wx.navigateTo({ url: '/pages/control/control' });
        }
      }, 800);
    }).catch(() => {
      this.setData({ submitting: false });
    });
  }
});
