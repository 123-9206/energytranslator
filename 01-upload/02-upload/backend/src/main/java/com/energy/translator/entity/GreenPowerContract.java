package com.energy.translator.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * 绿电交易台账
 * 记录企业购买绿电（风电/光伏）的合同、消纳、绿证与碳减排情况
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "green_power_contracts")
public class GreenPowerContract {

    @Id
    private String id;

    /** 所属企业ID */
    @Indexed
    private String enterpriseId;

    /** 企业名称 */
    private String enterpriseName;

    /** 合同编号，如 GP-2026-0001 */
    private String contractNo;

    /** 绿电供应商（风电场/售电公司） */
    private String supplierName;

    /** 供应商联系人 */
    private String supplierContact;

    /** 供应商电话 */
    private String supplierPhone;

    /** 合同电量 kWh */
    private Double energyVolume;

    /** 绿电溢价（元/kWh），即绿电比普通电贵出的部分 */
    private Double pricePremium;

    /** 合同总额（元） */
    private Double totalAmount;

    /** 合同开始日期 yyyy-MM-dd */
    private String startDate;

    /** 合同结束日期 yyyy-MM-dd */
    private String endDate;

    /** 合同状态：draft/active/completed/terminated */
    private String status;

    /** 绿证数量（1张=1000kWh） */
    private Integer certificateCount;

    /** 绿证状态：unclaimed/claimed */
    private String certificateStatus;

    /** 实际消纳电量 kWh */
    private Double actualVolume;

    /** 碳减排量 kg CO₂ = 实际消纳电量 × 0.785 */
    private Double carbonOffset;

    /** 预估年节约成本（元）——峰谷优化+需求响应综合测算 */
    private Double estimatedSaving;

    /** 备注 */
    private String notes;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
