package com.energy.translator.controller;

import com.energy.translator.dto.ApiResponse;
import com.energy.translator.entity.Partner;
import com.energy.translator.entity.RevenueShareSettlement;
import com.energy.translator.security.UserPrincipal;
import com.energy.translator.service.PartnerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/partners")
@RequiredArgsConstructor
public class PartnerController {

    private final PartnerService partnerService;

    /**
     * 创建合作售电公司（平台/管理员维护持牌方）
     * 前端 api-http.js.createPartner() -> POST /api/partners
     */
    @PostMapping
    public ResponseEntity<ApiResponse<Partner>> create(@RequestBody Partner partner) {
        return ResponseEntity.ok(ApiResponse.success(partnerService.createPartner(partner)));
    }

    /**
     * 列出本企业可用合作售电公司（企业专属 + 平台通用）
     * 前端 api-http.js.listPartners() -> GET /api/partners
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<Partner>>> list(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(partnerService.listPartners(principal.getEnterpriseId())));
    }

    /**
     * 生成某账期合作售电分润结算单（基于企业测算省电费基数）
     * 前端 api-http.js.calculateRevenueShare(partnerId, period) -> POST /api/partners/revenue-shares/calculate?partnerId=&period=
     */
    @PostMapping("/revenue-shares/calculate")
    public ResponseEntity<ApiResponse<RevenueShareSettlement>> calculate(
            @RequestParam String partnerId,
            @RequestParam(required = false) String period,
            @RequestParam(required = false) String enterpriseName,
            @AuthenticationPrincipal UserPrincipal principal) {
        RevenueShareSettlement settlement = partnerService.calculateShare(
                principal.getEnterpriseId(),
                enterpriseName != null ? enterpriseName : principal.getEnterpriseId(),
                partnerId, period);
        return ResponseEntity.ok(ApiResponse.success(settlement));
    }

    /**
     * 查询本企业分润结算单列表
     * 前端 api-http.js.getRevenueShares() -> GET /api/partners/revenue-shares
     */
    @GetMapping("/revenue-shares")
    public ResponseEntity<ApiResponse<List<RevenueShareSettlement>>> shares(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(partnerService.getSettlements(principal.getEnterpriseId())));
    }
}
