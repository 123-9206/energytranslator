package com.energy.translator.controller;

import com.energy.translator.dto.ApiResponse;
import com.energy.translator.dto.PageResponse;
import com.energy.translator.entity.VppSettlement;
import com.energy.translator.security.UserPrincipal;
import com.energy.translator.service.VppSettlementService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * VPP 结算管理（VPP增强）
 * 企业：查看我的结算单、请求生成结算
 * 平台：结算单管理、审批、付款
 */
@RestController
@RequestMapping("/api/vpp/settlements")
@RequiredArgsConstructor
public class VppSettlementController {

    private final VppSettlementService settlementService;

    /**
     * 企业侧：我的结算单列表
     */
    @GetMapping("/my")
    public ResponseEntity<ApiResponse<PageResponse<VppSettlement>>> getMySettlements(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @AuthenticationPrincipal UserPrincipal principal) {

        Page<VppSettlement> result = settlementService.getMySettlements(principal.getEnterpriseId(), page, pageSize);
        return ResponseEntity.ok(ApiResponse.success(PageResponse.of(
                result.getContent(), result.getTotalElements(), page, pageSize)));
    }

    /**
     * 平台侧：全部结算单（按状态过滤）
     */
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<VppSettlement>>> getAllSettlements(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @AuthenticationPrincipal UserPrincipal principal) {

        if (!"admin".equals(principal.getRole())) {
            return ResponseEntity.badRequest().body(ApiResponse.error("只有管理员可以查看全部结算单"));
        }
        Page<VppSettlement> result = settlementService.getAllSettlements(status, page, pageSize);
        return ResponseEntity.ok(ApiResponse.success(PageResponse.of(
                result.getContent(), result.getTotalElements(), page, pageSize)));
    }

    /**
     * 查询结算单详情（企业看本企业，管理员看任意）
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<VppSettlement>> getSettlement(
            @PathVariable String id,
            @AuthenticationPrincipal UserPrincipal principal) {

        boolean isAdmin = "admin".equals(principal.getRole());
        VppSettlement settlement = settlementService.getSettlementDetail(
                id, principal.getEnterpriseId(), isAdmin);
        return ResponseEntity.ok(ApiResponse.success(settlement));
    }

    /**
     * 企业发起提现（paid -> withdrawn）
     */
    @PostMapping("/{id}/withdraw")
    public ResponseEntity<ApiResponse<Void>> withdrawSettlement(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, String> body,
            @AuthenticationPrincipal UserPrincipal principal) {

        if ("admin".equals(principal.getRole())) {
            return ResponseEntity.badRequest().body(ApiResponse.error("管理员无需提现"));
        }
        String account = body != null ? body.get("account") : null;
        try {
            settlementService.withdrawSettlement(id, principal.getEnterpriseId(), account);
            return ResponseEntity.ok(ApiResponse.success(null, "提现申请已提交"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * 生成/刷新结算单（企业请求生成自己的月度结算）
     */
    @PostMapping("/generate")
    public ResponseEntity<ApiResponse<Map<String, String>>> generateSettlement(
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserPrincipal principal) {

        String period = body.get("period");
        if (period == null || !period.matches("\\d{4}-\\d{2}")) {
            return ResponseEntity.badRequest().body(ApiResponse.error("结算周期格式应为 yyyy-MM"));
        }
        try {
            String id = settlementService.generateSettlement(principal.getEnterpriseId(), period);
            return ResponseEntity.ok(ApiResponse.success(Map.of("settlementId", id), "结算单已生成"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * 审批结算单（管理员）
     */
    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<Void>> approveSettlement(
            @PathVariable String id,
            @AuthenticationPrincipal UserPrincipal principal) {

        if (!"admin".equals(principal.getRole())) {
            return ResponseEntity.badRequest().body(ApiResponse.error("只有管理员可以审批结算单"));
        }
        try {
            settlementService.approveSettlement(id, principal.getUserId());
            return ResponseEntity.ok(ApiResponse.success(null, "结算单已审批"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * 确认付款（管理员）
     */
    @PostMapping("/{id}/pay")
    public ResponseEntity<ApiResponse<Void>> paySettlement(
            @PathVariable String id,
            @AuthenticationPrincipal UserPrincipal principal) {

        if (!"admin".equals(principal.getRole())) {
            return ResponseEntity.badRequest().body(ApiResponse.error("只有管理员可以确认付款"));
        }
        try {
            settlementService.paySettlement(id);
            return ResponseEntity.ok(ApiResponse.success(null, "结算单已付款"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * 删除结算单（管理员）
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteSettlement(
            @PathVariable String id,
            @AuthenticationPrincipal UserPrincipal principal) {

        if (!"admin".equals(principal.getRole())) {
            return ResponseEntity.badRequest().body(ApiResponse.error("只有管理员可以删除结算单"));
        }
        try {
            settlementService.deleteSettlement(id);
            return ResponseEntity.ok(ApiResponse.success(null, "结算单已删除"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }
}
