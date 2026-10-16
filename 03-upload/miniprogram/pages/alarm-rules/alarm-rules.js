// pages/alarm-rules/alarm-rules.js
const httpApi = require('../../utils/api-http.js').httpApi;

Page({
  data: {
    loading: true,
    rules: [],
    showForm: false,
    editing: false,
    editId: null,
    // 预警设备：从已添加设备列表中选择（自行添加的设备直接可选）
    devices: [],
    deviceNames: ['所有设备（全局规则）'],
    deviceIndex: 0,
    formData: {
      name: '',
      deviceName: '',
      deviceId: '',
      metric: 'energy',
      condition: 'gt',
      threshold: '',
      severity: 'warning',
      enabled: true
    },
    metrics: [
      { value: 'energy', label: '能耗值' },
      { value: 'power', label: '功率' },
      { value: 'temperature', label: '温度' },
      { value: 'carbon', label: '碳排放' }
    ],
    conditions: [
      { value: 'gt', label: '大于' },
      { value: 'lt', label: '小于' },
      { value: 'eq', label: '等于' }
    ],
    severities: [
      { value: 'warning', label: '警告' },
      { value: 'critical', label: '严重' },
      { value: 'info', label: '提示' }
    ],
    metricIndex: 0,
    conditionIndex: 0,
    severityIndex: 0,
    conditionLabels: {
      gt: '>',
      lt: '<',
      eq: '='
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
    this.loadRules();
    this.loadDevices();
  },

  checkLogin() {
    const token = wx.getStorageSync('token');
    if (!token) {
      wx.switchTab({ url: '/pages/profile/profile' });
    }
  },

  // 加载已添加设备列表，供"预警设备"选择（设备在设备控制页自行添加）
  loadDevices() {
    httpApi.getDevices().then(res => {
      const data = res.data || res;
      // 后端返回 Spring Boot Page 格式: {content: [...], totalElements, ...}
      const list = Array.isArray(data)
        ? data
        : (data.content || data.list || data.records || []);
      const devices = list.filter(d => d && d.id);
      this.setData({
        devices,
        deviceNames: ['所有设备（全局规则）'].concat(devices.map(d => `${d.name}${d.siteId ? '（' + d.siteId + '）' : ''}`))
      });
    }).catch(() => {
      // 设备列表加载失败不阻断规则页，仍可手输/全局规则
      this.setData({ devices: [], deviceNames: ['所有设备（全局规则）'] });
    });
  },

  loadRules() {
    this.setData({ loading: true });

    httpApi.getAlarmRules({ page: 1, size: 50 }).then(res => {
      const list = res.list || res.data || [];
      this.setData({
        rules: list,
        loading: false
      });
    }).catch(err => {
      console.error('加载告警规则失败:', err);
      this.setData({ rules: [], loading: false });
      wx.showToast({ title: '加载失败', icon: 'none' });
    });
  },

  onAddRule() {
    this.setData({
      showForm: true,
      editing: false,
      editId: null,
      deviceIndex: 0,
      formData: {
        name: '',
        deviceName: '',
        deviceId: '',
        metric: 'energy',
        condition: 'gt',
        threshold: '',
        severity: 'warning',
        enabled: true
      },
      metricIndex: 0,
      conditionIndex: 0,
      severityIndex: 0
    });
  },

  onEditRule(e) {
    const id = e.currentTarget.dataset.id;
    const rule = this.data.rules.find(r => r.id === id);
    if (!rule) return;

    const metricIdx = this.data.metrics.findIndex(m => m.value === rule.metric);
    const condIdx = this.data.conditions.findIndex(c => c.value === rule.condition);
    const sevIdx = this.data.severities.findIndex(s => s.value === rule.severity);

    // 根据规则绑定的 deviceId 回填设备选择器下标（0=所有设备/全局规则）
    let deviceIdx = 0;
    if (rule.deviceId) {
      const idx = this.data.devices.findIndex(d => d.id === rule.deviceId);
      if (idx >= 0) deviceIdx = idx + 1; // 第0项是"所有设备"
    }

    this.setData({
      showForm: true,
      editing: true,
      editId: id,
      deviceIndex: deviceIdx,
      formData: { ...rule },
      metricIndex: metricIdx >= 0 ? metricIdx : 0,
      conditionIndex: condIdx >= 0 ? condIdx : 0,
      severityIndex: sevIdx >= 0 ? sevIdx : 0
    });
  },

  onDeleteRule(e) {
    const id = e.currentTarget.dataset.id;
    wx.showModal({
      title: '确认删除',
      content: '确定要删除这条告警规则吗？',
      success: (res) => {
        if (res.confirm) {
          httpApi.deleteAlarmRule(id).then(() => {
            this.setData({
              rules: this.data.rules.filter(r => r.id !== id)
            });
            wx.showToast({ title: '已删除', icon: 'success' });
          }).catch(err => {
            console.error('删除告警规则失败:', err);
            wx.showToast({ title: '删除失败', icon: 'none' });
          });
        }
      }
    });
  },

  onToggleRule(e) {
    const id = e.currentTarget.dataset.id;
    const currentRule = this.data.rules.find(r => r.id === id);
    if (!currentRule) return;

    httpApi.toggleAlarmRule(id).then(() => {
      this.setData({
        rules: this.data.rules.map(r => {
          if (r.id === id) {
            return { ...r, enabled: !r.enabled };
          }
          return r;
        })
      });
    }).catch(err => {
      console.error('切换规则状态失败:', err);
      wx.showToast({ title: '操作失败', icon: 'none' });
    });
  },

  onMetricChange(e) {
    const idx = Number(e.detail.value);
    this.setData({
      metricIndex: idx,
      'formData.metric': this.data.metrics[idx].value
    });
  },

  onConditionChange(e) {
    const idx = Number(e.detail.value);
    this.setData({
      conditionIndex: idx,
      'formData.condition': this.data.conditions[idx].value
    });
  },

  onSeverityChange(e) {
    const idx = Number(e.detail.value);
    this.setData({
      severityIndex: idx,
      'formData.severity': this.data.severities[idx].value
    });
  },

  onNameInput(e) {
    this.setData({ 'formData.name': e.detail.value });
  },

  // 选择预警设备（picker 第0项=所有设备/全局规则，其余=已添加设备）
  onDeviceChange(e) {
    const idx = Number(e.detail.value);
    const device = idx > 0 ? this.data.devices[idx - 1] : null;
    this.setData({
      deviceIndex: idx,
      'formData.deviceName': device ? device.name : '',
      'formData.deviceId': device ? device.id : ''
    });
  },

  onThresholdInput(e) {
    this.setData({ 'formData.threshold': e.detail.value });
  },

  onCancelForm() {
    this.setData({ showForm: false });
  },

  onSubmitForm() {
    const { name, threshold } = this.data.formData;
    if (!name) {
      wx.showToast({ title: '请输入规则名称', icon: 'none' });
      return;
    }
    if (!threshold) {
      wx.showToast({ title: '请输入阈值', icon: 'none' });
      return;
    }

    wx.showLoading({ title: this.data.editing ? '更新中...' : '创建中...' });

    const payload = {
      ...this.data.formData,
      threshold: Number(this.data.formData.threshold)
    };
    if (this.data.editing) {
      // 更新规则
      httpApi.updateAlarmRule(this.data.editId, payload).then(() => {
        wx.hideLoading();
        this.setData({ showForm: false });
        wx.showToast({ title: '已更新', icon: 'success' });
        this.loadRules();
      }).catch(err => {
        wx.hideLoading();
        console.error('更新告警规则失败:', err);
        wx.showToast({ title: '更新失败', icon: 'none' });
      });
    } else {
      // 创建规则
      httpApi.createAlarmRule(payload).then(() => {
        wx.hideLoading();
        this.setData({ showForm: false });
        wx.showToast({ title: '已添加', icon: 'success' });
        this.loadRules();
      }).catch(err => {
        wx.hideLoading();
        console.error('创建告警规则失败:', err);
        wx.showToast({ title: '创建失败', icon: 'none' });
      });
    }
  }
});
