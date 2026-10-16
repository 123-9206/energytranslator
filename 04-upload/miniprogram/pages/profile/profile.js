// pages/profile/profile.js
const httpApi = require('../../utils/api-http.js').httpApi;
const config = require('../../utils/config.js');

Page({
  data: {
    logged: false,
    userInfo: null,
    appVersion: (config.version && config.version.code) || '1.0.0',
    menuItems: [
      { icon: '📱', label: '我的设备', url: '/pages/control/control', needLogin: true },
      { icon: '⚡', label: '我的报名', url: '/pages/vpp/earnings/earnings', needLogin: true },
      { icon: '💰', label: '老板钱包账', url: '/pages/wallet/wallet', needLogin: true },
      { icon: '🌱', label: '绿电中心', url: '/pages/green/green', needLogin: true },
      { icon: '🤝', label: '合作售电分润', url: '/pages/partner/partner', needLogin: true },
      { icon: '🎯', label: '政策福利', url: '/pages/policy/policy', needLogin: true },
      { icon: '📋', label: '告警列表', url: '/pages/alarm/alarm', needLogin: true },
      { icon: '🔔', label: '告警规则', url: '/pages/alarm-rules/alarm-rules', needLogin: true },
      { icon: '🔧', label: '电路安全', url: '/pages/circuit-safety/circuit-safety', needLogin: true },
      { icon: '📊', label: '能耗分析', url: '/pages/analysis/analysis', needLogin: true },
      { icon: '🌳', label: '碳报告', url: '/pages/carbon/carbon', needLogin: true },
      { icon: '🏷️', label: '碳配额', url: '/pages/quota/quota', needLogin: true },
      { icon: '🌿', label: '碳资产', url: '/pages/carbon-asset/carbon-asset', needLogin: true },
      { icon: '🔗', label: '分布式协同调度', url: '/pages/coordination/coordination', needLogin: true },
      { icon: '📄', label: '历史记录', url: '/pages/energy/history/history', needLogin: true },
      { icon: '🔄', label: '检查更新', action: 'checkUpdate' },
      { icon: 'ℹ️', label: '关于', action: 'about' },
      { icon: '🔒', label: '隐私政策', url: '/pages/privacy/privacy', needLogin: false },
      { icon: '📘', label: '设备接入指南', url: '/pages/guide/guide', needLogin: false },
    ]
  },

  onLoad() {
    this.checkLoginStatus();
  },

  onShow() {
    if (typeof this.getTabBar === 'function' && this.getTabBar()) {
      this.getTabBar().setData({ selected: 4 });
    }
    this.checkLoginStatus();
  },

  checkLoginStatus() {
    const token = wx.getStorageSync('token');
    if (token) {
      this.setData({ logged: true });
      this.loadUserInfo();
    } else {
      this.setData({ logged: false, userInfo: null });
    }
  },

  loadUserInfo() {
    httpApi.getUserInfo().then(res => {
      this.setData({ userInfo: res.data || res });
    }).catch(() => {});
  },

  // tabBar 页面列表：这些页面只能用 wx.switchTab 跳转
  TAB_PAGES: ['/pages/index/index', '/pages/energy/energy', '/pages/analysis/analysis', '/pages/carbon/carbon', '/pages/profile/profile'],

  onMenuTap(e) {
    const index = e.currentTarget.dataset.index;
    const item = this.data.menuItems[index];
    console.log('[profile] tap menu:', index, item.label, 'url=', item.url, 'logged=', this.data.logged);

    if (item.needLogin && !this.data.logged) {
      wx.showModal({ title: '提示', content: '请先登录后再使用此功能', showCancel: false });
      return;
    }

    // 动作型菜单（检查更新 / 关于）
    if (item.action === 'checkUpdate') { this.onCheckUpdate(); return; }
    if (item.action === 'about') { this.onAbout(); return; }

    if (!item.url) return;

    if (this.TAB_PAGES.includes(item.url)) {
      wx.switchTab({ url: item.url });
    } else {
      wx.navigateTo({ url: item.url });
    }
  },

  /** 手动检查更新：重置标记后调用 app 级检查（强制弹窗） */
  onCheckUpdate() {
    const app = getApp();
    wx.showLoading({ title: '检查更新中...', mask: true });
    // 延迟执行，让 loading 展示
    setTimeout(() => {
      wx.hideLoading();
      if (app && app.checkServerVersion) {
        app._serverVersionChecked = false; // 允许再次弹窗
        app.checkServerVersion(true);      // force=true：任何环境都执行（含 DevTools）
        // 微信原生更新检测也重新触发
        if (wx.canIUse('getUpdateManager')) {
          const um = wx.getUpdateManager();
          um.onUpdateReady(function() {
            wx.showModal({
              title: '发现新版本',
              content: '新版本已下载完成，重启后即可使用',
              showCancel: false,
              confirmText: '立即重启',
              success: (r) => { if (r.confirm) um.applyUpdate(); }
            });
          });
        }
      } else {
        wx.showToast({ title: '版本检查不可用', icon: 'none' });
      }
    }, 400);
  },

  /** 关于：展示版本信息与备案信息 */
  onAbout() {
    const version = this.data.appVersion;
    const icp = (config.icp && config.icp.beianNumber) ? config.icp.beianNumber : '';
    wx.showModal({
      title: '能耗翻译官',
      content: '当前版本 v' + version + '\n\n企业能耗监测 · AI诊断 · 虚拟电厂\n\n' + (icp ? '备案号 ' + icp : ''),
      showCancel: false,
      confirmText: '知道了'
    });
  },

  onLogin() {
    wx.navigateTo({ url: '/pages/profile/login/login' });
  },

  onLogout() {
    wx.showModal({
      title: '提示',
      content: '确定要退出登录吗？',
      success: (res) => {
        if (res.confirm) {
          wx.removeStorageSync('token');
          wx.removeStorageSync('userInfo');
          this.setData({ logged: false, userInfo: null });
          wx.showToast({ title: '已退出登录', icon: 'success' });
        }
      }
    });
  }
});
