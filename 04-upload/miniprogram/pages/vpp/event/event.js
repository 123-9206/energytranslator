// pages/vpp/event/event.js
const httpApi = require('../../../utils/api-http.js').httpApi;

Page({
  data: {
    loading: true,
    id: '',
    activity: null,
    enrolled: false,
    enrolling: false,
    myResources: [],
    resourceNames: [],
    resourceIndex: 0,
    selectedResource: null,
    committedCapacity: 0,
    earningsEstimate: [0, 0, 0],
    statusLabels: {
      draft: '草稿',
      registration: '报名中',
      confirmed: '已确认',
      executing: '执行中',
      completed: '已结束',
      cancelled: '已取消'
    }
  },

  onLoad(options) {
    this.checkLogin();
    if (options.id) {
      this.setData({ id: options.id });
      this.loadActivityDetail(options.id);
    }
  },

  checkLogin() {
    const token = wx.getStorageSync('token');
    if (!token) {
      wx.switchTab({ url: '/pages/profile/profile' });
    }
  },

  loadActivityDetail(id) {
    this.setData({ loading: true });
    httpApi.getActivityDetail(id).then(res => {
      const data = res.data || res;
      const enrolled = data.isEnrolled === true || data.enrolled === true;
      const cp = Number(data.capacityPrice) || 0;
      // 预处理 startAtText/endAtText —— WXML 数据绑定不支持函数调用，需在 JS 侧格式化
      this.setData({
        activity: {
          ...data,
          startAtText: this.formatTime(data.startAt),
          endAtText: this.formatTime(data.endAt)
        },
        enrolled,
        loading: false,
        earningsEstimate: [Math.round(cp * 100), Math.round(cp * 500), Math.round(cp * 1000)]
      });
      if (!enrolled && data.canEnroll !== false) {
        this.loadMyResources();
      }
    }).catch(() => {
      this.setData({ loading: false });
      wx.showToast({ title: '加载失败', icon: 'none' });
    });
  },

  // 加载我的可调负荷资源（仅 online）
  loadMyResources() {
    httpApi.getResources({ page: 1, pageSize: 50 }).then(res => {
      const list = (res && res.list) || [];
      const online = list.filter(r => r.status === 'online' && r.adjustableRange);
      const resourceNames = online.map(r => `${r.name}（可调 ${r.adjustableRange.min}~${r.adjustableRange.max}kW）`);
      let resourceIndex = 0;
      let selectedResource = online.length ? online[0] : null;
      let committedCapacity = selectedResource ? selectedResource.adjustableRange.max : 0;

      // 若最低承诺容量高于资源上限，提示资源不足
      const minCapacity = this.data.activity && this.data.activity.minimumCapacity || 0;
      if (selectedResource && committedCapacity < minCapacity) {
        committedCapacity = committedCapacity;
      }

      this.setData({
        myResources: online,
        resourceNames,
        resourceIndex,
        selectedResource,
        committedCapacity: Math.round(committedCapacity * 100) / 100
      });
    }).catch(() => {});
  },

  onResourceChange(e) {
    const index = Number(e.detail.value);
    const resource = this.data.myResources[index];
    if (!resource) return;
    this.setData({
      resourceIndex: index,
      selectedResource: resource,
      committedCapacity: Math.round(resource.adjustableRange.max * 100) / 100
    });
  },

  onEnroll() {
    if (this.data.enrolling) return;
    this.setData({ enrolling: true });

    if (this.data.enrolled) {
      // 取消报名
      httpApi.cancelEnroll(this.data.id).then(() => {
        this.setData({ enrolled: false, enrolling: false });
        wx.showToast({ title: '已取消报名', icon: 'success' });
      }).catch(() => {
        this.setData({ enrolling: false });
        wx.showToast({ title: '取消失败', icon: 'none' });
      });
      return;
    }

    // 报名
    if (!this.data.selectedResource) {
      this.setData({ enrolling: false });
      wx.showToast({ title: '暂无在线可调资源，请先在VPP页注册资源', icon: 'none' });
      return;
    }
    const minCapacity = this.data.activity.minimumCapacity || 0;
    if (this.data.committedCapacity < minCapacity) {
      this.setData({ enrolling: false });
      wx.showToast({ title: `最低承诺容量需达到${minCapacity}kW`, icon: 'none' });
      return;
    }

    const userInfo = wx.getStorageSync('userInfo') || {};
    httpApi.enrollActivity(
      this.data.id,
      [this.data.selectedResource.id],
      this.data.committedCapacity,
      userInfo.nickname || userInfo.username || '',
      userInfo.phone || ''
    ).then(() => {
      this.setData({ enrolled: true, enrolling: false });
      wx.showToast({ title: '报名成功', icon: 'success' });
    }).catch((err) => {
      this.setData({ enrolling: false });
      wx.showToast({ title: (err && err.message) || '报名失败', icon: 'none' });
    });
  },

  formatTime(iso) {
    if (!iso) return '待定';
    const d = new Date(iso);
    const pad = (n) => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
  },

  onCallService() {
    wx.makePhoneCall({
      phoneNumber: '400-000-0000',
      fail: () => {}
    });
  }
});
