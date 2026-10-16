package com.energy.translator.service;

import com.energy.translator.entity.EnergyRecord;
import com.energy.translator.repository.DeviceRepository;
import com.energy.translator.repository.EnergyRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AnalysisService {

    private final EnergyRecordRepository energyRecordRepository;
    private final DeviceRepository deviceRepository;

    @org.springframework.beans.factory.annotation.Value("${system.default-electricity-price:0.6}")
    private double electricityPrice;

    @org.springframework.beans.factory.annotation.Value("${system.default-carbon-factor:0.785}")
    private double carbonFactor;

    /**
     * 获取能耗趋势数据
     */
    public Map<String, Object> getTrend(String enterpriseId, String type, int days,
                                         String startDate, String endDate) {
        Instant start, end;
        LocalDate today = LocalDate.now();

        if (startDate != null && endDate != null) {
            start = LocalDate.parse(startDate).atStartOfDay(ZoneId.systemDefault()).toInstant();
            end = LocalDate.parse(endDate).plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
        } else {
            end = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
            start = switch (type) {
                case "hour" -> LocalDateTime.now().minusHours(24).atZone(ZoneId.systemDefault()).toInstant();
                case "week" -> today.minusWeeks(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
                case "month" -> today.minusMonths(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
                case "year" -> today.minusYears(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
                default -> today.minusDays(days).atStartOfDay(ZoneId.systemDefault()).toInstant();
            };
        }

        List<EnergyRecord> records = energyRecordRepository.findByTimeRange(enterpriseId, start, end);

        // 按时间维度聚合
        List<Map<String, Object>> trendData = aggregateByTime(records, type, start, end);

        // 计算统计值
        double totalEnergy = records.stream().mapToDouble(EnergyRecord::getValue).sum();
        double totalCost = totalEnergy * electricityPrice;
        double totalCarbon = totalEnergy * carbonFactor;
        double avgEnergy = records.isEmpty() ? 0 : totalEnergy / trendData.size();

        // 同比/环比
        long periodDays = java.time.Duration.between(start, end).toDays();
        Instant prevStart = start.minus(java.time.Duration.between(start, end));
        List<EnergyRecord> prevRecords = energyRecordRepository.findByTimeRange(enterpriseId, prevStart, start);
        double prevEnergy = prevRecords.stream().mapToDouble(EnergyRecord::getValue).sum();
        double changePercent = prevEnergy == 0 ? 0 : ((totalEnergy - prevEnergy) / prevEnergy) * 100;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("trend", trendData);
        result.put("totalEnergy", totalEnergy);
        result.put("totalCost", totalCost);
        result.put("totalCarbon", totalCarbon);
        result.put("avgEnergy", avgEnergy);
        result.put("changePercent", changePercent);
        result.put("period", type);
        result.put("dataPoints", trendData.size());

        return result;
    }

    /**
     * 获取能耗排名
     */
    public Map<String, Object> getRanking(String enterpriseId, String dimension,
                                           String period, String siteId) {
        LocalDate today = LocalDate.now();
        Instant start, end;

        end = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
        start = switch (period) {
            case "week" -> today.minusWeeks(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
            case "year" -> today.minusYears(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
            default -> today.withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
        };

        List<EnergyRecord> records;
        if (siteId != null) {
            records = energyRecordRepository.findByTimeRange(enterpriseId, start, end);
            records = records.stream().filter(r -> siteId.equals(r.getSiteId())).collect(Collectors.toList());
        } else {
            records = energyRecordRepository.findByTimeRange(enterpriseId, start, end);
        }

        // 按维度聚合排名
        List<Map<String, Object>> ranking;
        if ("device".equals(dimension)) {
            ranking = records.stream()
                    .filter(r -> r.getResourceId() != null)
                    .collect(Collectors.groupingBy(EnergyRecord::getResourceId,
                            Collectors.summingDouble(EnergyRecord::getValue)))
                    .entrySet().stream()
                    .map(entry -> {
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("id", entry.getKey());
                        item.put("energy", entry.getValue());
                        item.put("cost", entry.getValue() * electricityPrice);
                        item.put("carbon", entry.getValue() * carbonFactor);
                        return item;
                    })
                    .sorted((a, b) -> Double.compare((Double) b.get("energy"), (Double) a.get("energy")))
                    .collect(Collectors.toList());
        } else {
            ranking = records.stream()
                    .filter(r -> r.getSiteId() != null)
                    .collect(Collectors.groupingBy(EnergyRecord::getSiteId,
                            Collectors.summingDouble(EnergyRecord::getValue)))
                    .entrySet().stream()
                    .map(entry -> {
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("id", entry.getKey());
                        item.put("energy", entry.getValue());
                        item.put("cost", entry.getValue() * electricityPrice);
                        item.put("carbon", entry.getValue() * carbonFactor);
                        return item;
                    })
                    .sorted((a, b) -> Double.compare((Double) b.get("energy"), (Double) a.get("energy")))
                    .collect(Collectors.toList());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ranking", ranking);
        result.put("total", records.stream().mapToDouble(EnergyRecord::getValue).sum());
        result.put("dimension", dimension);
        result.put("period", period);

        return result;
    }

    /**
     * 获取能耗统计摘要
     */
    public Map<String, Object> getSummary(String enterpriseId, String period, String siteId) {
        LocalDate today = LocalDate.now();
        Instant start, end;

        end = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
        start = switch (period) {
            case "day" -> today.atStartOfDay(ZoneId.systemDefault()).toInstant();
            case "week" -> today.minusWeeks(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
            case "year" -> today.minusYears(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
            default -> today.withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
        };

        List<EnergyRecord> records = energyRecordRepository.findByTimeRange(enterpriseId, start, end);
        if (siteId != null) {
            records = records.stream().filter(r -> siteId.equals(r.getSiteId())).collect(Collectors.toList());
        }

        double totalEnergy = records.stream().mapToDouble(EnergyRecord::getValue).sum();
        double maxEnergy = records.stream().mapToDouble(EnergyRecord::getValue).max().orElse(0);
        double minEnergy = records.stream().mapToDouble(EnergyRecord::getValue).min().orElse(0);
        double avgEnergy = records.isEmpty() ? 0 : totalEnergy / records.size();

        // 峰值/谷值时间
        Instant peakTime = records.stream()
                .max(Comparator.comparingDouble(EnergyRecord::getValue))
                .map(EnergyRecord::getTimestamp).orElse(null);
        Instant valleyTime = records.stream()
                .min(Comparator.comparingDouble(EnergyRecord::getValue))
                .map(EnergyRecord::getTimestamp).orElse(null);

        // 设备数量统计
        long deviceCount = deviceRepository.countByEnterpriseId(enterpriseId);
        long onlineCount = deviceRepository.findByEnterpriseIdAndStatus(enterpriseId, "online").size();

        // 数据来源分布
        Map<String, Long> sourceDistribution = records.stream()
                .collect(Collectors.groupingBy(r -> r.getSource() != null ? r.getSource() : "unknown", Collectors.counting()));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalEnergy", totalEnergy);
        result.put("totalCost", totalEnergy * electricityPrice);
        result.put("totalCarbon", totalEnergy * carbonFactor);
        result.put("maxEnergy", maxEnergy);
        result.put("minEnergy", minEnergy);
        result.put("avgEnergy", avgEnergy);
        result.put("peakTime", peakTime);
        result.put("valleyTime", valleyTime);
        result.put("recordCount", records.size());
        result.put("deviceCount", deviceCount);
        result.put("onlineDeviceCount", onlineCount);
        result.put("sourceDistribution", sourceDistribution);
        result.put("period", period);

        return result;
    }

    /**
     * 获取分析详情
     */
    public Map<String, Object> getDetail(String enterpriseId, String deviceId,
                                          String siteId, String period, int page, int size) {
        LocalDate today = LocalDate.now();
        Instant start, end;

        end = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
        start = switch (period) {
            case "week" -> today.minusWeeks(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
            case "month" -> today.minusMonths(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
            case "year" -> today.minusYears(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
            default -> today.atStartOfDay(ZoneId.systemDefault()).toInstant();
        };

        List<EnergyRecord> allRecords = energyRecordRepository.findByTimeRange(enterpriseId, start, end);

        // 过滤
        if (deviceId != null) {
            allRecords = allRecords.stream()
                    .filter(r -> deviceId.equals(r.getResourceId()))
                    .collect(Collectors.toList());
        }
        if (siteId != null) {
            allRecords = allRecords.stream()
                    .filter(r -> siteId.equals(r.getSiteId()))
                    .collect(Collectors.toList());
        }

        // 分页
        int total = allRecords.size();
        int fromIndex = Math.min((page - 1) * size, total);
        int toIndex = Math.min(fromIndex + size, total);
        List<EnergyRecord> records = allRecords.subList(fromIndex, toIndex);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", records);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        result.put("totalPages", (int) Math.ceil((double) total / size));

        // 基本统计
        result.put("totalEnergy", allRecords.stream().mapToDouble(EnergyRecord::getValue).sum());
        result.put("avgEnergy", allRecords.isEmpty() ? 0 :
                allRecords.stream().mapToDouble(EnergyRecord::getValue).average().orElse(0));

        return result;
    }

    /**
     * 按时间维度聚合数据
     */
    private List<Map<String, Object>> aggregateByTime(List<EnergyRecord> records,
                                                       String type, Instant start, Instant end) {
        DateTimeFormatter formatter;
        if ("hour".equals(type)) {
            formatter = DateTimeFormatter.ofPattern("HH:00");
        } else if ("week".equals(type) || "year".equals(type)) {
            formatter = DateTimeFormatter.ofPattern("MM-dd");
        } else {
            formatter = DateTimeFormatter.ofPattern("MM-dd");
        }

        Map<String, List<EnergyRecord>> grouped = new LinkedHashMap<>();
        ZoneId zone = ZoneId.systemDefault();
        LocalDate startDate = LocalDate.ofInstant(start, zone);
        LocalDate endDate = LocalDate.ofInstant(end, zone).minusDays(1);

        if ("hour".equals(type)) {
            // 按小时分组（最近24小时）
            for (int i = 0; i < 24; i++) {
                LocalDateTime hourStart = LocalDateTime.ofInstant(start, zone).plusHours(i);
                String key = hourStart.format(formatter);
                final Instant hStart = hourStart.atZone(zone).toInstant();
                final Instant hEnd = hourStart.plusHours(1).atZone(zone).toInstant();
                List<EnergyRecord> hourRecords = records.stream()
                        .filter(r -> r.getTimestamp() != null &&
                                !r.getTimestamp().isBefore(hStart) &&
                                r.getTimestamp().isBefore(hEnd))
                        .collect(Collectors.toList());
                grouped.put(key, hourRecords);
            }
        } else {
            // 按天分组
            LocalDate current = startDate;
            while (!current.isAfter(endDate)) {
                String key = current.format(formatter);
                Instant dayStart = current.atStartOfDay(zone).toInstant();
                Instant dayEnd = current.plusDays(1).atStartOfDay(zone).toInstant();
                List<EnergyRecord> dayRecords = records.stream()
                        .filter(r -> r.getTimestamp() != null &&
                                !r.getTimestamp().isBefore(dayStart) &&
                                r.getTimestamp().isBefore(dayEnd))
                        .collect(Collectors.toList());
                grouped.put(key, dayRecords);
                current = current.plusDays(1);
            }
        }

        return grouped.entrySet().stream()
                .map(entry -> {
                    Map<String, Object> point = new LinkedHashMap<>();
                    point.put("label", entry.getKey());
                    double energy = entry.getValue().stream().mapToDouble(EnergyRecord::getValue).sum();
                    point.put("energy", energy);
                    point.put("cost", energy * electricityPrice);
                    point.put("carbon", energy * carbonFactor);
                    point.put("count", entry.getValue().size());
                    return point;
                })
                .collect(Collectors.toList());
    }
}
