package com.energy.translator.service;

import com.energy.translator.dto.CarbonReportDTO;
import com.energy.translator.dto.DashboardDTO;
import com.energy.translator.entity.EnergyRecord;
import com.energy.translator.repository.EnergyRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final EnergyRecordRepository energyRecordRepository;

    @Value("${system.default-electricity-price:0.6}")
    private double electricityPrice;

    @Value("${system.default-carbon-factor:0.785}")
    private double carbonFactor;

    public DashboardDTO getDashboard(String enterpriseId, String period) {
        // 获取今日数据
        LocalDate today = LocalDate.now();
        Instant todayStart = today.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant todayEnd = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();

        List<EnergyRecord> todayRecords = energyRecordRepository.findByTimeRange(enterpriseId, todayStart, todayEnd);
        double todayUsage = todayRecords.stream().mapToDouble(EnergyRecord::getValue).sum();
        double todayCost = todayUsage * electricityPrice;
        double todayCarbon = todayUsage * carbonFactor;

        // 获取本月数据
        LocalDate monthStart = today.withDayOfMonth(1);
        Instant monthStartInstant = monthStart.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant monthEndInstant = todayEnd;

        List<EnergyRecord> monthRecords = energyRecordRepository.findByTimeRange(enterpriseId, monthStartInstant, monthEndInstant);
        double monthUsage = monthRecords.stream().mapToDouble(EnergyRecord::getValue).sum();
        double monthCost = monthUsage * electricityPrice;
        double monthCarbon = monthUsage * carbonFactor;

        // 计算评分
        int score = calculateScore(monthUsage, todayUsage);

        // 获取昨日数据用于对比-使用真实数据
        Instant yesterdayStart = today.minusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant yesterdayEnd = today.atStartOfDay(ZoneId.systemDefault()).toInstant();
        List<EnergyRecord> yesterdayRecords = energyRecordRepository.findByTimeRange(enterpriseId, yesterdayStart, yesterdayEnd);
        double yesterdayUsage = yesterdayRecords.stream().mapToDouble(EnergyRecord::getValue).sum();
        String trendClass = todayUsage < yesterdayUsage ? "trend-down" : "trend-up";
        String trendText = todayUsage < yesterdayUsage ? 
                String.format("↓ 比昨日低 %.0f%%", (1 - todayUsage / yesterdayUsage) * 100) :
                String.format("↑ 比昨日高 %.0f%%", (todayUsage / yesterdayUsage - 1) * 100);

        if (todayUsage == 0) {
            trendClass = "";
            trendText = "";
        }

        // 动态节能建议
        List<String> tips = new ArrayList<>();
        if (todayUsage > yesterdayUsage * 1.1 && yesterdayUsage > 0) {
            tips.add("今日能耗较昨日上升超过10%，请排查是否有异常设备运行");
        }
        if (todayUsage == 0) {
            tips.add("今日暂无能耗数据，请检查设备数据采集是否正常");
        } else if (monthUsage / LocalDate.now().getDayOfMonth() > 0) {
            double avgDaily = monthUsage / LocalDate.now().getDayOfMonth();
            if (todayUsage > avgDaily * 1.2) {
                tips.add("今日能耗高于本月日平均水平，建议关注大功率设备使用情况");
            }
        }
        tips.add("谷电时段(0:00-8:00)电价最低，可将大功率设备安排在该时段运行");

        // 季节感知横幅建议
        DashboardDTO.BannerTip bannerTip;
        int month = LocalDate.now().getMonthValue();
        if (month >= 6 && month <= 9) {
            bannerTip = DashboardDTO.BannerTip.builder()
                    .title("夏季空调节能指南")
                    .desc("制冷温度设置在26°C，每调高1°C可节电约8%")
                    .build();
        } else if (month >= 12 || month <= 2) {
            bannerTip = DashboardDTO.BannerTip.builder()
                    .title("冬季供暖节能建议")
                    .desc("合理利用储能设备谷电储热，降低峰时供暖成本")
                    .build();
        } else {
            bannerTip = DashboardDTO.BannerTip.builder()
                    .title("春季设备保养")
                    .desc("定期清洗滤网和换热器，可提升设备能效10-15%")
                    .build();
        }

        return DashboardDTO.builder()
                .score(score)
                .todayUsage(String.format("%.1f", todayUsage))
                .todayCost(String.format("%.2f", todayCost))
                .todayCarbon(String.format("%.1f", todayCarbon))
                .todayRank("TOP 35%")
                .monthUsage(String.format("%.1f", monthUsage))
                .monthCost(String.format("%.2f", monthCost))
                .monthCarbon(String.format("%.1f", monthCarbon))
                .todayTrend(DashboardDTO.TrendInfo.builder()
                        .className(trendClass)
                        .text(trendText)
                        .build())
                .tips(tips)
                .bannerTip(bannerTip)
                .build();
    }

    public CarbonReportDTO getCarbonReport(String enterpriseId, String period, String startDate, String endDate) {
        Instant start, end;
        
        if (startDate != null && endDate != null) {
            start = LocalDate.parse(startDate).atStartOfDay(ZoneId.systemDefault()).toInstant();
            end = LocalDate.parse(endDate).plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
        } else {
            LocalDate now = LocalDate.now();
            switch (period != null ? period : "month") {
                case "day":
                    start = now.atStartOfDay(ZoneId.systemDefault()).toInstant();
                    end = now.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
                    break;
                case "week":
                    start = now.minusWeeks(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
                    end = now.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
                    break;
                case "year":
                    start = now.withDayOfYear(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
                    end = now.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
                    break;
                default: // month
                    start = now.withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
                    end = now.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
            }
        }

        List<EnergyRecord> records = energyRecordRepository.findByTimeRange(enterpriseId, start, end);
        double totalCarbon = records.stream().mapToDouble(r -> (r.getValue() != null ? r.getValue() : 0.0) * carbonFactor).sum();

        // 今日碳排放
        Instant todayStart2 = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant todayEnd2 = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
        List<EnergyRecord> todayRecords2 = energyRecordRepository.findByTimeRange(enterpriseId, todayStart2, todayEnd2);
        double todayCarbon = todayRecords2.stream().mapToDouble(r -> (r.getValue() != null ? r.getValue() : 0.0) * carbonFactor).sum();

        // 按站点计算真实的碳排放来源分布
        List<CarbonReportDTO.CarbonSource> sources = calculateRealCarbonSources(records, totalCarbon);

        return CarbonReportDTO.builder()
                .totalCarbon(totalCarbon)
                .todayCarbon(todayCarbon)
                .monthCarbon(totalCarbon)
                .yearCarbon(totalCarbon * 12)
                .carbonIntensity(records.isEmpty() ? 0 : totalCarbon / records.stream().mapToDouble(EnergyRecord::getValue).sum())
                .equivalentReduction(String.format("相当于植树 %.0f 棵", totalCarbon / 0.05))
                .trends(generateRealTrends(enterpriseId, start, end))
                .sources(sources)
                .build();
    }

    /**
     * 基于实际站点数据计算碳排放来源分布
     */
    private List<CarbonReportDTO.CarbonSource> calculateRealCarbonSources(List<EnergyRecord> records, double totalCarbon) {
        // 按站点分组计算碳排
        Map<String, Double> siteCarbon = new java.util.LinkedHashMap<>();
        for (EnergyRecord r : records) {
            String site = r.getSiteId() != null ? r.getSiteId() : "未分类";
            siteCarbon.merge(site, (r.getValue() != null ? r.getValue() : 0.0) * carbonFactor, Double::sum);
        }

        if (siteCarbon.isEmpty()) {
            return List.of(CarbonReportDTO.CarbonSource.builder()
                    .name("电力").value(0.0).percentage(100.0).build());
        }

        return siteCarbon.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(5)
                .map(entry -> CarbonReportDTO.CarbonSource.builder()
                        .name(entry.getKey())
                        .value(entry.getValue())
                        .percentage(totalCarbon > 0 ? entry.getValue() / totalCarbon * 100 : 0)
                        .build())
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 基于真实能耗数据生成碳排放趋势
     */
    private List<CarbonReportDTO.CarbonTrend> generateRealTrends(String enterpriseId, Instant start, Instant end) {
        List<EnergyRecord> allRecords = energyRecordRepository.findByTimeRange(enterpriseId, start, end);
        Map<String, Double> dailyCarbon = new java.util.LinkedHashMap<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd");
        ZoneId zone = ZoneId.systemDefault();

        for (EnergyRecord r : allRecords) {
            if (r.getTimestamp() == null) continue;
            String day = LocalDate.ofInstant(r.getTimestamp(), zone).format(formatter);
            dailyCarbon.merge(day, (r.getValue() != null ? r.getValue() : 0.0) * carbonFactor, Double::sum);
        }

        // 填充没有数据的天数为0
        List<CarbonReportDTO.CarbonTrend> trends = new ArrayList<>();
        LocalDate current = LocalDate.ofInstant(start, zone);
        LocalDate endDate = LocalDate.ofInstant(end, zone);
        while (!current.isAfter(endDate)) {
            String key = current.format(formatter);
            trends.add(CarbonReportDTO.CarbonTrend.builder()
                    .date(key)
                    .value(dailyCarbon.getOrDefault(key, 0.0))
                    .build());
            current = current.plusDays(1);
        }
        return trends;
    }

    private int calculateScore(double monthUsage, double todayUsage) {
        if (monthUsage == 0) return 0;
        
        // 基于用能效率计算评分
        double efficiency = todayUsage / (monthUsage / LocalDate.now().getDayOfMonth());
        if (efficiency < 0.8) return 95;
        if (efficiency < 1.0) return 85;
        if (efficiency < 1.2) return 75;
        if (efficiency < 1.5) return 60;
        return 45;
    }

    private List<CarbonReportDTO.CarbonTrend> generateTrends(Instant start, Instant end) {
        List<CarbonReportDTO.CarbonTrend> trends = new ArrayList<>();
        LocalDate startDate = LocalDate.ofInstant(start, ZoneId.systemDefault());
        LocalDate endDate = LocalDate.ofInstant(end, ZoneId.systemDefault());
        
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd");
        
        while (!startDate.isAfter(endDate)) {
            trends.add(CarbonReportDTO.CarbonTrend.builder()
                    .date(startDate.format(formatter))
                    .value(Math.random() * 100 + 50)
                    .build());
            startDate = startDate.plusDays(1);
        }
        
        return trends;
    }
}
