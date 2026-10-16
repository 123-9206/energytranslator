package com.energy.translator.controller;

import com.energy.translator.dto.ApiResponse;
import com.energy.translator.dto.CarbonReportDTO;
import com.energy.translator.security.UserPrincipal;
import com.energy.translator.service.CarbonService;
import com.energy.translator.service.QuotaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/carbon")
@RequiredArgsConstructor
public class CarbonController {

    private final CarbonService carbonService;
    private final QuotaService quotaService;

    /**
     * 获取碳排放报告
     */
    @GetMapping("/report")
    public ResponseEntity<ApiResponse<CarbonReportDTO>> getCarbonReport(
            @RequestParam(defaultValue = "month") String period,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @AuthenticationPrincipal UserPrincipal principal) {

        CarbonReportDTO report = carbonService.getCarbonReport(
                principal.getEnterpriseId(), period, startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success(report));
    }

    /**
     * 获取碳排放趋势
     */
    @GetMapping("/trend")
    public ResponseEntity<ApiResponse<List<CarbonReportDTO.CarbonTrend>>> getCarbonTrend(
            @RequestParam(defaultValue = "month") String period,
            @AuthenticationPrincipal UserPrincipal principal) {

        List<CarbonReportDTO.CarbonTrend> trend = carbonService.getCarbonTrend(
                principal.getEnterpriseId(), period);
        return ResponseEntity.ok(ApiResponse.success(trend));
    }

    /**
     * 获取碳排放摘要统计
     */
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCarbonSummary(
            @AuthenticationPrincipal UserPrincipal principal) {

        Map<String, Object> summary = carbonService.getCarbonSummary(principal.getEnterpriseId());
        return ResponseEntity.ok(ApiResponse.success(summary));
    }

    /**
     * 碳资产盈余核算（配额+已购碳汇 vs 实际排放）
     * 前端 api-http.js.getCarbonAsset() -> GET /api/carbon/asset
     */
    @GetMapping("/asset")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCarbonAsset(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(quotaService.getCarbonAsset(principal.getEnterpriseId())));
    }
}
