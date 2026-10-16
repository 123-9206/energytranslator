package com.energy.translator.controller;

import com.energy.translator.dto.ApiResponse;
import com.energy.translator.security.UserPrincipal;
import com.energy.translator.service.CircuitSafetyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/circuit-safety")
@RequiredArgsConstructor
public class CircuitSafetyController {

    private final CircuitSafetyService circuitSafetyService;

    /**
     * 企业电路安全总览（按设备评估 + 汇总安全/警告/危险数量）
     * 前端 api-http.js.getCircuitSafetyOverview() -> GET /api/circuit-safety/overview
     */
    @GetMapping("/overview")
    public ResponseEntity<ApiResponse<Map<String, Object>>> overview(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(
                circuitSafetyService.assessEnterprise(principal.getEnterpriseId())));
    }

    /**
     * 单设备电路安全评估（过载/过温/电压偏差/功率因数）
     * 前端 api-http.js.getCircuitSafetyDevice(id) -> GET /api/circuit-safety/device/{deviceId}
     */
    @GetMapping("/device/{deviceId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> device(
            @PathVariable String deviceId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(
                circuitSafetyService.assessDevice(principal.getEnterpriseId(), deviceId)));
    }
}
