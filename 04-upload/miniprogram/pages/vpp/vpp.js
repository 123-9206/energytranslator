// pages/vpp/vpp.js
const httpApi = require('../../utils/api-http.js').httpApi;

Page({
  data: {
    loading: true,
    resources: [],
    activities: [],
    summary: {
      totalCapacity: 0,
      onlineCount: 0,
      totalEarnings: 0
    },
    activeTab: 'resources',
    tabs: [
      { key: 'resources', label: '我的资源' },
      { key: 'activities', label: 'VPP活动' }
    ],
    statusLabels: {
      online: '在线',
      offline: '离线',
      maintenance: '维护中'
    },
    statusColors: {
      online: 'green',
      offline: 'gray',
      maintenance: 'orange'
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
    this.loadData();
  },

  checkLogin() {
    const token = wx.getStorageSync('token');
    if (!token) {
      wx.switchTab({ url: '/pages/profile/profile' });
    }
  },

  loadData() {
    this.setData({ loading: true });
    Promise.all([
      this.loadResources(),
      this.loadActivities()
    ]).finally(() => {
      this.setData({ loading: false });
    });
  },

  loadResources() {
    return httpApi.getResources().then(res => {
      const data = res.data || res;
      const list = Array.isArray(data) ? data : (data.list || data.records || []);

      let totalCapacity = 0, onlineCount = 0, totalEarnings = 0;
      list.forEach(item => {
        totalCapacity += Number(item.capacity) || 0;
        if (item.status === 'online') onlineCount++;
        totalEarnings += Number(item.earnings) || 0;
      });

      this.setData({
        resources: list,
        'summary.totalCapacity': totalCapacity,
        'summary.onlineCount': onlineCount,
        'summary.totalEarnings': totalEarnings
      });
    }).catch(() => {});
  },

  loadActivities() {
    return httpApi.getActivities({ page: 1, size: 20 }).then(res => {
      const data = res.data || res;
      this.setData({
        activities: data.list || data.records || (Array.isArray(data) ? data : [])
      });
    }).catch(() => {});
  },

  onTabChange(e) {
    const key = e.currentTarget.dataset.key;
    this.setData({ activeTab: key });
  },

  onActivityTap(e) {
    const id = e.currentTarget.dataset.id;
    wx.navigateTo({ url: `/pages/vpp/event/event?id=${id}` });
  },

  goToAddResource() {
    wx.showToast({ title: '暂不支持手动添加', icon: 'none' });
  },

  goToEarnings() {
    wx.navigateTo({ url: '/pages/vpp/earnings/earnings' });
  }
});
