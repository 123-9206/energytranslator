package com.energy.translator.service;

import com.energy.translator.entity.*;
import com.energy.translator.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * 告警引擎服务
 * 每秒检查启用的告警规则，评估最新能耗数据是否触发阈值。
 * 
 * 触发逻辑：
 *   1. 遍历所有启用规则
 *   2. 查询对应设备最近的能耗数据
 *   3. 根据规则条件(gt/lt/eq/gte/lte)判断是否触发
 *   4. 检查持续时长要求（durationSeconds）
 *   5. 检查冷却期（cooldownSeconds），防止重复告警
 *   6. 创建 Alarm 记录并持久化
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertEngineService {

    private final AlarmRuleRepository alarmRuleRepository;
    private final AlarmRepository alarmRepository;
    private final EnergyRecordRepository energyRecordRepository;
    private final DeviceRepository deviceRepository;

    // 记录每个规则的最近触-发时间，用于冷却期控制
    private final Map<String, Instant> lastTriggerTime = new HashMap<>();

    /**
     * 每30秒执行一次告警规则评估
     */
    @Scheduled(fixedDelay = 30000, initialDelay = 15000)
    public void evaluateRules() {
        try {
            List<AlarmRule> enabledRules = alarmRuleRepository.findByEnabled(true);
            if (enabledRules.isEmpty()) return;

            log.debug("告警引擎: 开始评估 {} 条已启用规则", enabledRules.size());

            for (AlarmRule rule : enabledRules) {
                try {
                    evaluateRule(rule);
                } catch (Exception e) {
                    log.error("告警规则评估失败: rule={}, error={}", rule.getName(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("告警引擎批量评估异常: {}", e.getMessage());
        }
    }

    /**
     * 评估单条告警规则
     */
    private void evaluateRule(AlarmRule rule) {
        // 冷却期检查
        if (rule.getCooldownSeconds() != null && rule.getCooldownSeconds() > 0) {
            Instant lastTrigger = lastTriggerTime.get(rule.getId());
            if (lastTrigger != null) {
                long secondsSinceLast = ChronoUnit.SECONDS.between(lastTrigger, Instant.now());
                if (secondsSinceLast < rule.getCooldownSeconds()) {
                    return; // 冷却期内，跳过
                }
            }
        }

        // 获取最近的设备能耗数据
        String enterpriseId = rule.getEnterpriseId();
        String deviceId = rule.getDeviceId();

        List<EnergyRecord> recentRecords;
        if (deviceId != null) {
            recentRecords = energyRecordRepository.findTop100ByEnterpriseIdAndResourceIdOrderByTimestampDesc(
                    enterpriseId, deviceId);
        } else {
            recentRecords = energyRecordRepository.findTop100ByEnterpriseIdOrderByTimestampDesc(enterpriseId);
        }

        if (recentRecords.isEmpty()) return;

        // 取最新值
        EnergyRecord latest = recentRecords.get(0);

        // 获取指标值
        double metricValue = getMetricValue(latest, rule.getMetric());

        // 判断是否触发阈值
        boolean triggered = checkCondition(metricValue, rule.getCondition(), rule.getThreshold());

        // 持续时长检查
        if (triggered && rule.getDurationSeconds() != null && rule.getDurationSeconds() > 0) {
            triggered = checkDuration(recentRecords, rule);
        }

        if (triggered) {
            createAlarm(rule, latest, metricValue);
            lastTriggerTime.put(rule.getId(), Instant.now());
        }
    }

    /**
     * 获取指标值 — 支持全部6种采集指标
     */
    private double getMetricValue(EnergyRecord record, String metric) {
        if (metric == null) return record.getValue() != null ? record.getValue() : 0;
        return switch (metric.toLowerCase()) {
            case "power", "有功功率" -> record.getPower() != null ? record.getPower() : 0;
            case "energy", "consumption", "能耗" -> record.getValue() != null ? record.getValue() : 0;
            case "current", "电流" -> record.getCurrent() != null ? record.getCurrent() : 0;
            case "voltage", "电压" -> record.getVoltage() != null ? record.getVoltage() : 0;
            case "temperature", "温度" -> record.getTemperature() != null ? record.getTemperature() : 0;
            case "carbon", "碳排放" -> record.getCarbonEmission() != null ? record.getCarbonEmission() : 0;
            case "power_factor", "功率因数" -> record.getPowerFactor() != null ? record.getPowerFactor() : 0;
            default -> record.getValue() != null ? record.getValue() : 0;
        };
    }

    /**
     * 条件判断
     */
    private boolean checkCondition(double value, String condition, Double threshold) {
        if (threshold == null) return false;
        return switch (condition != null ? condition : "gt") {
            case "gt" -> value > threshold;
            case "lt" -> value < threshold;
            case "gte" -> value >= threshold;
            case "lte" -> value <= threshold;
            case "eq" -> Math.abs(value - threshold) < 0.001;
            default -> value > threshold;
        };
    }

    /**
     * 持续性检查：在持续时长内，连续超过阈值的记录比例高于 80%
     */
    private boolean checkDuration(List<EnergyRecord> records, AlarmRule rule) {
        int durationSeconds = rule.getDurationSeconds();
        Instant cutoff = Instant.now().minusSeconds(durationSeconds);

        long violatingCount = records.stream()
                .filter(r -> r.getTimestamp() != null && r.getTimestamp().isAfter(cutoff))
                .filter(r -> checkCondition(getMetricValue(r, rule.getMetric()), rule.getCondition(), rule.getThreshold()))
                .count();

        long totalInWindow = records.stream()
                .filter(r -> r.getTimestamp() != null && r.getTimestamp().isAfter(cutoff))
                .count();

        return totalInWindow > 0 && (double) violatingCount / totalInWindow >= 0.8;
    }

    /**
     * 创建告警记录
     */
    private void createAlarm(AlarmRule rule, EnergyRecord record, double metricValue) {
        Device device = rule.getDeviceId() != null ? 
                deviceRepository.findById(rule.getDeviceId()).orElse(null) : null;

        String title = String.format("[%s] %s - %s %.2f (阈值%.2f)",
                getSeverityLabel(rule.getSeverity()),
                rule.getName(),
                getMetricLabel(rule.getMetric()),
                metricValue,
                rule.getThreshold());

        String description = buildAlarmDescription(rule, device, metricValue, record);

        Alarm alarm = Alarm.builder()
                .enterpriseId(rule.getEnterpriseId())
                .deviceId(rule.getDeviceId())
                .deviceName(device != null ? device.getName() : rule.getDeviceName())
                .ruleId(rule.getId())
                .ruleName(rule.getName())
                .type(rule.getMetric())
                .level(rule.getSeverity())
                .title(title)
                .description(description)
                .triggerValue(metricValue)
                .thresholdValue(rule.getThreshold())
                .condition(rule.getCondition())
                .status("active")
                .occurredAt(Instant.now())
                .createdAt(Instant.now())
                .build();

        alarmRepository.save(alarm);
        log.warn("告警触发: {}, device={}, value={}, threshold={}", 
                rule.getName(), rule.getDeviceName(), metricValue, rule.getThreshold());
    }

    private String buildAlarmDescription(AlarmRule rule, Device device, double metricValue, EnergyRecord record) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("规则「%s」触发告警\n", rule.getName()));
        sb.append(String.format("监控指标: %s\n", getMetricLabel(rule.getMetric())));
        sb.append(String.format("当前值: %.2f\n", metricValue));
        sb.append(String.format("阈值: %.2f (%s)\n", rule.getThreshold(), getConditionLabel(rule.getCondition())));

        if (device != null) {
            sb.append(String.format("设备: %s (%s)\n", device.getName(), device.getType()));
            if (device.getSiteId() != null) {
                sb.append(String.format("站点: %s\n", device.getSiteId()));
            }
        }

        sb.append(String.format("触发时间: %s\n", Instant.now().toString()));

        if (rule.getDescription() != null && !rule.getDescription().isEmpty()) {
            sb.append(String.format("处理建议: %s", rule.getDescription()));
        } else {
            sb.append("处理建议: 请检查设备运行状态，排查异常原因");
        }

        return sb.toString();
    }

    private String getSeverityLabel(String severity) {
        return switch (severity) {
            case "critical" -> "严重";
            case "warning" -> "警告";
            case "info" -> "信息";
            default -> severity;
        };
    }

    private String getMetricLabel(String metric) {
        return switch (metric != null ? metric.toLowerCase() : "") {
            case "energy" -> "能耗(kWh)";
            case "power" -> "功率(kW)";
            case "current" -> "电流(A)";
            case "voltage" -> "电压(V)";
            case "temperature" -> "温度(°C)";
            case "carbon" -> "碳排放(kg)";
            case "power_factor", "功率因数" -> "功率因数";
            default -> metric != null ? metric : "未知";
        };
    }

    private String getConditionLabel(String condition) {
        return switch (condition) {
            case "gt" -> "大于";
            case "lt" -> "小于";
            case "gte" -> "大于等于";
            case "lte" -> "小于等于";
            case "eq" -> "等于";
            default -> condition;
        };
    }
}
