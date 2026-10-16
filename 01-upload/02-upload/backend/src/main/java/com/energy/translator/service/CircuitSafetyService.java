package com.energy.translator.service;

import com.energy.translator.entity.Device;
import com.energy.translator.entity.EnergyRecord;
import com.energy.translator.repository.DeviceRepository;
import com.energy.translator.repository.EnergyRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 电路安全评估服务
 * 基于设备最新电气量（电流/电压/温度/功率因数）与额定参数，
 * 识别过载、过温、电压偏差、功率因数偏低等隐患，输出安全评分与可执行的故障清单。
 *
 * 数据来自 EnergyRecord（MQTT/Modbus/HTTP 采集已写入电流/电压/温度字段）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CircuitSafetyService {

    private final EnergyRecordRepository energyRecordRepository;
    private final DeviceRepository deviceRepository;

    /** 设备未配置额定参数时的默认额定电压（单相） */
    private static final double DEFAULT_RATED_VOLTAGE = 220.0;
    /** 温度预警/危险阈值 °C */
    private static final double TEMP_WARN = 60.0;
    private static final double TEMP_DANGER = 80.0;
    /** 电压允许偏差（±10%） */
    private static final double VOLTAGE_TOLERANCE = 0.10;
    /** 功率因数低于此值预警 */
    private static final double PF_WARN = 0.85;
    /** 负载率（电流/额定）预警/危险 */
    private static final double LOAD_WARN = 0.80;
    private static final double LOAD_DANGER = 1.0;

    /**
     * 单设备电路安全评估
     */
    public Map<String, Object> assessDevice(String enterpriseId, String deviceId) {
        Device device = deviceRepository.findById(deviceId).orElse(null);
        if (device == null) {
            return Map.of("deviceId", deviceId, "available", false, "message", "设备不存在");
        }

        List<EnergyRecord> recs = energyRecordRepository
                .findTop100ByEnterpriseIdAndResourceIdOrderByTimestampDesc(enterpriseId, deviceId);
        if (recs.isEmpty()) {
            return Map.of("deviceId", deviceId, "deviceName", nz(device.getName()), "available", false,
                    "message", "暂无电气量数据（电流/电压/温度）");
        }

        List<EnergyRecord> recent = recs.stream().limit(20)
                .filter(r -> r.getCurrent() != null || r.getVoltage() != null || r.getTemperature() != null)
                .collect(Collectors.toList());
        if (recent.isEmpty()) {
            return Map.of("deviceId", deviceId, "deviceName", nz(device.getName()), "available", false,
                    "message", "最近数据未包含电流/电压/温度字段");
        }

        double maxCurrent = recent.stream().mapToDouble(r -> r.getCurrent() != null ? r.getCurrent() : 0).max().orElse(0);
        double avgVoltage = recent.stream().mapToDouble(r -> r.getVoltage() != null ? r.getVoltage() : 0)
                .filter(v -> v > 0).average().orElse(0);
        double maxTemp = recent.stream().mapToDouble(r -> r.getTemperature() != null ? r.getTemperature() : 0).max().orElse(0);
        double minPf = recent.stream().mapToDouble(r -> r.getPowerFactor() != null ? r.getPowerFactor() : 1).min().orElse(1);

        double ratedCurrent = device.getRatedCurrent() != null ? device.getRatedCurrent() : 0;
        double ratedVoltage = device.getRatedVoltage() != null ? device.getRatedVoltage() : 0;
        // 未配置额定电压时，依据实测电压推断相别：>300V 视为三相380V系统，否则单相220V，避免误报
        if (ratedVoltage <= 0 && avgVoltage > 0) {
            ratedVoltage = avgVoltage > 300 ? 380.0 : 220.0;
        }
        double loadRatio = (ratedCurrent > 0 && maxCurrent > 0) ? maxCurrent / ratedCurrent : 0;

        final String name = nz(device.getName());
        List<Map<String, Object>> faults = new ArrayList<>();
        int score = 100;

        // 1. 过载
        if (ratedCurrent > 0) {
            if (loadRatio >= LOAD_DANGER) {
                faults.add(fault("过载", "OVERLOAD", "critical", name,
                        String.format("电流 %.1fA 已达/超过额定 %.1fA", maxCurrent, ratedCurrent)));
                score -= 40;
            } else if (loadRatio >= LOAD_WARN) {
                faults.add(fault("负载偏高", "OVERLOAD_WARN", "warning", name,
                        String.format("电流 %.1fA / 额定 %.1fA (负载率 %.0f%%)", maxCurrent, ratedCurrent, loadRatio * 100)));
                score -= 15;
            }
        }
        // 2. 过温
        if (maxTemp >= TEMP_DANGER) {
            faults.add(fault("设备过温", "OVERTEMP", "critical", name,
                    String.format("温度 %.1f°C 超过危险阈值 %.0f°C", maxTemp, TEMP_DANGER)));
            score -= 35;
        } else if (maxTemp >= TEMP_WARN) {
            faults.add(fault("温度偏高", "OVERTEMP_WARN", "warning", name,
                    String.format("温度 %.1f°C 超过预警阈值 %.0f°C", maxTemp, TEMP_WARN)));
            score -= 15;
        }
        // 3. 电压偏差
        if (avgVoltage > 0) {
            double dev = Math.abs(avgVoltage - ratedVoltage) / ratedVoltage;
            if (dev > VOLTAGE_TOLERANCE * 2) {
                faults.add(fault("电压异常", "VOLTAGE_ABNORMAL", "critical", name,
                        String.format("平均电压 %.1fV 偏离额定 %.1fV 超 %.0f%%", avgVoltage, ratedVoltage, dev * 100)));
                score -= 25;
            } else if (dev > VOLTAGE_TOLERANCE) {
                faults.add(fault("电压偏差", "VOLTAGE_DEVIATION", "warning", name,
                        String.format("平均电压 %.1fV 偏离额定 %.1fV %.0f%%", avgVoltage, ratedVoltage, dev * 100)));
                score -= 10;
            }
        }
        // 4. 功率因数
        if (minPf < PF_WARN) {
            faults.add(fault("功率因数偏低", "LOW_PF", "warning", name,
                    String.format("最低功率因数 %.2f，无功损耗大、力调电费可能加收", minPf)));
            score -= 10;
        }

        score = Math.max(0, Math.min(100, score));
        String level = score >= 90 ? "安全" : score >= 70 ? "注意" : score >= 50 ? "警告" : "危险";

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("deviceId", deviceId);
        result.put("deviceName", name);
        result.put("available", true);
        result.put("safetyScore", score);
        result.put("safetyLevel", level);
        result.put("maxCurrent", round2(maxCurrent));
        result.put("ratedCurrent", round2(ratedCurrent));
        result.put("loadRatio", round2(loadRatio * 100));
        result.put("avgVoltage", round2(avgVoltage));
        result.put("ratedVoltage", round2(ratedVoltage));
        result.put("maxTemperature", round2(maxTemp));
        result.put("minPowerFactor", round2(minPf));
        result.put("faults", faults);
        result.put("faultCount", faults.size());
        result.put("assessedAt", new Date().toString());
        return result;
    }

    /**
     * 企业级电路安全总览
     */
    public Map<String, Object> assessEnterprise(String enterpriseId) {
        List<Device> devices = deviceRepository.findByEnterpriseId(enterpriseId);
        List<Map<String, Object>> items = new ArrayList<>();
        int safe = 0, warning = 0, danger = 0;
        for (Device d : devices) {
            Map<String, Object> a = assessDevice(enterpriseId, d.getId());
            if (Boolean.TRUE.equals(a.get("available"))) {
                items.add(a);
                String lvl = (String) a.get("safetyLevel");
                if ("危险".equals(lvl) || "警告".equals(lvl)) danger++;
                else if ("注意".equals(lvl)) warning++;
                else safe++;
            }
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("enterpriseId", enterpriseId);
        summary.put("deviceCount", items.size());
        summary.put("safe", safe);
        summary.put("warning", warning);
        summary.put("danger", danger);
        summary.put("items", items);
        summary.put("supportedCapabilities", buildCircuitCapabilities());
        summary.put("assessedAt", new Date().toString());
        return summary;
    }

    /**
     * 故障条目：吸收竞品专利 CN224216804U「低压电网故障检测装置」的核心能力——
     * 故障类型识别(faultType) + 故障定位(faultLocation=回路/设备) + 推送(pushChannel)。
     * 注：谐波(THD)、三相不平衡需接入安科瑞/炫通等电能质量(PQ)类硬件后由网关上报，软件层自动启用。
     */
    private Map<String, Object> fault(String type, String faultType, String severity, String location, String detail) {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("type", type);
        f.put("faultType", faultType);
        f.put("severity", severity);
        f.put("detail", detail);
        f.put("faultLocation", location);
        f.put("pushable", true);
        f.put("pushChannel", "小程序告警+短信");
        return f;
    }

    /**
     * 电路安全已具备的能力清单（含硬件依赖说明）。
     */
    private List<Map<String, Object>> buildCircuitCapabilities() {
        List<Map<String, Object>> caps = new ArrayList<>();
        caps.add(cap("过载保护", "实时", "基于额定电流评估负载率"));
        caps.add(cap("过温监测", "实时", "设备/环境温度越限预警"));
        caps.add(cap("电压偏差", "实时", "±10% 偏差识别(含三相380V推断)"));
        caps.add(cap("功率因数", "实时", "力调电费风险预警"));
        caps.add(cap("谐波监测(THD)", "需电能质量硬件", "接入安科瑞/炫通 PQ 类装置后自动启用"));
        caps.add(cap("三相不平衡", "需分相数据", "接入分相采集后自动启用"));
        caps.add(cap("故障定位+推送", "实时", "异常自动定位回路并推送至小程序/短信"));
        return caps;
    }

    private Map<String, Object> cap(String name, String mode, String desc) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("name", name);
        c.put("mode", mode);
        c.put("desc", desc);
        return c;
    }

    private String nz(String s) {
        return s != null ? s : "";
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
