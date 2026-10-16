// pages/index/index.js
const httpApi = require('../../utils/api-http.js').httpApi;

Page({
  data: {
    loading: true,
    logged: false,
    dashboard: {
      todayEnergy: 0,
      todayCarbon: 0,
      todayCost: 0,
      monthEnergy: 0,
      monthCarbon: 0,
      monthCost: 0
    },
    recentActivities: [],
    userInfo: null,
    todayDate: '',
    // 首次启动引导浮层
    showGuide: false,
    guideCurrent: 0,
    // 更多服务折叠
    showMore: false
  },

  onLoad() {
    this.checkLogin();
    const now = new Date();
    const pad = n => (n < 10 ? '0' + n : '' + n);
    this.setData({ todayDate: `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}` });

    // 首次启动引导：仅在从未看过时展示，并隐藏 tabBar 以聚焦引导
    // 延迟到 app.onLaunch 的 nextTick showTabBar 之后再隐藏，避免时序冲突
    const app = getApp();
    if (app && app.globalData && app.globalData.needGuide) {
      this.setData({ showGuide: true });
      setTimeout(() => {
        try { wx.hideTabBar({ animation: false }); } catch (e) {}
      }, 80);
    }
  },

  onShow() {
    if (typeof this.getTabBar === 'function' && this.getTabBar()) {
      this.getTabBar().setData({ selected: 0 });
    }
    this.checkLogin();
  },

  checkLogin() {
    const token = wx.getStorageSync('token');
    if (!token) {
      this.setData({ loading: false, logged: false });
      return;
    }
    this.setData({ logged: true });
    this.loadDashboard();
    this.loadRecentActivities();
    this.loadUserInfo();
  },

  loadDashboard() {
    this.setData({ loading: true });
    httpApi.getDashboard('today').then(res => {
      const data = res.data || res;
      this.setData({
        // 后端返回字段: todayUsage/todayCost/todayCarbon
        'dashboard.todayEnergy': data.todayUsage || data.totalEnergy || 0,
        'dashboard.todayCarbon': data.todayCarbon || data.totalCarbon || 0,
        'dashboard.todayCost': data.todayCost || data.totalCost || 0,
        loading: false
      });
    }).catch(() => {
      this.setData({ loading: false });
    });

    httpApi.getDashboard('month').then(res => {
      const data = res.data || res;
      this.setData({
        // 后端返回字段: monthUsage/monthCost/monthCarbon
        'dashboard.monthEnergy': data.monthUsage || data.totalEnergy || 0,
        'dashboard.monthCarbon': data.monthCarbon || data.totalCarbon || 0,
        'dashboard.monthCost': data.monthCost || data.totalCost || 0
      });
    }).catch(() => {});
  },

  loadRecentActivities() {
    httpApi.getActivities({ page: 1, size: 5 }).then(res => {
      const raw = (res && res.list) || (res && res.data) || [];
      // 后端返回 title/startAt/endAt/capacityPrice，映射为模板需要的字段
      const list = raw.map(a => ({
        id: a.id,
        name: a.title || a.name,
        startTime: a.startAt || a.startTime,
        endTime: a.endAt || a.endTime,
        subsidy: a.capacityPrice || a.subsidy,
        status: a.status
      }));
      this.setData({ recentActivities: list });
    }).catch(() => {});
  },

  loadUserInfo() {
    httpApi.getUserInfo().then(res => {
      const info = res.data || res;
      this.setData({ userInfo: info });
    }).catch(() => {});
  },

  goToVPP() {
    // vpp 非 tabBar 页，须用 navigateTo
    wx.navigateTo({ url: '/pages/vpp/vpp' });
  },

  goToEnergy() {
    // energy 是 tabBar 页，须用 switchTab
    wx.switchTab({ url: '/pages/energy/energy' });
  },

  goToCarbon() {
    // carbon 是 tabBar 页，须用 switchTab
    wx.switchTab({ url: '/pages/carbon/carbon' });
  },

  goToActivityDetail(e) {
    const id = e.currentTarget.dataset.id;
    wx.navigateTo({ url: `/pages/vpp/event/event?id=${id}` });
  },

  goToProfile() {
    wx.switchTab({ url: '/pages/profile/profile' });
  },

  goToGreen() {
    // green 非 tabBar 页，用 navigateTo
    wx.navigateTo({ url: '/pages/green/green' });
  },

  goToBillCheck() {
    // billcheck 非 tabBar 页，用 navigateTo
    wx.navigateTo({ url: '/pages/billcheck/billcheck' });
  },

  goToPolicy() {
    // policy 非 tabBar 页，用 navigateTo
    wx.navigateTo({ url: '/pages/policy/policy' });
  },

  toggleMore() {
    this.setData({ showMore: !this.data.showMore });
  },

  // ============ 首次启动引导浮层 ============
  // 阻止引导层滚动穿透到下方页面
  preventMove() {},

  nextGuide() {
    if (this.data.guideCurrent < 3) {
      this.setData({ guideCurrent: this.data.guideCurrent + 1 });
    } else {
      this.closeGuide();
    }
  },

  skipGuide() {
    this.closeGuide();
  },

  closeGuide() {
    // 标记已看过，后续启动不再弹出
    try { wx.setStorageSync('hasSeenGuide', true); } catch (e) {}
    const app = getApp();
    if (app && app.globalData) app.globalData.needGuide = false;
    this.setData({ showGuide: false });
    try { wx.showTabBar({ animation: true }); } catch (e) {}
  }
});
