// pages/profile/login/login.js
const httpApi = require('../../../utils/api-http.js').httpApi;
const app = getApp();

Page({
  data: {
    isLogin: true, // true=登录, false=注册
    username: '',
    password: '',
    confirmPassword: '',
    enterpriseName: '',
    phone: '',
    loading: false
  },

  onLoad() {},

  // 切换登录/注册
  switchMode() {
    this.setData({ isLogin: !this.data.isLogin });
  },

  // 输入事件
  onUsernameInput(e) {
    this.setData({ username: e.detail.value });
  },

  onPasswordInput(e) {
    this.setData({ password: e.detail.value });
  },

  onConfirmPasswordInput(e) {
    this.setData({ confirmPassword: e.detail.value });
  },

  onEnterpriseNameInput(e) {
    this.setData({ enterpriseName: e.detail.value });
  },

  onPhoneInput(e) {
    this.setData({ phone: e.detail.value });
  },

  /**
   * 隐私授权前置检查（合规要求）
   * - 基础库不支持隐私接口时直接放行
   * - 需要授权且用户同意 → 放行；拒绝 → 拦截并提示
   */
  ensurePrivacyAuthorized() {
    return new Promise((resolve) => {
      if (typeof wx.getPrivacySetting !== 'function') { resolve(true); return; }
      wx.getPrivacySetting({
        success: (res) => {
          if (!res.needAuthorization) { resolve(true); return; }
          if (typeof wx.requirePrivacyAuthorize === 'function') {
            wx.requirePrivacyAuthorize({
              success: () => { wx.setStorageSync('privacyAuthorized', true); resolve(true); },
              fail: () => {
                wx.showToast({ title: '请先同意隐私协议', icon: 'none' });
                resolve(false);
              }
            });
          } else {
            wx.showModal({
              title: '隐私协议',
              content: '使用前请阅读并同意《隐私保护指引》',
              confirmText: '去查看',
              success: (r) => {
                if (r.confirm) wx.navigateTo({ url: '/pages/privacy/privacy' });
                resolve(false);
              }
            });
          }
        },
        fail: () => resolve(true)
      });
    });
  },

  // 登录
  async onLogin() {
    const { username, password } = this.data;

    if (!username.trim()) {
      wx.showToast({ title: '请输入用户名', icon: 'none' });
      return;
    }
    if (!password) {
      wx.showToast({ title: '请输入密码', icon: 'none' });
      return;
    }

    // 隐私授权：收集个人信息前确保用户已同意隐私协议
    const ok = await this.ensurePrivacyAuthorized();
    if (!ok) return;

    this.setData({ loading: true });
    try {
      const res = await httpApi.login(username.trim(), password);
      if (res.token) {
        app.setUserInfo(res.user || res, res.token);
        wx.showToast({ title: '登录成功', icon: 'success' });
        setTimeout(() => {
          wx.navigateBack({
            delta: 1,
            fail: () => {
              wx.switchTab({ url: '/pages/index/index' });
            }
          });
        }, 800);
      }
    } catch (err) {
      wx.showToast({ title: err.message || '登录失败', icon: 'none' });
    } finally {
      this.setData({ loading: false });
    }
  },

  // 演示账号一键登录（免密体验端点）
  async onDemoLogin() {
    const ok = await this.ensurePrivacyAuthorized();
    if (!ok) return;
    this.setData({ loading: true });
    try {
      const res = await httpApi.demoLogin();
      if (res.token) {
        app.setUserInfo(res.user || res, res.token);
        wx.showToast({ title: '已进入演示企业', icon: 'success' });
        setTimeout(() => {
          wx.switchTab({ url: '/pages/index/index' });
        }, 800);
      }
    } catch (err) {
      wx.showToast({ title: err.message || '演示登录失败', icon: 'none' });
    } finally {
      this.setData({ loading: false });
    }
  },

  // 注册
  async onRegister() {
    const { username, password, confirmPassword, enterpriseName, phone } = this.data;

    if (!username.trim()) {
      wx.showToast({ title: '请输入用户名', icon: 'none' });
      return;
    }
    if (username.trim().length < 3) {
      wx.showToast({ title: '用户名至少3个字符', icon: 'none' });
      return;
    }
    if (!password || password.length < 6) {
      wx.showToast({ title: '密码至少6位', icon: 'none' });
      return;
    }
    if (password !== confirmPassword) {
      wx.showToast({ title: '两次密码不一致', icon: 'none' });
      return;
    }
    if (!enterpriseName.trim()) {
      wx.showToast({ title: '请输入企业名称', icon: 'none' });
      return;
    }

    // 隐私授权：注册即收集企业/个人信息，需先同意隐私协议
    const ok = await this.ensurePrivacyAuthorized();
    if (!ok) return;

    this.setData({ loading: true });
    try {
      await httpApi.register({
        username: username.trim(),
        password: password,
        enterpriseName: enterpriseName.trim(),
        phone: phone.trim()
      });
      wx.showToast({ title: '注册成功，请登录', icon: 'success' });
      this.setData({ isLogin: true, password: '', confirmPassword: '' });
    } catch (err) {
      wx.showToast({ title: err.message || '注册失败', icon: 'none' });
    } finally {
      this.setData({ loading: false });
    }
  }
});
