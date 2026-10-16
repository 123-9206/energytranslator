package com.energy.translator.service;

import com.energy.translator.config.BillCheckProperties;
import com.energy.translator.dto.BillData;
import com.energy.translator.entity.BillCheckReport.Finding;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 规则引擎：4 个分析器，全部确定性计算，输入=校验通过的账单字段+配置电价。
 * 每个 Finding 都带 basis（来源行）：用了哪几个字段、哪个电价，可追溯。
 */
@Service
@RequiredArgsConstructor
public class BillRuleEngineService {

    private final BillCheckProperties props;

    public List<Finding> analyze(BillData d) {
        List<Finding> findings = new ArrayList<>();
        analyzeCapacityDemand(d, findings);
        analyzePeakValley(d, findings);
        analyzePowerFactor(d, findings);
        analyzeAnomaly(d, findings);
        return findings;
    }

    /** 分析器1：容需对比——基本电费计价方式是否最优 */
    private void analyzeCapacityDemand(BillData d, List<Finding> out) {
        if (!"TWO_PART".equals(d.getMeterType())) return;
        BillCheckProperties.Tariff t = props.getTariff();
        Double cap = d.getContractCapacityKva();
        Double actual = d.getMaxDemandKw();
        if (cap == null || actual == null || cap <= 0) return;

        double floor = cap * t.getDemandFloorRatio();

        if ("CAPACITY".equals(d.getPricingMode())) {
            double currentBasic = cap * t.getCapacityPricePerKva();
            double demandBasic = Math.max(actual, floor) * t.getDemandPricePerKw();
            if (demandBasic < currentBasic * 0.95) {
                out.add(Finding.builder()
                        .analyzer("CAPACITY_DEMAND")
                        .title("基本电费按需量计费更划算")
                        .monthlySavingEstimate(round0(currentBasic - demandBasic))
                        .basis("合同容量=" + cap + "kVA×" + t.getCapacityPricePerKva() + "元 vs 需量="
                                + fmt1(Math.max(actual, floor)) + "kW×" + t.getDemandPricePerKw() + "元；" + t.getSourceNote())
                        .detail("当前按容量计费，月基本电费约" + round0(currentBasic) + "元；若改按实际需量计费约"
                                + round0(demandBasic) + "元。可向供电营业厅申请变更计费方式（一般一个季度可变更一次）。")
                        .severity(currentBasic - demandBasic > 1000 ? "HIGH" : "MEDIUM")
                        .build());
            }
        } else if (("DEMAND".equals(d.getPricingMode()) || "ACTUAL_DEMAND".equals(d.getPricingMode()))
                && d.getDemandValueKw() != null && actual < d.getDemandValueKw() * 0.85) {
            double saving = (d.getDemandValueKw() - actual) * t.getDemandPricePerKw();
            out.add(Finding.builder()
                    .analyzer("CAPACITY_DEMAND")
                    .title("核定需量偏高，可申请下调")
                    .monthlySavingEstimate(round0(saving))
                    .basis("核定需量=" + d.getDemandValueKw() + "kW vs 实际最大需量=" + actual + "kW×"
                            + t.getDemandPricePerKw() + "元；" + t.getSourceNote())
                    .detail("实际最大需量仅为核定值的" + pct(actual / d.getDemandValueKw())
                            + "，下调核定需量可直接降低基本电费。")
                    .severity(saving > 1000 ? "HIGH" : "MEDIUM")
                    .build());
        }
    }

    /** 分析器2：峰谷结构——谷段电量占比与转移空间 */
    private void analyzePeakValley(BillData d, List<Finding> out) {
        if (d.getTotalKwh() == null || d.getTotalKwh() <= 0) return;
        BillCheckProperties.Tariff t = props.getTariff();
        double tip = nz(d.getTipKwh()), peak = nz(d.getPeakKwh()),
               flat = nz(d.getFlatKwh()), valley = nz(d.getValleyKwh());
        if (tip + peak + flat + valley <= 0) return;

        double valleyShare = valley / d.getTotalKwh();
        double shiftable = (tip + peak + flat) * t.getShiftableRatio();
        // 保守按平段与谷段价差估算转移收益下限
        double saving = shiftable * (t.getFlatPrice() - t.getValleyPrice());

        if (valleyShare < 0.30 && saving > 100) {
            out.add(Finding.builder()
                    .analyzer("PEAK_VALLEY")
                    .title("谷段用电占比偏低，存在错峰空间")
                    .monthlySavingEstimate(round0(saving))
                    .basis("谷段占比=" + pct(valleyShare) + "；按平谷价差" + fmt2(t.getFlatPrice() - t.getValleyPrice())
                            + "元/kWh、可转移" + pct(t.getShiftableRatio()) + "估算；" + t.getSourceNote())
                    .detail("谷段电量占比仅" + pct(valleyShare) + "。若将约" + round0(shiftable)
                            + "kWh/月的可移负荷（如空压机、烘干、充电）排入 23:00-7:00 谷段，按保守价差估算月省约"
                            + round0(saving) + "元。")
                    .severity(saving > 1500 ? "HIGH" : "MEDIUM")
                    .build());
        } else {
            out.add(Finding.builder()
                    .analyzer("PEAK_VALLEY")
                    .title("峰谷结构健康")
                    .monthlySavingEstimate(0.0)
                    .basis("谷段占比=" + pct(valleyShare))
                    .detail("谷段用电占比" + pct(valleyShare) + "，处于合理水平。")
                    .severity("INFO")
                    .build());
        }
    }

    /** 分析器3：功率因数力调——罚款是否可消除 */
    private void analyzePowerFactor(BillData d, List<Finding> out) {
        BillCheckProperties.Tariff t = props.getTariff();
        Double pf = d.getPowerFactor();
        Double pfFee = d.getPowerFactorAdjustmentFee();
        if (pf == null && (pfFee == null || pfFee <= 0)) return;

        if ((pf != null && pf < t.getPowerFactorStandard()) || (pfFee != null && pfFee > 0)) {
            double saving = pfFee != null && pfFee > 0 ? pfFee : 0;
            out.add(Finding.builder()
                    .analyzer("POWER_FACTOR")
                    .title("功率因数不达标，正在交力调罚款")
                    .monthlySavingEstimate(round0(saving))
                    .basis("功率因数=" + (pf == null ? "未读取" : pf) + "（考核标准" + t.getPowerFactorStandard()
                            + "）；本月力调电费=" + (pfFee == null ? "未读取" : fmt2(pfFee)) + "元")
                    .detail("功率因数低于考核标准会产生力调罚款。加装/检修无功补偿装置后，罚款可消除，"
                            + "补偿柜投资通常 3-6 个月回本。" + (saving <= 0 ? "（金额未读取到，需人工核对）" : ""))
                    .severity(saving > 500 ? "HIGH" : "MEDIUM")
                    .build());
        } else if (pf != null) {
            out.add(Finding.builder()
                    .analyzer("POWER_FACTOR")
                    .title("功率因数达标")
                    .monthlySavingEstimate(0.0)
                    .basis("功率因数=" + pf)
                    .detail("功率因数" + pf + "，达到考核标准。")
                    .severity("INFO")
                    .build());
        }
    }

    /** 分析器4：异常检测——负载率异常（轻载浪费/超载风险） */
    private void analyzeAnomaly(BillData d, List<Finding> out) {
        BillCheckProperties.Tariff t = props.getTariff();
        if ("TWO_PART".equals(d.getMeterType()) && d.getContractCapacityKva() != null
                && d.getContractCapacityKva() > 0 && d.getTotalKwh() != null) {
            double hours = 30.0 * 24;
            double loadFactor = d.getTotalKwh() / (d.getContractCapacityKva() * hours);
            if (loadFactor < t.getLowLoadFactor()) {
                // 轻载：评估减容收益（容量降到匹配实际需量的水平）
                double suggestCap = Math.max(d.getMaxDemandKw() == null ? 0 : d.getMaxDemandKw() * 1.3,
                        d.getContractCapacityKva() * 0.3);
                double saving = "CAPACITY".equals(d.getPricingMode())
                        ? Math.max(0, (d.getContractCapacityKva() - suggestCap) * t.getCapacityPricePerKva())
                        : 0;
                out.add(Finding.builder()
                        .analyzer("ANOMALY")
                        .title("变压器轻载，容量存在减容空间")
                        .monthlySavingEstimate(round0(saving))
                        .basis("负载率=" + pct(loadFactor) + "（月电量" + d.getTotalKwh() + "kWh / 容量"
                                + d.getContractCapacityKva() + "kVA）")
                        .detail("负载率仅" + pct(loadFactor) + "，变压器长期轻载运行。可评估减容或暂停部分容量"
                                + (saving > 0 ? "，按容量计费估算月省约" + round0(saving) + "元" : "")
                                + "。减容属营业厅免费业务，但需评估后续扩产需求。")
                        .severity(saving > 1000 ? "HIGH" : "MEDIUM")
                        .build());
                return;
            }
        }
        if (d.getMaxDemandKw() != null && d.getContractCapacityKva() != null
                && d.getMaxDemandKw() > d.getContractCapacityKva() * 0.95) {
            out.add(Finding.builder()
                    .analyzer("ANOMALY")
                    .title("需量接近容量上限，存在超载风险")
                    .monthlySavingEstimate(0.0)
                    .basis("实际最大需量=" + d.getMaxDemandKw() + "kW / 容量=" + d.getContractCapacityKva() + "kVA")
                    .detail("实际最大需量已达容量的"
                            + pct(d.getMaxDemandKw() / d.getContractCapacityKva())
                            + "，继续增产可能触发超容考核，建议提前规划增容或错峰。")
                    .severity("MEDIUM")
                    .build());
        }
    }

    private double nz(Double v) { return v == null ? 0 : v; }
    private String pct(double v) { return String.format("%.0f%%", v * 100); }
    private String fmt1(double v) { return String.format("%.1f", v); }
    private String fmt2(double v) { return String.format("%.2f", v); }
    private double round0(double v) { return Math.round(v); }
}
