package com.energy.translator.controller;

import com.energy.translator.dto.ApiResponse;
import com.energy.translator.entity.Alarm;
import com.energy.translator.entity.AlarmRule;
import com.energy.translator.security.UserPrincipal;
import com.energy.translator.service.AlarmService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/alarms")
@RequiredArgsConstructor
public class AlarmController {

    private final AlarmService alarmService;

    /**
     * 获取告警列表
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAlarms(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String level,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal principal) {

        Map<String, Object> result = alarmService.getAlarms(
                principal.getEnterpriseId(), status, level, page, size);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * 获取告警详情
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Alarm>> getAlarm(
            @PathVariable String id,
            @AuthenticationPrincipal UserPrincipal principal) {

        Alarm alarm = alarmService.getAlarm(id, principal.getEnterpriseId());
        return ResponseEntity.ok(ApiResponse.success(alarm));
    }

    /**
     * 确认告警
     */
    @PutMapping("/{id}/acknowledge")
    public ResponseEntity<ApiResponse<Alarm>> acknowledgeAlarm(
            @PathVariable String id,
            @AuthenticationPrincipal UserPrincipal principal) {

        Alarm alarm = alarmService.acknowledge(id, principal.getEnterpriseId(), principal.getUserId());
        return ResponseEntity.ok(ApiResponse.success(alarm));
    }

    /**
     * 解决告警
     */
    @PutMapping("/{id}/resolve")
    public ResponseEntity<ApiResponse<Alarm>> resolveAlarm(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, String> body,
            @AuthenticationPrincipal UserPrincipal principal) {

        String solution = body != null ? body.get("solution") : null;
        Alarm alarm = alarmService.resolve(id, principal.getEnterpriseId(), principal.getUserId(), solution);
        return ResponseEntity.ok(ApiResponse.success(alarm));
    }

    /**
     * 忽略告警
     */
    @PutMapping("/{id}/ignore")
    public ResponseEntity<ApiResponse<Alarm>> ignoreAlarm(
            @PathVariable String id,
            @AuthenticationPrincipal UserPrincipal principal) {

        Alarm alarm = alarmService.ignore(id, principal.getEnterpriseId());
        return ResponseEntity.ok(ApiResponse.success(alarm));
    }

    /**
     * 获取告警统计
     */
    @GetMapping("/statistics")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStatistics(
            @AuthenticationPrincipal UserPrincipal principal) {

        Map<String, Object> stats = alarmService.getStatistics(principal.getEnterpriseId());
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    /**
     * 批量确认告警
     */
    @PutMapping("/batch/acknowledge")
    public ResponseEntity<ApiResponse<Map<String, Object>>> batchAcknowledge(
            @RequestBody Map<String, List<String>> body,
            @AuthenticationPrincipal UserPrincipal principal) {

        List<String> ids = body.get("ids");
        Map<String, Object> result = alarmService.batchAcknowledge(
                ids, principal.getEnterpriseId(), principal.getUserId());
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // ==================== 告警规则管理 ====================

    /**
     * 获取告警规则列表
     */
    @GetMapping("/rules")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAlarmRules(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal principal) {

        Map<String, Object> result = alarmService.getAlarmRules(
                principal.getEnterpriseId(), page, size);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * 创建告警规则
     */
    @PostMapping("/rules")
    public ResponseEntity<ApiResponse<AlarmRule>> createAlarmRule(
            @RequestBody AlarmRule rule,
            @AuthenticationPrincipal UserPrincipal principal) {

        rule.setEnterpriseId(principal.getEnterpriseId());
        AlarmRule created = alarmService.createAlarmRule(rule);
        return ResponseEntity.ok(ApiResponse.success(created));
    }

    /**
     * 更新告警规则
     */
    @PutMapping("/rules/{id}")
    public ResponseEntity<ApiResponse<AlarmRule>> updateAlarmRule(
            @PathVariable String id,
            @RequestBody Map<String, Object> updates,
            @AuthenticationPrincipal UserPrincipal principal) {

        AlarmRule updated = alarmService.updateAlarmRule(id, updates, principal.getEnterpriseId());
        return ResponseEntity.ok(ApiResponse.success(updated));
    }

    /**
     * 删除告警规则
     */
    @DeleteMapping("/rules/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteAlarmRule(
            @PathVariable String id,
            @AuthenticationPrincipal UserPrincipal principal) {

        alarmService.deleteAlarmRule(id, principal.getEnterpriseId());
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /**
     * 启用/禁用告警规则
     */
    @PutMapping("/rules/{id}/toggle")
    public ResponseEntity<ApiResponse<AlarmRule>> toggleAlarmRule(
            @PathVariable String id,
            @AuthenticationPrincipal UserPrincipal principal) {

        AlarmRule rule = alarmService.toggleAlarmRule(id, principal.getEnterpriseId());
        return ResponseEntity.ok(ApiResponse.success(rule));
    }
}
