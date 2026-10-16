package com.energy.translator.controller;

import com.energy.translator.dto.ApiResponse;
import com.energy.translator.dto.PageResponse;
import com.energy.translator.entity.PolicyBenefit;
import com.energy.translator.security.UserPrincipal;
import com.energy.translator.service.PolicyService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 政策福利中心
 * 平台：政策库管理
 * 企业：政策浏览 + 智能匹配推荐
 */
@RestController
@RequestMapping("/api/policies")
@RequiredArgsConstructor
public class PolicyController {

    private final PolicyService policyService;

    /**
     * 企业侧：可申报政策列表（含匹配标记，匹配优先）
     */
    @GetMapping("/available")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAvailablePolicies(
            @RequestParam(required = false) String policyType,
            @AuthenticationPrincipal UserPrincipal principal) {

        return ResponseEntity.ok(ApiResponse.success(
                policyService.getAvailablePolicies(principal.getEnterpriseId(), policyType)));
    }

    /**
     * 企业侧：政策汇总（可用数/预估福利/类型分布）
     */
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSummary(
            @AuthenticationPrincipal UserPrincipal principal) {

        return ResponseEntity.ok(ApiResponse.success(policyService.getSummary(principal.getEnterpriseId())));
    }

    /**
     * 平台侧：政策管理列表
     */
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<PolicyBenefit>>> getPolicies(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String policyType,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @AuthenticationPrincipal UserPrincipal principal) {

        Page<PolicyBenefit> result = policyService.getPolicies(status, policyType, keyword, page, pageSize);
        return ResponseEntity.ok(ApiResponse.success(PageResponse.of(
                result.getContent(), result.getTotalElements(), page, pageSize)));
    }

    /**
     * 平台侧：新增政策
     */
    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, String>>> createPolicy(
            @RequestBody PolicyBenefit policy,
            @AuthenticationPrincipal UserPrincipal principal) {

        if (!"admin".equals(principal.getRole())) {
            return ResponseEntity.badRequest().body(ApiResponse.error("只有管理员可以创建政策"));
        }
        try {
            String id = policyService.createPolicy(policy);
            return ResponseEntity.ok(ApiResponse.success(Map.of("policyId", id), "政策创建成功"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * 平台侧：更新政策
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> updatePolicy(
            @PathVariable String id,
            @RequestBody PolicyBenefit policy,
            @AuthenticationPrincipal UserPrincipal principal) {

        if (!"admin".equals(principal.getRole())) {
            return ResponseEntity.badRequest().body(ApiResponse.error("只有管理员可以更新政策"));
        }
        try {
            policyService.updatePolicy(id, policy);
            return ResponseEntity.ok(ApiResponse.success(null, "政策更新成功"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * 平台侧：删除政策
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deletePolicy(
            @PathVariable String id,
            @AuthenticationPrincipal UserPrincipal principal) {

        if (!"admin".equals(principal.getRole())) {
            return ResponseEntity.badRequest().body(ApiResponse.error("只有管理员可以删除政策"));
        }
        try {
            policyService.deletePolicy(id);
            return ResponseEntity.ok(ApiResponse.success(null, "政策已删除"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }
}
