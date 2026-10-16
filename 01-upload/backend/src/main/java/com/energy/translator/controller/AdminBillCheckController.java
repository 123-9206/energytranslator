package com.energy.translator.controller;

import com.energy.translator.dto.ApiResponse;
import com.energy.translator.dto.BillData;
import com.energy.translator.entity.BillCheckReport;
import com.energy.translator.service.BillCheckService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 电费体检 admin 复核台。前100单全部人工复核（质量红线）。
 */
@RestController
@RequestMapping("/api/admin/bill-check")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminBillCheckController {

    private final BillCheckService billCheckService;

    @GetMapping
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        BillCheckReport.Status st = null;
        if (status != null && !status.isBlank()) {
            st = BillCheckReport.Status.valueOf(status);
        }
        Page<BillCheckReport> p = billCheckService.list(st, page, size);
        Map<String, Object> data = new HashMap<>();
        data.put("list", p.getContent());
        data.put("total", p.getTotalElements());
        return ApiResponse.success(data);
    }

    /** 漏斗看板：上传 → 读单成功 → 复核通过 → 已交付 */
    @GetMapping("/funnel")
    public ApiResponse<Map<String, Object>> funnel() {
        return ApiResponse.success(billCheckService.funnel());
    }

    @GetMapping("/{id}")
    public ApiResponse<BillCheckReport> detail(@PathVariable String id) {
        return ApiResponse.success(billCheckService.get(id));
    }

    /** 复核台查看电费单原图 */
    @GetMapping("/{id}/image")
    public org.springframework.http.ResponseEntity<org.springframework.core.io.Resource> image(
            @PathVariable String id) throws java.io.IOException {
        BillCheckReport report = billCheckService.get(id);
        if (report.getImagePath() == null) {
            return org.springframework.http.ResponseEntity.notFound().build();
        }
        java.nio.file.Path path = java.nio.file.Paths.get(report.getImagePath());
        if (!java.nio.file.Files.exists(path)) {
            return org.springframework.http.ResponseEntity.notFound().build();
        }
        String name = path.getFileName().toString().toLowerCase();
        org.springframework.http.MediaType type = name.endsWith(".png")
                ? org.springframework.http.MediaType.IMAGE_PNG
                : org.springframework.http.MediaType.IMAGE_JPEG;
        return org.springframework.http.ResponseEntity.ok()
                .contentType(type)
                .body(new org.springframework.core.io.FileSystemResource(path));
    }

    /** 人工录入/修正账单字段后重跑校验+分析（NEEDS_MANUAL_READ 的兜底入口） */
    @PostMapping("/{id}/rerun")
    public ApiResponse<BillCheckReport> rerun(@PathVariable String id, @RequestBody BillData data) {
        return ApiResponse.success(billCheckService.rerunWithData(id, data));
    }

    /** 复核通过。finalReportText 允许复核人微调报告措辞，但数字只能来自系统计算。 */
    @PostMapping("/{id}/approve")
    public ApiResponse<BillCheckReport> approve(@PathVariable String id,
                                                @RequestBody(required = false) Map<String, String> body,
                                                Authentication auth) {
        String note = body == null ? null : body.get("note");
        String finalText = body == null ? null : body.get("finalReportText");
        return ApiResponse.success(billCheckService.approve(id, auth.getName(), note, finalText));
    }

    @PostMapping("/{id}/deliver")
    public ApiResponse<BillCheckReport> deliver(@PathVariable String id) {
        try {
            return ApiResponse.success(billCheckService.deliver(id));
        } catch (IllegalStateException e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @PostMapping("/{id}/reject")
    public ApiResponse<BillCheckReport> reject(@PathVariable String id,
                                               @RequestBody(required = false) Map<String, String> body,
                                               Authentication auth) {
        String note = body == null ? null : body.get("note");
        return ApiResponse.success(billCheckService.reject(id, auth.getName(), note));
    }
}
