// pages/policy/policy.js
const httpApi = require('../../utils/api-http.js').httpApi;

Page({
  data: {
    loading: true,
    summary: { totalPolicies: 0, matchedCount: 0, totalBenefit: 0 },
    policies: [],
    total: 0,
    matchedCount: 0,
    annualUsage: 0,
    industry: '',
    typeFilter: 'all',
    typeTabs: [
      { key: 'all', label: '全部' },
      { key: 'green_power', label: '绿电' },
      { key: 'demand_response', label: '需求响应' },
      { key: 'energy_saving', label: '节能改造' },
      { key: 'green_factory', label: '绿色工厂' },
      { key: 'carbon', label: '碳交易' },
      { key: 'subsidy', label: '专项补贴' }
    ],
    typeLabels: {
      green_power: '绿电',
      carbon: '碳交易',
      demand_response: '需求响应',
      energy_saving: '节能改造',
      green_factory: '绿色工厂',
      subsidy: '专项补贴'
    }
  },

  onLoad() {
    this.checkLogin();
  },

  onShow() {
    // 未登录/无 token 时不发请求，直接回"我的"引导登录，避免并发 401 竞态
    const token = wx.getStorageSync('token');
    if (!token) {
      this.setData({ loading: false });
      wx.switchTab({ url: '/pages/profile/profile' });
      return;
    }
    this.loadData();
  },

  checkLogin() {
    const token = wx.getStorageSync('token');
    if (!token) {
      // profile 是 tabBar 页，必须用 switchTab
      wx.switchTab({ url: '/pages/profile/profile' });
    }
  },

  loadData() {
    this.loadSummary();
    this.loadPolicies();
  },

  loadSummary() {
    httpApi.getPolicySummary().then(res => {
      const s = res || this.data.summary;
      // 预处理 totalBenefitText —— WXML 不支持函数调用
      this.setData({ summary: { ...s, totalBenefitText: this.formatAmount(s.totalBenefit) } });
    }).catch(() => {});
  },

  loadPolicies() {
    this.setData({ loading: true });
    const params = {};
    if (this.data.typeFilter !== 'all') {
      params.policyType = this.data.typeFilter;
    }
    httpApi.getAvailablePolicies(params).then(res => {
      const rawList = (res && res.list) || [];
      // 把 policy.id 提升到 item 顶层，供 wx:key 使用；并预处理 typeLabel/benefitAmountText（WXML 不支持函数调用）
      const list = rawList.map((item, index) => {
        const policy = item.policy || {};
        return {
          ...item,
          id: policy.id || `idx_${index}`,
          typeLabel: this.data.typeLabels[policy.policyType] || policy.policyType || '政策',
          benefitAmountText: this.formatAmount(policy.benefitAmount)
        };
      });
      this.setData({
        policies: list,
        total: (res && res.total) || 0,
        matchedCount: (res && res.matchedCount) || 0,
        annualUsage: (res && res.annualUsage) || 0,
        industry: (res && res.industry) || '',
        loading: false
      });
    }).catch(() => {
      this.setData({ loading: false });
      wx.showToast({ title: '加载失败', icon: 'none' });
    });
  },

  onTypeChange(e) {
    const key = e.currentTarget.dataset.key;
    if (key === this.data.typeFilter) return;
    this.setData({ typeFilter: key });
    this.loadPolicies();
  },

  onApply(e) {
    const index = e.currentTarget.dataset.index;
    const item = this.data.policies[index];
    const policy = item.policy;
    if (policy.applyUrl) {
      wx.setClipboardData({
        data: policy.applyUrl,
        success: () => wx.showToast({ title: '申报链接已复制', icon: 'success' })
      });
    } else {
      wx.showModal({
        title: '申报提示',
        content: '该政策由平台协助申报，请联系客服获取申报服务。',
        showCancel: false
      });
    }
  },

  typeLabel(type) {
    return this.data.typeLabels[type] || type || '政策';
  },

  formatAmount(amount) {
    if (!amount) return '0';
    if (amount >= 10000) return `${(amount / 10000).toFixed(1)}万`;
    return String(amount);
  }
});
