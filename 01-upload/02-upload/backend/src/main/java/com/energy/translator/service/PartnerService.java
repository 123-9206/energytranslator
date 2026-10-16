package com.energy.translator.service;

import com.energy.translator.entity.Partner;
import com.energy.translator.entity.RevenueShareSettlement;
import com.energy.translator.repository.PartnerRepository;
import com.energy.translator.repository.RevenueShareSettlementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 合作售电分润服务
 * 管理持牌合作售电公司，并基于企业测算省电费基数生成分润结算单。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PartnerService {

    private final PartnerRepository partnerRepository;
    private final RevenueShareSettlementRepository shareRepository;
    private final AIDiagnosisService aiDiagnosisService;

    public Partner createPartner(Partner partner) {
        partner.setCreatedAt(Instant.now());
        if (partner.getStatus() == null) partner.setStatus("active");
        if (partner.getPlatformShareRatio() == null) partner.setPlatformShareRatio(0.3);
        return partnerRepository.save(partner);
    }

    /**
     * 列出某企业的可用合作售电公司：企业专属 + 平台级通用
     */
    public List<Partner> listPartners(String enterpriseId) {
        List<Partner> list = new ArrayList<>();
        list.addAll(partnerRepository.findByEnterpriseId(enterpriseId));
        list.addAll(partnerRepository.findByEnterpriseIdIsNull());
        return list;
    }

    /**
     * 生成某账期分润结算单：
     * 省电费基数取自 AI 诊断峰谷可月省（真实数据派生，非写死），
     * 平台留存 = 基数 × platformShareRatio，其余分给合作方。
     */
    public RevenueShareSettlement calculateShare(String enterpriseId, String enterpriseName,
                                                 String partnerId, String period) {
        Partner partner = partnerRepository.findById(partnerId)
                .orElseThrow(() -> new IllegalArgumentException("合作售电公司不存在: " + partnerId));

        String calcPeriod = (period != null && !period.isBlank())
                ? period
                : YearMonth.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));

        double ratio = partner.getPlatformShareRatio() != null ? partner.getPlatformShareRatio() : 0.3;

        // 测算省电费基数（元/月）：来自 AI 诊断峰谷可优化月省
        double savingBase = resolveMonthlySaving(enterpriseId);

        double platformAmt = savingBase * ratio;
        double partnerAmt = savingBase * (1 - ratio);

        RevenueShareSettlement settlement = RevenueShareSettlement.builder()
                .enterpriseId(enterpriseId)
                .enterpriseName(enterpriseName)
                .partnerId(partner.getId())
                .partnerCompanyName(partner.getPartnerCompanyName())
                .period(calcPeriod)
                .savingBase(round2(savingBase))
                .platformShareRatio(ratio)
                .platformShareAmount(round2(platformAmt))
                .partnerShareAmount(round2(partnerAmt))
                .status("pending")
                .createdAt(Instant.now())
                .build();

        return shareRepository.save(settlement);
    }

    public List<RevenueShareSettlement> getSettlements(String enterpriseId) {
        return shareRepository.findByEnterpriseId(enterpriseId);
    }

    private double resolveMonthlySaving(String enterpriseId) {
        try {
            Map<String, Object> diag = aiDiagnosisService.diagnose(enterpriseId, null, 30);
            Object pv = diag.get("peakValleyAnalysis");
            if (pv instanceof Map) {
                Object sm = ((Map<?, ?>) pv).get("potentialMonthlySaving");
                if (sm instanceof String) {
                    return Double.parseDouble(((String) sm).replace(",", ""));
                }
            }
        } catch (Exception e) {
            log.debug("分润基数测算跳过: {}", e.getMessage());
        }
        return 0.0;
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
