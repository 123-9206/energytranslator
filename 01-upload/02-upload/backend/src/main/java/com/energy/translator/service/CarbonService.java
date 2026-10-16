package com.energy.translator.service;

import com.energy.translator.dto.CarbonReportDTO;
import com.energy.translator.entity.EnergyRecord;
import com.energy.translator.repository.EnergyRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CarbonService {

    private final EnergyRecordRepository energyRecordRepository;

    @Value("${system.default-carbon-factor:0.785}")
    private double carbonFactor;

    /**
     * 获取碳排放报告（按时间段）
     */
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
        double totalUsage = records.stream().mapToDouble(EnergyRecord::getValue).sum();
        double totalCarbon = totalUsage * carbonFactor;

        // 按站点分组计算碳排放来源
        Map<String, Double> siteCarbonMap = records.stream()
                .filter(r -> r.getSiteId() != null)
                .collect(Collectors.groupingBy(
                        EnergyRecord::getSiteId,
                        Collectors.summingDouble(r -> (r.getValue() != null ? r.getValue() : 0.0) * carbonFactor)
                ));

        List<CarbonReportDTO.CarbonSource> sources = siteCarbonMap.entrySet().stream()
                .map(entry -> CarbonReportDTO.CarbonSource.builder()
                        .name(entry.getKey())
                        .value(entry.getValue())
                        .percentage(totalCarbon > 0 ? (entry.getValue() / totalCarbon) * 100 : 0)
                        .build())
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .collect(Collectors.toList());

        if (sources.isEmpty()) {
            sources = Arrays.asList(
                    CarbonReportDTO.CarbonSource.builder().name("暂无数据").value(0.0).percentage(0.0).build()
            );
        }

        return CarbonReportDTO.builder()
                .totalCarbon(totalCarbon)
                .todayCarbon(calculatePeriodCarbon(enterpriseId, "day"))
                .monthCarbon(calculatePeriodCarbon(enterpriseId, "month"))
                .yearCarbon(calculatePeriodCarbon(enterpriseId, "year"))
                .carbonIntensity(totalUsage > 0 ? totalCarbon / totalUsage : 0)
                .equivalentReduction(String.format("相当于植树 %.0f 棵", totalCarbon / 0.05))
                .trends(generateTrends(enterpriseId, start, end))
                .sources(sources)
                .build();
    }

    /**
     * 获取碳排放趋势数据
     */
    public List<CarbonReportDTO.CarbonTrend> getCarbonTrend(String enterpriseId, String period) {
        LocalDate now = LocalDate.now();
        Instant start, end;
        int days;

        switch (period != null ? period : "month") {
            case "week":
                start = now.minusWeeks(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
                days = 7;
                break;
            case "year":
                start = now.minusDays(30).atStartOfDay(ZoneId.systemDefault()).toInstant();
                days = 30;
                break;
            default: // month
                start = now.withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
                days = now.getDayOfMonth();
        }
        end = now.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();

        return generateTrends(enterpriseId, start, end);
    }

    /**
     * 获取碳排放摘要统计
     */
    public Map<String, Object> getCarbonSummary(String enterpriseId) {
        LocalDate now = LocalDate.now();

        double todayCarbon = calculatePeriodCarbon(enterpriseId, "day");
        double monthCarbon = calculatePeriodCarbon(enterpriseId, "month");
        double yearCarbon = calculatePeriodCarbon(enterpriseId, "year");

        // 计算环比变化（本月 vs 上月）
        double lastMonthCarbon = calculateLastPeriodCarbon(enterpriseId, "month");
        double monthChange = lastMonthCarbon > 0
                ? ((monthCarbon - lastMonthCarbon) / lastMonthCarbon) * 100
                : 0;

        return Map.of(
                "todayCarbon", todayCarbon,
                "monthCarbon", monthCarbon,
                "yearCarbon", yearCarbon,
                "monthChange", String.format("%.1f", monthChange),
                "monthChangeDirection", monthChange <= 0 ? "down" : "up",
                "treesEquivalent", String.format("%.0f", monthCarbon / 0.05)
        );
    }

    private double calculatePeriodCarbon(String enterpriseId, String period) {
        LocalDate now = LocalDate.now();
        Instant start, end;

        switch (period) {
            case "day":
                start = now.atStartOfDay(ZoneId.systemDefault()).toInstant();
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

        List<EnergyRecord> records = energyRecordRepository.findByTimeRange(enterpriseId, start, end);
        return records.stream().mapToDouble(r -> (r.getValue() != null ? r.getValue() : 0.0) * carbonFactor).sum();
    }

    private double calculateLastPeriodCarbon(String enterpriseId, String period) {
        LocalDate now = LocalDate.now();
        Instant start, end;

        switch (period) {
            case "month":
                start = now.minusMonths(1).withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
                end = now.withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
                break;
            case "year":
                start = now.minusYears(1).withDayOfYear(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
                end = now.withDayOfYear(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
                break;
            default:
                return 0;
        }

        List<EnergyRecord> records = energyRecordRepository.findByTimeRange(enterpriseId, start, end);
        return records.stream().mapToDouble(r -> (r.getValue() != null ? r.getValue() : 0.0) * carbonFactor).sum();
    }

    private List<CarbonReportDTO.CarbonTrend> generateTrends(String enterpriseId, Instant start, Instant end) {
        List<EnergyRecord> records = energyRecordRepository.findByTimeRange(enterpriseId, start, end);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd");

        // 按天分组计算碳排放
        Map<LocalDate, Double> dailyCarbon = records.stream()
                .collect(Collectors.groupingBy(
                        r -> LocalDate.ofInstant(r.getTimestamp() != null ? r.getTimestamp() : r.getCreatedAt(), ZoneId.systemDefault()),
                        Collectors.summingDouble(r -> (r.getValue() != null ? r.getValue() : 0.0) * carbonFactor)
                ));

        LocalDate startDate = LocalDate.ofInstant(start, ZoneId.systemDefault());
        LocalDate endDate = LocalDate.ofInstant(end, ZoneId.systemDefault());
        List<CarbonReportDTO.CarbonTrend> trends = new ArrayList<>();

        while (!startDate.isAfter(endDate)) {
            trends.add(CarbonReportDTO.CarbonTrend.builder()
                    .date(startDate.format(formatter))
                    .value(dailyCarbon.getOrDefault(startDate, 0.0))
                    .build());
            startDate = startDate.plusDays(1);
        }

        return trends;
    }
}
