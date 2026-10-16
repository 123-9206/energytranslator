package com.energy.translator.controller;

import com.energy.translator.dto.ApiResponse;
import com.energy.translator.entity.EnergyRecord;
import com.energy.translator.security.UserPrincipal;
import com.energy.translator.service.AnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/analysis")
@RequiredArgsConstructor
public class AnalysisController {

    private final AnalysisService analysisService;

    /**
     * 获取能耗趋势分析
     */
    @GetMapping("/trend")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getTrend(
            @RequestParam(defaultValue = "day") String type,
            @RequestParam(defaultValue = "7") int days,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @AuthenticationPrincipal UserPrincipal principal) {

        Map<String, Object> trend = analysisService.getTrend(
                principal.getEnterpriseId(), type, days, startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success(trend));
    }

    /**
     * 获取能耗排名（按设备/按站点）
     */
    @GetMapping("/ranking")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getRanking(
            @RequestParam(defaultValue = "device") String dimension,
            @RequestParam(defaultValue = "month") String period,
            @RequestParam(required = false) String siteId,
            @AuthenticationPrincipal UserPrincipal principal) {

        Map<String, Object> ranking = analysisService.getRanking(
                principal.getEnterpriseId(), dimension, period, siteId);
        return ResponseEntity.ok(ApiResponse.success(ranking));
    }

    /**
     * 获取能耗统计摘要
     */
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSummary(
            @RequestParam(defaultValue = "month") String period,
            @RequestParam(required = false) String siteId,
            @AuthenticationPrincipal UserPrincipal principal) {

        Map<String, Object> summary = analysisService.getSummary(
                principal.getEnterpriseId(), period, siteId);
        return ResponseEntity.ok(ApiResponse.success(summary));
    }

    /**
     * 获取分析详情（按设备或站点明细）
     */
    @GetMapping("/detail")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getDetail(
            @RequestParam(required = false) String deviceId,
            @RequestParam(required = false) String siteId,
            @RequestParam(defaultValue = "day") String period,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal principal) {

        Map<String, Object> detail = analysisService.getDetail(
                principal.getEnterpriseId(), deviceId, siteId, period, page, size);
        return ResponseEntity.ok(ApiResponse.success(detail));
    }
}
