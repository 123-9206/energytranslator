package com.energy.translator.service;

import com.energy.translator.entity.Device;
import com.energy.translator.entity.EnergyRecord;
import com.energy.translator.repository.DeviceRepository;
import com.energy.translator.repository.EnergyRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 驾驶舱 AI 分析聚合服务（管理后台专用）
 *
 * 一次调用返回驾驶舱 AI 面板所需的全部数据：
 * 1. AI 能效诊断摘要 — 复用 AIDiagnosisService（综合评分/优化建议/异常检测/设备能效排行）
 * 2. 未来 7 天用电预测 — 三日加权移动平均滚动预测
 * 3. 24 小时实时功率曲线 — 按小时聚合平均功率
 * 4. 数据采集状态 — 设备在线率 / 协议分布 / 最近采集时间
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AIAnalysisService {

    private final AIDiagnosisService aiDiagnosisService;
    private final EnergyRecordRepository energyRecordRepository;
    private final DeviceRepository deviceRepository;

    /**
     * 聚合分析入口
     *
     * @param enterpriseId 可选；为空时自动选择数据量最大的企业
     */
    public Map<String, Object> analyzeOverview(String enterpriseId, int days) {
        Map<String, Object> result = new LinkedHashMap<>();

        String eid = resolveEnterpriseId(enterpriseId);
        result.put("enterpriseId", eid);
        result.put("analysisTime", Instant.now().toString());

        // 1. AI 诊断摘要（复用已有 6 维度诊断引擎）
        result.put("diagnosis", buildDiagnosisSummary(eid, days));

        // 2. 未来 7 天预测
        result.put("forecast", buildForecast(eid, 7));

        // 3. 24 小时功率曲线
        result.put("powerCurve", buildPowerCurve(eid));

        // 4. 采集状态
        result.put("collection", buildCollectionStatus(eid));

        return result;
    }

    /**
     * 企业解析：未指定时取最近有能耗数据的企业（平台管理员视角）
     */
    private String resolveEnterpriseId(String enterpriseId) {
        if (enterpriseId != null && !enterpriseId.isBlank()) {
            return enterpriseId;
        }
        // 取最近有数据记录的企业（不限企业查询）
        List<EnergyRecord> recent = energyRecordRepository.findTop100Recent();
        if (!recent.isEmpty()) {
            EnergyRecord r = recent.get(0);
            if (r.getEnterpriseId() != null && !r.getEnterpriseId().isBlank()) {
                return r.getEnterpriseId();
            }
        }
        // 兜底：取第一个企业
        List<Device> devices = deviceRepository.findAll();
        if (!devices.isEmpty() && devices.get(0).getEnterpriseId() != null) {
            return devices.get(0).getEnterpriseId();
        }
        return "demo";
    }

    /**
     * 诊断摘要：从完整诊断结果中提取驾驶舱展示所需字段
     */
    private Map<String, Object> buildDiagnosisSummary(String eid, int days) {
        Map<String, Object> summary = new LinkedHashMap<>();
        try {
            Map<String, Object> full = aiDiagnosisService.diagnose(eid, null, days);

            summary.put("overallScore", full.getOrDefault("overallScore", 0));
            summary.put("scoreLevel", full.getOrDefault("scoreLevel", "无数据"));
            summary.put("estimatedAnnualSaving", full.getOrDefault("estimatedAnnualSaving", "0.00"));
            summary.put("estimatedSavingPercent", full.getOrDefault("estimatedSavingPercent", "0.0"));
            summary.put("totalEnergy", full.getOrDefault("totalEnergy", "0.00"));
            summary.put("totalCost", full.getOrDefault("totalCost", "0.00"));
            summary.put("totalCarbon", full.getOrDefault("totalCarbon", "0.00"));
            summary.put("peakValleyAnalysis", full.getOrDefault("peakValleyAnalysis", new LinkedHashMap<>()));

            // 优化建议（按优先级，取前 5）
            List<?> recs = (List<?>) full.getOrDefault("recommendations", Collections.emptyList());
            summary.put("recommendations", recs.stream().limit(5).collect(Collectors.toList()));

            // 异常事件（取前 5）
            List<?> anoms = (List<?>) full.getOrDefault("anomalies", Collections.emptyList());
            summary.put("anomalies", anoms.stream().limit(5).collect(Collectors.toList()));

            // 设备能效排行（按能耗降序，取前 5）
            List<?> devices = (List<?>) full.getOrDefault("deviceDiagnosis", Collections.emptyList());
            summary.put("deviceDiagnosis", devices.stream().limit(5).collect(Collectors.toList()));

        } catch (Exception e) {
            log.warn("AI 诊断摘要生成失败: {}", e.getMessage());
            summary.put("overallScore", 0);
            summary.put("scoreLevel", "暂不可用");
            summary.put("recommendations", Collections.emptyList());
            summary.put("anomalies", Collections.emptyList());
            summary.put("deviceDiagnosis", Collections.emptyList());
        }
        return summary;
    }

    /**
     * 未来 N 天用电预测
     * 算法：按天聚合近 7 日能耗 → 三日加权移动平均 (3/2/1) 滚动预测
     */
    private Map<String, Object> buildForecast(String eid, int days) {
        Map<String, Object> forecast = new LinkedHashMap<>();

        LocalDate today = LocalDate.now();
        Instant end = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant start = today.minusDays(14).atStartOfDay(ZoneId.systemDefault()).toInstant();

        List<EnergyRecord> records = energyRecordRepository.findByTimeRange(eid, start, end);
        if (records.isEmpty()) {
            forecast.put("method", "暂无历史数据");
            forecast.put("points", Collections.emptyList());
            return forecast;
        }

        ZoneId zone = ZoneId.systemDefault();
        // 近 7 天每日能耗（含今天，补 0）
        Map<LocalDate, Double> dailyMap = new HashMap<>();
        for (EnergyRecord r : records) {
            if (r.getTimestamp() == null) continue;
            LocalDate d = LocalDate.ofInstant(r.getTimestamp(), zone);
            if (!d.isBefore(today.minusDays(6))) {
                dailyMap.merge(d, r.getValue() == null ? 0 : r.getValue(), Double::sum);
            }
        }

        List<Double> daily = new ArrayList<>();
        List<LocalDate> actualDates = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            daily.add(dailyMap.getOrDefault(d, 0.0));
            actualDates.add(d);
        }

        List<Map<String, Object>> points = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate d = actualDates.get(i);
            Map<String, Object> pt = new LinkedHashMap<>();
            pt.put("date", d.toString());
            pt.put("label", d.getMonthValue() + "/" + d.getDayOfMonth());
            pt.put("value", round2(daily.get(i)));
            pt.put("type", "actual");
            points.add(pt);
        }

        // 滚动加权移动平均预测
        List<Double> seq = new ArrayList<>(daily);
        for (int i = 0; i < days; i++) {
            int n = seq.size();
            double w1 = seq.get(n - 1);
            double w2 = seq.get(n - 2);
            double w3 = seq.get(n - 3);
            double next = (3 * w1 + 2 * w2 + w3) / 6.0;
            seq.add(next);

            LocalDate d = today.plusDays(i + 1);
            Map<String, Object> pt = new LinkedHashMap<>();
            pt.put("date", d.toString());
            pt.put("label", d.getMonthValue() + "/" + d.getDayOfMonth());
            pt.put("value", round2(next));
            pt.put("type", "forecast");
            points.add(pt);
        }

        // 预估周电费（按均价 0.7 元/kWh 估算）
        double forecastSum = points.stream().filter(p -> "forecast".equals(p.get("type")))
                .mapToDouble(p -> (double) p.get("value")).sum();
        forecast.put("method", "三日加权移动平均");
        forecast.put("totalForecast", round2(forecastSum));
        forecast.put("estimatedCost", round2(forecastSum * 0.7));
        forecast.put("points", points);
        return forecast;
    }

    /**
     * 24 小时实时功率曲线：按小时聚合平均功率
     */
    private Map<String, Object> buildPowerCurve(String eid) {
        Map<String, Object> curve = new LinkedHashMap<>();

        Instant end = Instant.now();
        Instant start = end.minus(Duration.ofHours(25)); // 25h 窗口避免采集时间戳边界问题

        List<EnergyRecord> records = energyRecordRepository.findByTimeRange(eid, start, end);
        if (records.isEmpty()) {
            curve.put("points", Collections.emptyList());
            curve.put("maxPower", 0);
            return curve;
        }

        ZoneId zone = ZoneId.systemDefault();
        Map<Integer, List<Double>> hourPowers = new TreeMap<>();
        for (EnergyRecord r : records) {
            if (r.getTimestamp() == null) continue;
            int hour = LocalDateTime.ofInstant(r.getTimestamp(), zone).getHour();
            double p = r.getPower() != null ? r.getPower() : 0;
            hourPowers.computeIfAbsent(hour, k -> new ArrayList<>()).add(p);
        }

        List<Map<String, Object>> points = new ArrayList<>();
        double maxPower = 0;
        for (int h = 0; h < 24; h++) {
            List<Double> ps = hourPowers.get(h);
            double avg = (ps == null || ps.isEmpty()) ? 0 : ps.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            maxPower = Math.max(maxPower, avg);
            Map<String, Object> pt = new LinkedHashMap<>();
            pt.put("hour", String.format("%02d:00", h));
            pt.put("power", round2(avg));
            points.add(pt);
        }

        curve.put("points", points);
        curve.put("maxPower", round2(maxPower));
        return curve;
    }

    /**
     * 采集状态：设备在线率 / 协议分布 / 最近采集时间
     */
    private Map<String, Object> buildCollectionStatus(String eid) {
        Map<String, Object> status = new LinkedHashMap<>();

        List<Device> devices;
        try {
            devices = deviceRepository.findByEnterpriseId(eid);
        } catch (Exception e) {
            devices = deviceRepository.findAll();
        }
        if (devices == null) devices = Collections.emptyList();

        long online = devices.stream().filter(d -> "online".equals(d.getStatus())).count();
        long offline = devices.stream().filter(d -> "offline".equals(d.getStatus())).count();

        Map<String, Long> protocolCounts = devices.stream()
                .filter(d -> d.getProtocol() != null)
                .collect(Collectors.groupingBy(Device::getProtocol, Collectors.counting()));

        // 最近采集时间（企业最近一条记录）
        String lastCollect = null;
        try {
            List<EnergyRecord> recent = energyRecordRepository.findTop100ByEnterpriseIdOrderByTimestampDesc(eid);
            if (!recent.isEmpty() && recent.get(0).getTimestamp() != null) {
                lastCollect = recent.get(0).getTimestamp().toString();
            }
        } catch (Exception ignored) {}

        status.put("deviceTotal", devices.size());
        status.put("deviceOnline", online);
        status.put("deviceOffline", offline);
        status.put("onlineRate", devices.isEmpty() ? 0 : round2(online * 100.0 / devices.size()));
        status.put("protocols", protocolCounts);
        status.put("lastCollectTime", lastCollect);
        status.put("collecting", devices.isEmpty() ? false : online > 0);
        return status;
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
