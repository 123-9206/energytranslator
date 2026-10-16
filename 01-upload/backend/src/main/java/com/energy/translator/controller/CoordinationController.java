package com.energy.translator.controller;

import com.energy.translator.dto.ApiResponse;
import com.energy.translator.security.UserPrincipal;
import com.energy.translator.service.CoordinationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 分布式协同调度控制器（多企业可调负荷聚合优化器）
 * 吸收竞品专利 CN120767796B 分布式协同思想，能耗翻译官独立实现。
 */
@RestController
@RequestMapping("/api/coordination")
@RequiredArgsConstructor
public class CoordinationController {

    private final CoordinationService coordinationService;

    /**
     * 执行一次区域需求响应协同调度。
     * body: { "targetReductionKw": 500, "windowMinutes": 60 }
     * 返回区域可调容量、分配率、缺口及每个节点的本地决策。
     */
    @PostMapping("/aggregate")
    public ResponseEntity<ApiResponse<Map<String, Object>>> aggregate(
            @RequestBody(required = false) Map<String, Object> body,
            @AuthenticationPrincipal UserPrincipal principal) {

        double target = body != null && body.get("targetReductionKw") != null
                ? ((Number) body.get("targetReductionKw")).doubleValue() : 500.0;
        int window = body != null && body.get("windowMinutes") != null
                ? ((Number) body.get("windowMinutes")).intValue() : 60;

        Map<String, Object> result = coordinationService.aggregate(target, window, principal.getEnterpriseId());
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
