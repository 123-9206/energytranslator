// pages/energy/history/history.js
const httpApi = require('../../../utils/api-http.js').httpApi;

Page({
  data: {
    loading: false,
    selectedDate: '',
    currentMonth: '',
    year: 0,
    month: 0,
    days: [],
    dailyData: {},
    selectedDayData: null,
    weekDays: ['日', '一', '二', '三', '四', '五', '六']
  },

  onLoad() {
    this.checkLogin();
    const now = new Date();
    const year = now.getFullYear();
    const month = now.getMonth();
    this.setData({ year, month, selectedDate: this.formatDate(now) });
    this.generateCalendar(year, month);
    this.loadMonthData(year, month);
  },

  checkLogin() {
    const token = wx.getStorageSync('token');
    if (!token) {
      wx.switchTab({ url: '/pages/profile/profile' });
    }
  },

  formatDate(date) {
    const y = date.getFullYear();
    const m = String(date.getMonth() + 1).padStart(2, '0');
    const d = String(date.getDate()).padStart(2, '0');
    return `${y}-${m}-${d}`;
  },

  generateCalendar(year, month) {
    const firstDay = new Date(year, month, 1);
    const lastDay = new Date(year, month + 1, 0);
    const startWeek = firstDay.getDay();
    const totalDays = lastDay.getDate();

    const days = [];
    for (let i = 0; i < startWeek; i++) {
      days.push({ day: 0, date: '' });
    }
    for (let i = 1; i <= totalDays; i++) {
      const d = String(i).padStart(2, '0');
      const m = String(month + 1).padStart(2, '0');
      days.push({ day: i, date: `${year}-${m}-${d}` });
    }

    this.setData({
      days,
      currentMonth: `${year}年${month + 1}月`
    });
  },

  loadMonthData(year, month) {
    this.setData({ loading: true });
    const m = String(month + 1).padStart(2, '0');
    const startDate = `${year}-${m}-01`;
    const lastDay = new Date(year, month + 1, 0).getDate();
    const endDate = `${year}-${m}-${String(lastDay).padStart(2, '0')}`;

    httpApi.getEnergyRecords({ startDate, endDate, page: 1, size: 1000 }).then(res => {
      const data = res.data || res;
      const list = data.list || data.records || [];
      const dailyData = {};
      list.forEach(item => {
        const date = item.recordDate || item.date;
        if (!dailyData[date]) {
          dailyData[date] = { energy: 0, carbon: 0, cost: 0, count: 0 };
        }
        dailyData[date].energy += Number(item.value) || 0;
        dailyData[date].carbon += Number(item.carbon) || 0;
        dailyData[date].cost += Number(item.cost) || 0;
        dailyData[date].count += 1;
      });

      this.setData({ dailyData, loading: false });
      this.selectDay(this.data.selectedDate);
    }).catch(() => {
      this.setData({ loading: false });
    });
  },

  selectDay(date) {
    if (!date) return;
    const dayData = this.data.dailyData[date] || null;
    this.setData({ selectedDate: date, selectedDayData: dayData });
  },

  onDayTap(e) {
    const date = e.currentTarget.dataset.date;
    if (!date) return;
    this.selectDay(date);
  },

  prevMonth() {
    let { year, month } = this.data;
    month--;
    if (month < 0) { month = 11; year--; }
    this.setData({ year, month });
    this.generateCalendar(year, month);
    this.loadMonthData(year, month);
  },

  nextMonth() {
    let { year, month } = this.data;
    month++;
    if (month > 11) { month = 0; year++; }
    this.setData({ year, month });
    this.generateCalendar(year, month);
    this.loadMonthData(year, month);
  }
});
