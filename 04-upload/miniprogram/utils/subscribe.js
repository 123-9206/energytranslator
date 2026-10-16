/**
 * 微信订阅消息工具
 * 
 * 使用 wx.requestSubscribeMessage 引导用户授权订阅
 * 典型使用场景：告警通知、碳配额预警、VPP活动提醒等
 */

const config = require('./config.js');

// 订阅消息模板ID（统一从 config.js 读取，在微信公众平台配置后填入）
const TEMPLATE_IDS = {
  alarm: config.subscribeTemplates?.alarm || '',
  quotaWarning: config.subscribeTemplates?.quotaWarning || '',
  vppActivity: config.subscribeTemplates?.vppActivity || ''
};

/**
 * 请求订阅消息授权
 * @param {string[]} tmplIds - 模板ID数组
 * @returns {Promise<Object>} 授权结果
 */
function requestSubscribe(tmplIds = []) {
  const ids = tmplIds.filter(Boolean);
  if (ids.length === 0) {
    // 默认请求所有已配置的模板
    ids.push(...Object.values(TEMPLATE_IDS).filter(Boolean));
  }
  if (ids.length === 0) {
    wx.showToast({ title: '暂无可订阅消息', icon: 'none' });
    return Promise.resolve({ accepted: [] });
  }

  return new Promise((resolve) => {
    wx.showModal({
      title: '消息订阅',
      content: '开启消息提醒后，我们将及时通知您告警、配额预警等重要信息。是否允许订阅？',
      confirmText: '去设置',
      cancelText: '暂不',
      success: (modalRes) => {
        if (!modalRes.confirm) {
          resolve({ accepted: [], cancelled: true });
          return;
        }

        wx.requestSubscribeMessage({
          tmplIds: ids,
          success: (res) => {
            const accepted = ids.filter((id) => res[id] === 'accept');
            if (accepted.length > 0) {
              wx.showToast({
                title: `已订阅${accepted.length}项`,
                icon: 'success'
              });
            }
            resolve({ accepted, raw: res });
          },
          fail: (err) => {
            console.error('订阅消息授权失败:', err);
            wx.showToast({ title: '订阅失败', icon: 'none' });
            resolve({ accepted: [], error: err });
          }
        });
      }
    });
  });
}

/**
 * 获取配置的模板ID列表
 */
function getTemplateIds() {
  return Object.entries(TEMPLATE_IDS).filter(([, v]) => !!v);
}

module.exports = {
  TEMPLATE_IDS,
  requestSubscribe,
  getTemplateIds
};
