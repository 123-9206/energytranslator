package com.energy.translator.controller;

import com.energy.translator.dto.ApiResponse;
import com.energy.translator.dto.PageResponse;
import com.energy.translator.entity.EnergyRecord;
import com.energy.translator.security.UserPrincipal;
import com.energy.translator.service.EnergyRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/energy")
@RequiredArgsConstructor
public class EnergyRecordController {

    private final EnergyRecordService energyRecordService;

    @PostMapping("/records")
    public ResponseEntity<ApiResponse<Map<String, String>>> addRecord(
            @RequestBody EnergyRecord record,
            @AuthenticationPrincipal UserPrincipal principal) {
        
        record.setEnterpriseId(principal.getEnterpriseId());
        EnergyRecord saved = energyRecordService.addRecord(record);
        return ResponseEntity.ok(ApiResponse.success(Map.of("recordId", saved.getId())));
    }

    @GetMapping("/records")
    public ResponseEntity<ApiResponse<PageResponse<EnergyRecord>>> getRecords(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @AuthenticationPrincipal UserPrincipal principal) {
        
        PageResponse<EnergyRecord> response = energyRecordService.getRecords(
                principal.getEnterpriseId(), startDate, endDate, page, pageSize);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/records/today")
    public ResponseEntity<ApiResponse<Object>> getTodayRecords(
            @AuthenticationPrincipal UserPrincipal principal) {
        
        var records = energyRecordService.getTodayRecords(principal.getEnterpriseId());
        double total = records.stream().mapToDouble(EnergyRecord::getValue).sum();
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "records", records,
                "totalUsage", total
        )));
    }
}
