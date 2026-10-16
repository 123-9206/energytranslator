package com.energy.translator.controller;

import com.energy.translator.dto.ApiResponse;
import com.energy.translator.entity.BillCheckReport;
import com.energy.translator.service.BillCheckService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

/**
 * 电费体检客户端入口（免登录，/api/public/** 已在 SecurityConfig permitAll）。
 * 交付策略：异步——提交后转人工复核，客户凭手机号+单号查询结果。
 */
@RestController
@RequestMapping("/api/public/bill-check")
@RequiredArgsConstructor
public class PublicBillCheckController {

    private final BillCheckService billCheckService;

    @PostMapping("/upload")
    public ApiResponse<Map<String, Object>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "contactName", required = false) String contactName,
            @RequestParam("contactPhone") String contactPhone,
            @RequestParam(value = "factoryName", required = false) String factoryName) {
        try {
            BillCheckReport report = billCheckService.submit(file, contactName, contactPhone, factoryName);
            Map<String, Object> data = new HashMap<>();
            data.put("id", report.getId());
            data.put("status", report.getStatus().name());
            data.put("message", "体检单已收到，复核完成后凭本单号和手机号查询报告");
            return ApiResponse.success(data);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error("提交失败，请稍后重试");
        }
    }

    /** 客户查询：手机号 + 单号。仅在 APPROVED/DELIVERED 后返回报告正文。 */
    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> query(@PathVariable String id,
                                                  @RequestParam("phone") String phone) {
        try {
            BillCheckReport report = billCheckService.queryForCustomer(id, phone);
            Map<String, Object> data = new HashMap<>();
            data.put("id", report.getId());
            data.put("status", report.getStatus().name());
            if (report.getStatus() == BillCheckReport.Status.APPROVED
                    || report.getStatus() == BillCheckReport.Status.DELIVERED) {
                data.put("reportText", report.getReportText());
            } else {
                data.put("message", "体检进行中，人工复核完成后可查看报告（一般1个工作日内）");
            }
            return ApiResponse.success(data);
        } catch (SecurityException e) {
            return ApiResponse.error("手机号与体检单不匹配");
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(e.getMessage());
        }
    }
}
