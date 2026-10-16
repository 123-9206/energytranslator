/**
 * =====================================================
 * 能耗翻译官 - 小程序入口
 * =====================================================
 * v1.4.0 版本更新增强：
 *  - wx.getUpdateManager() 微信原生更新检测（解决缓存旧包）
 *  - /api/public/version 服务端版本对比（更新说明 + 强制更新）
 */

const config = require('./utils/config.js');

App({
  /**
   * 全局错误拦截 — 过滤 DevTools 框架级内部超时
   */
  onError: function(err) {
    var msg = String(err || '');
    var stack = '';
    if (err && typeof err === 'object' && err.stack) {
      stack = String(err.stack);
    }
    var full = msg + '\n' + stack;
    // DevTools 框架级 getDeviceInfo / WAServiceMainContext 超时，非业务错误
    if (full.indexOf('getDeviceInfo') >= 0 ||
        (full.indexOf('WAServiceMainContext') >= 0 && msg.indexOf('timeout') >= 0)) {
      return; // 静默丢弃
    }
    console.error('[App Error]', err);
  },

  globalData: {
    userInfo: null,
    token: null,
    enterpriseId: null,
    // API配置
    apiMode: config.mode,  // 'cloud' | 'http'
    apiBaseUrl: config.http.baseUrl,
    cloudEnv: config.cloud.env,
    // 版本信息
    appVersion: config.version.code,
    serverVersion: null,   // 服务端最新版本信息
    // 首次启动引导：仅首次进入展示，关闭后写 storage 不再弹
    needGuide: false
  },

  onLaunch: function() {
    // 初始化云开发（如果使用云开发模式）
    if (config.mode === 'cloud') {
      wx.cloud.init({
        env: config.cloud.env,
        traceUser: true
      });
    }

    // 检查登录状态
    this.checkLoginStatus();

    // 首次启动引导标记：仅首次进入展示（关闭后写 storage 不再弹）
    try {
      this.globalData.needGuide = !wx.getStorageSync('hasSeenGuide');
    } catch (e) {
      this.globalData.needGuide = false;
    }

    // 订阅消息模板ID配置检查（仅开发环境输出提示）
    this.checkSubscribeConfig();

    // 注册全局错误监听（兜底：框架级 getDeviceInfo 超时）
    wx.onError(function(err) {
      var msg = String(err || '');
      var stack = '';
      if (err && typeof err === 'object' && err.stack) {
        stack = String(err.stack);
      }
      var full = msg + '\n' + stack;
      if (full.indexOf('getDeviceInfo') >= 0 ||
          (full.indexOf('WAServiceMainContext') >= 0 && msg.indexOf('timeout') >= 0)) {
        return; // 静默丢弃
      }
      console.error('[wx.onError]', err);
    });

    // tabBar 显示 — 延迟到首屏渲染后
    wx.nextTick(() => {
      try { wx.showTabBar({ animation: false }); } catch(e) {}
    });
  },

  onShow: function() {
    // ==========================================
    // 更新检测（onShow 中执行，此时页面已渲染）
    // 1) wx.getUpdateManager 微信原生检测 — 仅正式版(release)启用，
    //    DevTools/体验版会触发 getDeviceInfo 网络超时，静默跳过
    // 2) 服务端版本对比 — 仅正式版自动检查（DevTools/体验版工具网络不稳会超时），
    //    手动"检查更新"（force=true）任何环境都可触发
    // ==========================================
    try {
      var isRelease = typeof __wxConfig !== 'undefined'
        && __wxConfig.envVersion === 'release';
      if (isRelease && !this._wxUpdateChecked) {
        this._wxUpdateChecked = true;
        this.checkForUpdate();
      }
    } catch(e) { /* 静默 */ }

    // 服务端版本对比 — 仅正式版(release)自动检查；
    // DevTools/体验版工具网络不稳，wx.request 挂起直到超时会拖慢启动并刷错误日志，
    // 需要测试时可在"我的 → 检查更新"手动触发（force=true）
    if (!this._serverVersionChecked && this._isRelease()) {
      this._serverVersionChecked = true;
      this.checkServerVersion();
    }
  },

  /**
   * 判断当前是否为正式版（release）环境
   * DevTools(develop) / 体验版(trial) 返回 false
   */
  _isRelease: function() {
    try {
      return typeof __wxConfig !== 'undefined' && __wxConfig.envVersion === 'release';
    } catch(e) {
      return false;
    }
  },

  /**
   * 检测小程序新版本并提示用户更新（微信原生机制）
   * 解决：发布新版本后，老用户设备缓存旧包的问题
   */
  checkForUpdate: function() {
    if (!wx.canIUse('getUpdateManager')) {
      console.log('当前微信版本不支持自动更新');
      return;
    }
    const updateManager = wx.getUpdateManager();

    updateManager.onCheckForUpdate(function(res) {
      console.log('[更新检测] 微信是否有新版本:', res.hasUpdate);
    });

    // 新版本下载完成时提示用户重启
    updateManager.onUpdateReady(function() {
      wx.showModal({
        title: '发现新版本',
        content: '小程序已发布新版本，重启后即可使用最新功能',
        showCancel: false,
        confirmText: '立即重启',
        success: function(res) {
          if (res.confirm) {
            updateManager.applyUpdate();
          }
        }
      });
    });

    // 新版本下载失败
    updateManager.onUpdateFailed(function() {
      console.log('[更新检测] 新版本下载失败');
      wx.showToast({
        title: '新版本下载失败，请删除小程序后重新进入',
        icon: 'none',
        duration: 3000
      });
    });
  },

  /**
   * 服务端版本对比（运营级更新管理）
   * 调 GET /api/public/version，与本地版本号对比：
   *  - 服务端版本 > 本地版本 → 弹更新说明（可含强制更新）
   *  - 相等 → 正常使用
   * @param {boolean} force 手动触发（profile 页"检查更新"），任何环境都执行；
   *                        自动触发仅 release 环境（见 onShow）
   */
  checkServerVersion: function(force) {
    const local = (config.version && config.version.code) || '';
    if (!local) return;

    // DevTools/体验版：非手动触发时跳过（工具网络不稳，超时拖慢启动 + 刷错误日志）
    if (!force && !this._isRelease()) return;

    wx.request({
      url: config.http.baseUrl + '/api/public/version',
      method: 'GET',
      timeout: 8000,
      success: (res) => {
        try {
          const body = res.data || {};
          const data = body.data || body;
          if (!data || !data.version) return;
          this.globalData.serverVersion = data;

          const server = String(data.version || '').trim();
          if (!server || server === local) return; // 已是最新

          const localNum = this._toNum(local);
          const serverNum = this._toNum(server);
          const isNewer = serverNum > localNum;

          if (!isNewer) return; // 本地比服务端新（开发中），不提示

          const force = !!data.forceUpdate;
          const note = data.note || '请更新到最新版本体验新功能';
          const name = data.name || ('v' + data.version);

          wx.showModal({
            title: force ? '需要更新' : '发现新版本 ' + name,
            content: note,
            showCancel: !force,
            confirmText: '立即更新',
            cancelText: '稍后再说',
            success: (r) => {
              if (r.confirm) {
                // 尝试微信原生更新；若微信侧无新包，引导用户重启
                if (wx.canIUse('getUpdateManager')) {
                  const um = wx.getUpdateManager();
                  um.onUpdateReady(function() {
                    um.applyUpdate();
                  });
                  um.onUpdateFailed(function() {
                    wx.showModal({
                      title: '更新提示',
                      content: '新版本已下载完成，请关闭小程序后重新打开即可生效',
                      showCancel: false,
                      confirmText: '知道了'
                    });
                  });
                } else {
                  wx.showModal({
                    title: '更新提示',
                    content: '请关闭小程序后重新打开，即可使用最新版本',
                    showCancel: false,
                    confirmText: '知道了'
                  });
                }
              } else if (force) {
                // 强制更新被取消 → 无法使用，再次弹出
                wx.showModal({
                  title: '需要更新',
                  content: '当前版本已停止服务，请更新后继续使用',
                  showCancel: false,
                  confirmText: '去更新',
                  success: () => this.checkServerVersion()
                });
              }
            }
          });
        } catch(e) {
          console.log('[版本对比] 解析失败', e);
        }
      },
      fail: () => {
        // 网络失败静默，不打扰用户
      }
    });
  },

  /** 版本号 "1.4.0" → 数字 10400，用于大小比较 */
  _toNum: function(v) {
    const parts = String(v).split('.');
    let n = 0;
    for (let i = 0; i < 3; i++) {
      n = n * 100 + (parseInt(parts[i], 10) || 0);
    }
    return n;
  },

  /**
   * 检查订阅消息模板ID配置
   */
  checkSubscribeConfig: function() {
    var tpl = config.subscribeTemplates;
    if (!tpl) return;
    var hasAlarm = !!tpl.alarm;
    var hasQuota = !!tpl.quotaWarning;
    var hasVpp = !!tpl.vppActivity;
    if (!hasAlarm) console.warn('[订阅消息] 告警通知模板ID未配置');
    if (!hasQuota) console.warn('[订阅消息] 碳配额预警模板ID未配置');
    if (!hasVpp) console.warn('[订阅消息] VPP活动提醒模板ID未配置');
  },

  /**
   * 检查登录状态
   */
  checkLoginStatus: function() {
    const token = wx.getStorageSync('token');
    if (token) {
      this.globalData.token = token;
      const userInfo = wx.getStorageSync('userInfo');
      if (userInfo) {
        this.globalData.userInfo = userInfo;
        this.globalData.enterpriseId = userInfo.enterpriseId;
      }
    }
  },

  /**
   * 设置用户信息
   */
  setUserInfo: function(userInfo, token) {
    this.globalData.userInfo = userInfo;
    this.globalData.enterpriseId = userInfo.enterpriseId;
    if (token) {
      this.globalData.token = token;
      wx.setStorageSync('token', token);
    }
    wx.setStorageSync('userInfo', userInfo);
  },

  /**
   * 清除用户信息（退出登录）
   */
  clearUserInfo: function() {
    this.globalData.userInfo = null;
    this.globalData.token = null;
    this.globalData.enterpriseId = null;
    wx.removeStorageSync('token');
    wx.removeStorageSync('refreshToken');
    wx.removeStorageSync('userInfo');
  },

  /**
   * 检查是否已登录
   */
  isLoggedIn: function() {
    return !!this.globalData.token;
  }
});
