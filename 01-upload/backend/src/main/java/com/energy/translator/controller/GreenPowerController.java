package com.energy.translator.controller;

import com.energy.translator.dto.ApiResponse;
import com.energy.translator.dto.PageResponse;
import com.energy.translator.entity.GreenPowerContract;
import com.energy.translator.security.UserPrincipal;
import com.energy.translator.service.GreenPowerService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 绿电交易台账
 * 企业：查看/新增自己的绿电合同与绿电收益汇总
 * 平台：全部企业合同管理
 */
@RestController
@RequestMapping("/api/green-power")
@RequiredArgsConstructor
public class GreenPowerController {

    private final GreenPowerService greenPowerService;

    /**
     * 企业侧：我的绿电合同列表
     */
    @GetMapping("/my")
    public ResponseEntity<ApiResponse<PageResponse<GreenPowerContract>>> getMyContracts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @AuthenticationPrincipal UserPrincipal principal) {

        Page<GreenPowerContract> result = greenPowerService.getMyContracts(principal.getEnterpriseId(), page, pageSize);
        return ResponseEntity.ok(ApiResponse.success(PageResponse.of(
                result.getContent(), result.getTotalElements(), page, pageSize)));
    }

    /**
     * 平台侧：全部合同（关键词/状态过滤）
     */
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<GreenPowerContract>>> getAllContracts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @AuthenticationPrincipal UserPrincipal principal) {

        if (!"admin".equals(principal.getRole())) {
            return ResponseEntity.badRequest().body(ApiResponse.error("只有管理员可以查看全部合同"));
        }
        Page<GreenPowerContract> result = greenPowerService.getAllContracts(keyword, status, page, pageSize);
        return ResponseEntity.ok(ApiResponse.success(PageResponse.of(
                result.getContent(), result.getTotalElements(), page, pageSize)));
    }

    /**
     * 合同详情（校验归属）
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<GreenPowerContract>> getContract(
            @PathVariable String id,
            @AuthenticationPrincipal UserPrincipal principal) {

        boolean isAdmin = "admin".equals(principal.getRole());
        try {
            GreenPowerContract contract = greenPowerService.getContract(id, principal.getEnterpriseId(), isAdmin);
            return ResponseEntity.ok(ApiResponse.success(contract));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * 新增绿电合同（企业侧）
     */
    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, String>>> createContract(
            @RequestBody GreenPowerContract contract,
            @AuthenticationPrincipal UserPrincipal principal) {

        try {
            String contractNo = greenPowerService.createContract(contract, principal.getEnterpriseId());
            return ResponseEntity.ok(ApiResponse.success(Map.of("contractNo", contractNo), "绿电合同创建成功"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * 更新合同
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> updateContract(
            @PathVariable String id,
            @RequestBody GreenPowerContract updates,
            @AuthenticationPrincipal UserPrincipal principal) {

        boolean isAdmin = "admin".equals(principal.getRole());
        try {
            greenPowerService.updateContract(id, updates, principal.getEnterpriseId(), isAdmin);
            return ResponseEntity.ok(ApiResponse.success(null, "合同更新成功"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * 删除合同（仅管理员）
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteContract(
            @PathVariable String id,
            @AuthenticationPrincipal UserPrincipal principal) {

        if (!"admin".equals(principal.getRole())) {
            return ResponseEntity.badRequest().body(ApiResponse.error("只有管理员可以删除合同"));
        }
        try {
            greenPowerService.deleteContract(id);
            return ResponseEntity.ok(ApiResponse.success(null, "合同已删除"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * 企业侧：绿电收益汇总
     */
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSummary(
            @AuthenticationPrincipal UserPrincipal principal) {

        return ResponseEntity.ok(ApiResponse.success(greenPowerService.getSummary(principal.getEnterpriseId())));
    }

    /**
     * 平台侧：全平台绿电统计
     */
    @GetMapping("/platform-summary")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getPlatformSummary(
            @AuthenticationPrincipal UserPrincipal principal) {

        if (!"admin".equals(principal.getRole())) {
            return ResponseEntity.badRequest().body(ApiResponse.error("只有管理员可以查看平台统计"));
        }
        return ResponseEntity.ok(ApiResponse.success(greenPowerService.getPlatformSummary()));
    }
}
