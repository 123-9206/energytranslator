package com.energy.translator.controller;

import com.energy.translator.dto.ApiResponse;
import com.energy.translator.security.UserPrincipal;
import com.energy.translator.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    /**
     * 老板钱包账总览（VPP收益 + 绿电年省 + 诊断年省）
     * 前端 api-http.js.getWalletSummary() -> GET /api/wallet/summary
     */
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<Map<String, Object>>> summary(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(walletService.getWalletSummary(principal.getEnterpriseId())));
    }
}
