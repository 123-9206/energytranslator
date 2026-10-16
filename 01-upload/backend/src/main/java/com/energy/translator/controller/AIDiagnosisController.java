package com.energy.translator.controller;

import com.energy.translator.dto.ApiResponse;
import com.energy.translator.security.UserPrincipal;
import com.energy.translator.service.AIDiagnosisService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * AI能效诊断控制器
 * 提供设备级能耗AI诊断、优化建议、异常检测等功能
 */
@RestController
@RequestMapping("/api/diagnosis")
@RequiredArgsConstructor
public class AIDiagnosisController {

    private final AIDiagnosisService aiDiagnosisService;

    /**
     * 执行完整AI诊断
     * 返回：设备能效评估、峰谷分析、异常检测、优化建议、综合评分
     */
    @GetMapping("/run")
    public ResponseEntity<ApiResponse<Map<String, Object>>> runDiagnosis(
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(required = false) String deviceId,
            @AuthenticationPrincipal UserPrincipal principal) {

        Map<String, Object> result = aiDiagnosisService.diagnose(
                principal.getEnterpriseId(), deviceId, days);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * 快速诊断（7天数据，设备级）
     */
    @GetMapping("/quick")
    public ResponseEntity<ApiResponse<Map<String, Object>>> quickDiagnosis(
            @RequestParam(required = false) String deviceId,
            @AuthenticationPrincipal UserPrincipal principal) {

        Map<String, Object> result = aiDiagnosisService.diagnose(
                principal.getEnterpriseId(), deviceId, 7);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
