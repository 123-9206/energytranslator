package com.energy.translator.service;

import com.energy.translator.entity.Alarm;
import com.energy.translator.entity.AlarmRule;
import com.energy.translator.repository.AlarmRepository;
import com.energy.translator.repository.AlarmRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AlarmService {

    private final AlarmRepository alarmRepository;
    private final AlarmRuleRepository alarmRuleRepository;

    /**
     * 获取告警列表
     */
    public Map<String, Object> getAlarms(String enterpriseId, String status, String level,
                                          int page, int size) {
        PageRequest pageRequest = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "occurredAt"));

        Page<Alarm> alarmPage;
        if (status != null && level != null) {
            alarmPage = alarmRepository.findByEnterpriseIdAndStatusAndLevel(enterpriseId, status, level, pageRequest);
        } else if (status != null) {
            alarmPage = alarmRepository.findByEnterpriseIdAndStatus(enterpriseId, status, pageRequest);
        } else if (level != null) {
            alarmPage = alarmRepository.findByEnterpriseIdAndLevel(enterpriseId, level, pageRequest);
        } else {
            alarmPage = alarmRepository.findByEnterpriseId(enterpriseId, pageRequest);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("list", alarmPage.getContent());
        result.put("total", alarmPage.getTotalElements());
        result.put("page", page);
        result.put("size", size);
        result.put("totalPages", alarmPage.getTotalPages());
        return result;
    }

    /**
     * 获取告警详情
     */
    public Alarm getAlarm(String id, String enterpriseId) {
        return alarmRepository.findById(id)
                .filter(a -> enterpriseId.equals(a.getEnterpriseId()))
                .orElseThrow(() -> new RuntimeException("告警不存在"));
    }

    /**
     * 确认告警
     */
    public Alarm acknowledge(String id, String enterpriseId, String userId) {
        Alarm alarm = getAlarm(id, enterpriseId);
        alarm.setStatus("acknowledged");
        alarm.setAcknowledgedAt(Instant.now());
        alarm.setAcknowledgedBy(userId);
        return alarmRepository.save(alarm);
    }

    /**
     * 解决告警
     */
    public Alarm resolve(String id, String enterpriseId, String userId, String solution) {
        Alarm alarm = getAlarm(id, enterpriseId);
        alarm.setStatus("resolved");
        alarm.setResolvedAt(Instant.now());
        alarm.setResolvedBy(userId);
        alarm.setSolution(solution);
        return alarmRepository.save(alarm);
    }

    /**
     * 忽略告警
     */
    public Alarm ignore(String id, String enterpriseId) {
        Alarm alarm = getAlarm(id, enterpriseId);
        alarm.setStatus("ignored");
        return alarmRepository.save(alarm);
    }

    /**
     * 获取告警统计
     * 使用数据库 count 聚合，避免 OOM（不再全量加载告警列表）
     */
    public Map<String, Object> getStatistics(String enterpriseId) {
        long total = alarmRepository.countByEnterpriseId(enterpriseId);
        long active = alarmRepository.countByEnterpriseIdAndStatus(enterpriseId, "active");
        long acknowledged = alarmRepository.countByEnterpriseIdAndStatus(enterpriseId, "acknowledged");
        long resolved = alarmRepository.countByEnterpriseIdAndStatus(enterpriseId, "resolved");
        long critical = alarmRepository.countByEnterpriseIdAndLevel(enterpriseId, "critical");
        long warning = alarmRepository.countByEnterpriseIdAndLevel(enterpriseId, "warning");
        long info = alarmRepository.countByEnterpriseIdAndLevel(enterpriseId, "info");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("active", active);
        result.put("acknowledged", acknowledged);
        result.put("resolved", resolved);
        result.put("critical", critical);
        result.put("warning", warning);
        result.put("info", info);

        return result;
    }

    /**
     * 批量确认告警
     */
    public Map<String, Object> batchAcknowledge(List<String> ids, String enterpriseId, String userId) {
        int success = 0;
        int failed = 0;
        for (String id : ids) {
            try {
                acknowledge(id, enterpriseId, userId);
                success++;
            } catch (Exception e) {
                failed++;
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", success);
        result.put("failed", failed);
        result.put("message", String.format("成功确认 %d 条，失败 %d 条", success, failed));
        return result;
    }

    // ==================== 告警规则管理 ====================

    /**
     * 获取告警规则列表
     */
    public Map<String, Object> getAlarmRules(String enterpriseId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AlarmRule> rulePage = alarmRuleRepository.findByEnterpriseId(enterpriseId, pageRequest);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("list", rulePage.getContent());
        result.put("total", rulePage.getTotalElements());
        result.put("page", page);
        result.put("size", size);
        return result;
    }

    /**
     * 获取所有启用的告警规则（用于规则引擎检查）
     */
    public List<AlarmRule> getEnabledRules(String enterpriseId) {
        return alarmRuleRepository.findByEnterpriseIdAndEnabled(enterpriseId, true);
    }

    /**
     * 创建告警规则
     */
    public AlarmRule createAlarmRule(AlarmRule rule) {
        if (rule.getEnabled() == null) {
            rule.setEnabled(true);
        }
        if (rule.getCooldownSeconds() == null) {
            rule.setCooldownSeconds(300); // 默认5分钟冷却
        }
        if (rule.getDurationSeconds() == null) {
            rule.setDurationSeconds(0); // 默认即时触发
        }
        return alarmRuleRepository.save(rule);
    }

    /**
     * 更新告警规则
     */
    public AlarmRule updateAlarmRule(String id, Map<String, Object> updates, String enterpriseId) {
        AlarmRule rule = alarmRuleRepository.findById(id)
                .filter(r -> enterpriseId.equals(r.getEnterpriseId()))
                .orElseThrow(() -> new RuntimeException("告警规则不存在"));

        if (updates.containsKey("name")) rule.setName((String) updates.get("name"));
        if (updates.containsKey("deviceName")) rule.setDeviceName((String) updates.get("deviceName"));
        if (updates.containsKey("deviceId")) rule.setDeviceId((String) updates.get("deviceId"));
        if (updates.containsKey("metric")) rule.setMetric((String) updates.get("metric"));
        if (updates.containsKey("condition")) rule.setCondition((String) updates.get("condition"));
        if (updates.containsKey("threshold")) rule.setThreshold(((Number) updates.get("threshold")).doubleValue());
        if (updates.containsKey("durationSeconds")) rule.setDurationSeconds(((Number) updates.get("durationSeconds")).intValue());
        if (updates.containsKey("severity")) rule.setSeverity((String) updates.get("severity"));
        if (updates.containsKey("enabled")) rule.setEnabled((Boolean) updates.get("enabled"));
        if (updates.containsKey("notifyMethod")) rule.setNotifyMethod((String) updates.get("notifyMethod"));
        if (updates.containsKey("cooldownSeconds")) rule.setCooldownSeconds(((Number) updates.get("cooldownSeconds")).intValue());
        if (updates.containsKey("description")) rule.setDescription((String) updates.get("description"));

        return alarmRuleRepository.save(rule);
    }

    /**
     * 删除告警规则
     */
    public void deleteAlarmRule(String id, String enterpriseId) {
        alarmRuleRepository.findById(id)
                .filter(r -> enterpriseId.equals(r.getEnterpriseId()))
                .orElseThrow(() -> new RuntimeException("告警规则不存在"));
        alarmRuleRepository.deleteById(id);
    }

    /**
     * 启用/禁用告警规则
     */
    public AlarmRule toggleAlarmRule(String id, String enterpriseId) {
        AlarmRule rule = alarmRuleRepository.findById(id)
                .filter(r -> enterpriseId.equals(r.getEnterpriseId()))
                .orElseThrow(() -> new RuntimeException("告警规则不存在"));
        rule.setEnabled(!rule.getEnabled());
        return alarmRuleRepository.save(rule);
    }
}
