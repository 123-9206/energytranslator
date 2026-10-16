package com.energy.translator.service;

import com.energy.translator.entity.Quota;
import com.energy.translator.repository.QuotaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Year;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class QuotaService {

    private final QuotaRepository quotaRepository;
    private final CarbonService carbonService;

    /**
     * 获取当前年度配额。若不存在则返回全 0 的默认值（避免前端"加载失败"）。
     */
    public Quota getCurrentQuota(String enterpriseId) {
        int year = Year.now().getValue();
        return quotaRepository.findByEnterpriseIdAndYear(enterpriseId, year)
                .orElseGet(() -> Quota.builder()
                        .enterpriseId(enterpriseId)
                        .year(year)
                        .allocatedQuota(0.0)
                        .purchasedQuota(0.0)
                        .consumedQuota(0.0)
                        .alertThreshold(80)
                        .build());
    }

    /**
     * 获取配额历史（按年度倒序）。
     */
    public List<Quota> getQuotaHistory(String enterpriseId) {
        return quotaRepository.findByEnterpriseIdOrderByYearDesc(enterpriseId);
    }

    /**
     * 碳资产盈余核算：实际排放（来自 CarbonService 本年碳排放，内部单位 kg CO₂）vs 核定配额+已购碳汇。
     * 单位统一为市场口径 吨CO₂（tCO₂，与全国碳市场一致）：实际排放 kg→t 换算后参与计算，
     * 避免 kg/t 量级差造成的“超排”误判。盈余=配额+已购-实际排放。
     */
    public Map<String, Object> getCarbonAsset(String enterpriseId) {
        Quota q = getCurrentQuota(enterpriseId);
        Map<String, Object> carbon = carbonService.getCarbonSummary(enterpriseId);
        // CarbonService.yearCarbon 单位为 kg CO₂；碳市场口径为 吨CO₂(tCO₂)，统一换算
        double actualKg = ((Number) carbon.get("yearCarbon")).doubleValue();
        double actual = actualKg / 1000.0; // kg -> t

        double allocated = q.getAllocatedQuota();   // 单位：tCO₂
        double purchased = q.getPurchasedQuota();    // 单位：tCO₂
        double surplus = allocated + purchased - actual; // tCO₂

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("unit", "tCO₂");
        result.put("allocatedQuota", round2(allocated));
        result.put("purchasedQuota", round2(purchased));
        result.put("actualEmission", round2(actual));
        result.put("surplus", round2(surplus));
        result.put("status", surplus >= 0 ? "盈余" : "超排");
        result.put("alertThreshold", q.getAlertThreshold());
        result.put("unitNote", "单位：吨CO₂（tCO₂），与全国碳市场口径一致；盈余=核定配额+已购碳汇−实际排放(tCO₂)");
        return result;
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
