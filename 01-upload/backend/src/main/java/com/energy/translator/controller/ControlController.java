package com.energy.translator.controller;

import com.energy.translator.dto.ApiResponse;
import com.energy.translator.entity.ControlRule;
import com.energy.translator.security.UserPrincipal;
import com.energy.translator.service.ControlService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/control")
@RequiredArgsConstructor
public class ControlController {

    private final ControlService controlService;

    /**
     * 下发设备控制指令
     */
    @PostMapping("/device/{deviceId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> controlDevice(
            @PathVariable String deviceId,
            @RequestBody Map<String, Object> command,
            @AuthenticationPrincipal UserPrincipal principal) {

        Map<String, Object> result = controlService.sendControlCommand(
                deviceId, command, principal.getEnterpriseId(), principal.getUserId());
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * 批量下发控制指令
     */
    @PostMapping("/batch")
    public ResponseEntity<ApiResponse<Map<String, Object>>> batchControl(
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal UserPrincipal principal) {

        @SuppressWarnings("unchecked")
        List<String> deviceIds = (List<String>) body.get("deviceIds");
        Map<String, Object> command = (Map<String, Object>) body.get("command");

        Map<String, Object> result = controlService.batchControl(
                deviceIds, command, principal.getEnterpriseId(), principal.getUserId());
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * 获取控制统计
     */
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getControlStats(
            @AuthenticationPrincipal UserPrincipal principal) {

        Map<String, Object> stats = controlService.getControlStats(principal.getEnterpriseId());
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    /**
     * 管理员获取全部控制规则
     */
    @GetMapping("/admin/rules")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAllControlRules() {

        Map<String, Object> result = controlService.getAllRules();
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * 手动触发控制规则
     */
    @PostMapping("/rules/{id}/trigger")
    public ResponseEntity<ApiResponse<Map<String, Object>>> triggerRule(
            @PathVariable String id,
            @AuthenticationPrincipal UserPrincipal principal) {

        Map<String, Object> result = controlService.triggerRule(
                id, principal.getEnterpriseId(), principal.getUserId());
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * 获取控制日志
     */
    @GetMapping("/logs")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getControlLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String deviceId,
            @AuthenticationPrincipal UserPrincipal principal) {

        Map<String, Object> logs = controlService.getControlLogs(
                principal.getEnterpriseId(), deviceId, page, size);
        return ResponseEntity.ok(ApiResponse.success(logs));
    }

    /**
     * 获取控制规则列表
     */
    @GetMapping("/rules")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getRules(
            @AuthenticationPrincipal UserPrincipal principal) {

        Map<String, Object> result = controlService.getRules(principal.getEnterpriseId());
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * 创建控制规则
     */
    @PostMapping("/rules")
    public ResponseEntity<ApiResponse<ControlRule>> createRule(
            @RequestBody ControlRule rule,
            @AuthenticationPrincipal UserPrincipal principal) {

        rule.setEnterpriseId(principal.getEnterpriseId());
        ControlRule created = controlService.createRule(rule);
        return ResponseEntity.ok(ApiResponse.success(created));
    }

    /**
     * 更新控制规则
     */
    @PutMapping("/rules/{id}")
    public ResponseEntity<ApiResponse<ControlRule>> updateRule(
            @PathVariable String id,
            @RequestBody Map<String, Object> updates,
            @AuthenticationPrincipal UserPrincipal principal) {

        ControlRule updated = controlService.updateRule(
                id, updates, principal.getEnterpriseId());
        return ResponseEntity.ok(ApiResponse.success(updated));
    }

    /**
     * 删除控制规则
     */
    @DeleteMapping("/rules/{id}")
    public ResponseEntity<ApiResponse<Map<String, String>>> deleteRule(
            @PathVariable String id,
            @AuthenticationPrincipal UserPrincipal principal) {

        controlService.deleteRule(id, principal.getEnterpriseId());
        return ResponseEntity.ok(ApiResponse.success(Map.of("message", "规则已删除")));
    }

    /**
     * 启用/禁用控制规则
     */
    @PutMapping("/rules/{id}/toggle")
    public ResponseEntity<ApiResponse<ControlRule>> toggleRule(
            @PathVariable String id,
            @AuthenticationPrincipal UserPrincipal principal) {

        ControlRule rule = controlService.toggleRule(id, principal.getEnterpriseId());
        return ResponseEntity.ok(ApiResponse.success(rule));
    }
}
