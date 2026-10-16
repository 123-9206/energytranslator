/**
 * 电费单体检（L0 钩子）
 * 链路：拍照上传 -> 后端AI读单+勾稽校验+规则引擎 -> 人工复核 -> 凭单号+手机号查报告
 * 接口为 /api/public/** 免登录；上传走 wx.uploadFile（需在小程序后台配置 uploadFile 合法域名）
 */
const config = require('../../utils/config.js');

Page({
  data: {
    // 上传
    imagePath: '',
    phone: '',
    contactName: '',
    factoryName: '',
    submitting: false,
    submitMsg: '',
    submitError: false,
    ticketId: '',
    // 查询
    queryId: '',
    queryPhone: '',
    querying: false,
    queryMsg: '',
    queryError: false,
    reportText: ''
  },

  onLoad() {
    const savedId = wx.getStorageSync('billcheck_id');
    const savedPhone = wx.getStorageSync('billcheck_phone');
    if (savedId) this.setData({ queryId: savedId });
    if (savedPhone) this.setData({ queryPhone: savedPhone });
  },

  chooseImage() {
    wx.chooseMedia({
      count: 1,
      mediaType: ['image'],
      sourceType: ['album', 'camera'],
      success: (res) => {
        const f = res.tempFiles[0];
        if (f.size > 10 * 1024 * 1024) {
          this.setData({ submitMsg: '照片不能超过10MB', submitError: true });
          return;
        }
        this.setData({ imagePath: f.tempFilePath, submitMsg: '', submitError: false });
      }
    });
  },

  onInput(e) {
    const field = e.currentTarget.dataset.field;
    this.setData({ [field]: e.detail.value });
  },

  submit() {
    const { imagePath, phone, contactName, factoryName } = this.data;
    if (!imagePath) {
      this.setData({ submitMsg: '请先选择电费单照片', submitError: true });
      return;
    }
    if (!/^1\d{10}$/.test(phone)) {
      this.setData({ submitMsg: '请填写正确的11位手机号', submitError: true });
      return;
    }
    this.setData({ submitting: true, submitMsg: '', submitError: false, ticketId: '' });

    wx.uploadFile({
      url: `${config.http.baseUrl}/api/public/bill-check/upload`,
      filePath: imagePath,
      name: 'file',
      formData: { contactPhone: phone, contactName, factoryName },
      success: (res) => {
        this.setData({ submitting: false });
        let data = null;
        try { data = JSON.parse(res.data); } catch (e) { /* 非JSON响应 */ }
        if (data && data.success) {
          const id = data.data.id;
          wx.setStorageSync('billcheck_id', id);
          wx.setStorageSync('billcheck_phone', phone);
          this.setData({
            ticketId: id,
            queryId: id,
            queryPhone: phone,
            submitMsg: '',
            reportText: ''
          });
        } else {
          this.setData({ submitMsg: (data && data.error) || '提交失败，请稍后重试', submitError: true });
        }
      },
      fail: () => {
        this.setData({ submitting: false, submitMsg: '网络异常，请稍后重试', submitError: true });
      }
    });
  },

  query() {
    const { queryId, queryPhone } = this.data;
    if (!queryId || !queryPhone) {
      this.setData({ queryMsg: '请填写单号和手机号', queryError: true });
      return;
    }
    this.setData({ querying: true, queryMsg: '查询中…', queryError: false, reportText: '' });
    wx.request({
      url: `${config.http.baseUrl}/api/public/bill-check/${encodeURIComponent(queryId)}`,
      data: { phone: queryPhone },
      success: (res) => {
        this.setData({ querying: false });
        const data = res.data;
        if (data && data.success) {
          if (data.data.reportText) {
            this.setData({ reportText: data.data.reportText, queryMsg: '' });
          } else {
            this.setData({ queryMsg: data.data.message || '体检进行中，请稍后再查', queryError: false });
          }
        } else {
          this.setData({ queryMsg: (data && data.error) || '查询失败', queryError: true });
        }
      },
      fail: () => {
        this.setData({ querying: false, queryMsg: '网络异常，请稍后重试', queryError: true });
      }
    });
  },

  copyTicketId() {
    if (!this.data.ticketId) return;
    wx.setClipboardData({ data: this.data.ticketId });
  }
});
