Page({
  data: {
    tab: 'privacy' // 'privacy' | 'agreement'
  },

  onLoad(options) {
    if (options.tab) {
      this.setData({ tab: options.tab });
    }
  },

  onSwitchTab(e) {
    const tab = e.currentTarget.dataset.tab;
    this.setData({ tab });
  }
});
