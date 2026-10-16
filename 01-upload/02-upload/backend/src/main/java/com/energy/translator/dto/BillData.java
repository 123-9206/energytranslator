package com.energy.translator.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * AI读单提取结果。原则：AI只负责"抄字段"，不负责任何计算。
 * 所有字段允许为 null（看不清/没有该字段），由校验层和规则引擎兜底。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BillData {

    /** 账期，格式 yyyy-MM */
    private String billMonth;

    private String customerName;
    private String customerNo;

    /** 电价类别：TWO_PART=两部制, SINGLE=单一制 */
    private String meterType;

    /** 电压等级，如 "1-10kV" */
    private String voltageLevel;

    /** 基本电费计价方式：CAPACITY=按容量, DEMAND=按核定需量, ACTUAL_DEMAND=按实际需量 */
    private String pricingMode;

    /** 合同容量 kVA（按容量计费时） */
    private Double contractCapacityKva;

    /** 核定需量 kW（按需量计费时） */
    private Double demandValueKw;

    /** 本月实际最大需量 kW */
    private Double maxDemandKw;

    // ---- 电量（kWh） ----
    private Double tipKwh;      // 尖峰
    private Double peakKwh;     // 高峰
    private Double flatKwh;     // 平段
    private Double valleyKwh;   // 低谷
    private Double totalKwh;    // 总电量

    // ---- 金额（元） ----
    private Double basicFee;                    // 基本电费
    private Double energyFee;                   // 电度电费
    private Double powerFactorAdjustmentFee;    // 力调电费（正=罚款，负=奖励）
    private Double totalAmount;                 // 总电费

    /** 功率因数，如 0.87 */
    private Double powerFactor;
}
