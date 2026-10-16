package com.energy.translator.service;

import com.energy.translator.entity.GreenPowerContract;
import com.energy.translator.entity.VppSettlement;
import com.energy.translator.repository.GreenPowerContractRepository;
import com.energy.translator.repository.VppSettlementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 老板钱包账：聚合企业可量化的省费/收益，给出"老板一眼看懂"的总账。
 * 来源：VPP 结算净额（已结算/已提现）、绿电预估年节约、AI 诊断预估年省。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WalletService {

    private final VppSettlementRepository vppSettlementRepository;
    private final GreenPowerContractRepository greenPowerContractRepository;
    private final AIDiagnosisService aiDiagnosisService;

    public Map<String, Object> getWalletSummary(String enterpriseId) {
        // 1. VPP 结算净额（paid/withdrawn 计为已确认收益）
        List<VppSettlement> settlements = vppSettlementRepository.findByEnterpriseId(enterpriseId);
        double vppTotal = 0;
        double vppWithdrawn = 0;
        for (VppSettlement s : settlements) {
            Double amt = s.getSummary() != null ? s.getSummary().getTotalCompensation() : null;
            if (amt == null && s.getSummary() != null) amt = s.getSummary().getTotalAmount();
            if (amt != null) {
                vppTotal += amt;
                if ("withdrawn".equals(s.getStatus())) vppWithdrawn += amt;
            }
        }

        // 2. 绿电预估年节约
        List<GreenPowerContract> greens = greenPowerContractRepository.findByEnterpriseId(enterpriseId);
        double greenSaving = greens.stream()
                .mapToDouble(g -> g.getEstimatedSaving() != null ? g.getEstimatedSaving() : 0)
                .sum();

        // 3. AI 诊断预估年省（基于真实数据，无数据时为0）
        double diagnosisAnnual = 0;
        try {
            Map<String, Object> diag = aiDiagnosisService.diagnose(enterpriseId, null, 30);
            Object sv = diag.get("estimatedAnnualSaving");
            if (sv instanceof String) {
                diagnosisAnnual = Double.parseDouble(((String) sv).replace(",", ""));
            }
        } catch (Exception e) {
            log.debug("诊断年省计算跳过: {}", e.getMessage());
        }

        double totalSaved = vppTotal + greenSaving + diagnosisAnnual;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("vppSettled", round2(vppTotal));
        result.put("vppWithdrawn", round2(vppWithdrawn));
        result.put("vppWithdrawable", round2(vppTotal - vppWithdrawn));
        result.put("greenSavingAnnual", round2(greenSaving));
        result.put("diagnosisSavingAnnual", round2(diagnosisAnnual));
        result.put("totalSavedAnnual", round2(totalSaved));
        result.put("currency", "元");
        result.put("updatedAt", new Date().toString());
        return result;
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
