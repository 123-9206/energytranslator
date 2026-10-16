package com.energy.translator.controller;

import com.energy.translator.dto.ApiResponse;
import com.energy.translator.entity.Quota;
import com.energy.translator.security.UserPrincipal;
import com.energy.translator.service.QuotaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/carbon/quota")
@RequiredArgsConstructor
public class QuotaController {

    private final QuotaService quotaService;

    /**
     * 当前年度碳配额
     * 前端 api-http.js.getCurrentQuota() -> GET /api/carbon/quota/current
     */
    @GetMapping("/current")
    public ResponseEntity<ApiResponse<Quota>> getCurrentQuota(
            @AuthenticationPrincipal UserPrincipal principal) {
        Quota quota = quotaService.getCurrentQuota(principal.getEnterpriseId());
        return ResponseEntity.ok(ApiResponse.success(quota));
    }

    /**
     * 配额历史
     * 前端 api-http.js.getQuotaHistory() -> GET /api/carbon/quota/history
     */
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<Quota>>> getQuotaHistory(
            @AuthenticationPrincipal UserPrincipal principal) {
        List<Quota> history = quotaService.getQuotaHistory(principal.getEnterpriseId());
        return ResponseEntity.ok(ApiResponse.success(history));
    }
}
