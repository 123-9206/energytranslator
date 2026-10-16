package com.energy.translator.controller;

import com.energy.translator.dto.ApiResponse;
import com.energy.translator.dto.CarbonReportDTO;
import com.energy.translator.dto.DashboardDTO;
import com.energy.translator.security.UserPrincipal;
import com.energy.translator.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    public ResponseEntity<ApiResponse<DashboardDTO>> getDashboard(
            @RequestParam(defaultValue = "today") String period,
            @AuthenticationPrincipal UserPrincipal principal) {
        
        DashboardDTO dashboard = dashboardService.getDashboard(principal.getEnterpriseId(), period);
        return ResponseEntity.ok(ApiResponse.success(dashboard));
    }

    @GetMapping("/carbon")
    public ResponseEntity<ApiResponse<CarbonReportDTO>> getCarbonReport(
            @RequestParam(defaultValue = "month") String period,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @AuthenticationPrincipal UserPrincipal principal) {
        
        CarbonReportDTO report = dashboardService.getCarbonReport(
                principal.getEnterpriseId(), period, startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success(report));
    }
}
