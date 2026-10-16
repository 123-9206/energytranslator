package com.energy.translator.service;

import com.energy.translator.dto.BillData;
import com.energy.translator.entity.BillCheckReport.CheckItem;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 勾稽校验服务。原则：数字之间的勾稽关系是"确定性的"，不允许模型背书。
 * 校验不通过 → 整单转人工复核，绝不带病进入规则引擎。
 * 容差：±1%（账单四舍五入、舍入进位导致的天然误差）。
 */
@Service
public class BillValidationService {

    private static final double TOLERANCE_PCT = 1.0;

    public static class ValidationResult {
        public final boolean passed;
        public final List<CheckItem> items;
        public ValidationResult(boolean passed, List<CheckItem> items) {
            this.passed = passed;
            this.items = items;
        }
    }

    public ValidationResult validate(BillData d) {
        List<CheckItem> items = new ArrayList<>();

        // 闸1：四时段电量之和 = 总电量
        if (d.getTotalKwh() != null && anyPresent(d.getTipKwh(), d.getPeakKwh(), d.getFlatKwh(), d.getValleyKwh())) {
            double sum = nz(d.getTipKwh()) + nz(d.getPeakKwh()) + nz(d.getFlatKwh()) + nz(d.getValleyKwh());
            items.add(build("时段电量勾稽（尖+峰+平+谷=总电量）", d.getTotalKwh(), sum));
        }

        // 闸2：费用加总 = 总电费（基本+电度+力调 ≈ 总额；代征基金等小额并入容差）
        if (d.getTotalAmount() != null && d.getEnergyFee() != null) {
            double sum = nz(d.getBasicFee()) + nz(d.getEnergyFee()) + nz(d.getPowerFactorAdjustmentFee());
            // 代征附加常见为总额的一定比例，放宽容差到 8%
            items.add(buildWithTolerance("费用勾稽（基本+电度+力调≈总额）", d.getTotalAmount(), sum, 8.0));
        }

        // 闸3：字段合理域
        if (d.getPowerFactor() != null) {
            boolean ok = d.getPowerFactor() >= 0.5 && d.getPowerFactor() <= 1.0;
            items.add(CheckItem.builder()
                    .name("功率因数合理域 [0.5, 1.0]")
                    .expected("[0.5, 1.0]")
                    .actual(String.valueOf(d.getPowerFactor()))
                    .passed(ok)
                    .build());
        }
        if (d.getTotalKwh() != null) {
            boolean ok = d.getTotalKwh() > 0 && d.getTotalKwh() < 100_000_000;
            items.add(CheckItem.builder()
                    .name("总电量合理域")
                    .expected("0 < totalKwh < 1亿")
                    .actual(String.valueOf(d.getTotalKwh()))
                    .passed(ok)
                    .build());
        }
        if ("TWO_PART".equals(d.getMeterType()) && "CAPACITY".equals(d.getPricingMode())
                && d.getContractCapacityKva() != null && d.getMaxDemandKw() != null) {
            // 实际最大需量不应长期超过合同容量（单月略超可能，超 20% 视为可疑）
            boolean ok = d.getMaxDemandKw() <= d.getContractCapacityKva() * 1.2;
            items.add(CheckItem.builder()
                    .name("需量/容量合理性（实际需量 ≤ 容量×1.2）")
                    .expected("<= " + d.getContractCapacityKva() * 1.2)
                    .actual(String.valueOf(d.getMaxDemandKw()))
                    .passed(ok)
                    .build());
        }

        boolean passed = !items.isEmpty() && items.stream().allMatch(i -> Boolean.TRUE.equals(i.getPassed()));
        return new ValidationResult(passed, items);
    }

    private CheckItem build(String name, double expected, double actual) {
        return buildWithTolerance(name, expected, actual, TOLERANCE_PCT);
    }

    private CheckItem buildWithTolerance(String name, double expected, double actual, double tolPct) {
        double diffPct = expected == 0 ? (actual == 0 ? 0 : 100) : Math.abs(actual - expected) / Math.abs(expected) * 100;
        return CheckItem.builder()
                .name(name)
                .expected(fmt(expected))
                .actual(fmt(actual))
                .diffPct(round2(diffPct))
                .passed(diffPct <= tolPct)
                .build();
    }

    private boolean anyPresent(Double... vals) {
        for (Double v : vals) if (v != null) return true;
        return false;
    }

    private double nz(Double v) { return v == null ? 0 : v; }

    private String fmt(double v) { return String.format("%.2f", v); }

    private double round2(double v) { return Math.round(v * 100.0) / 100.0; }
}
