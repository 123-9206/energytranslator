package com.energy.translator.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * 合作售电分润结算单
 * 基于企业测算省电费基数 × 分润比例，计算平台留存额与分给合作方额。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "revenue_share_settlements")
public class RevenueShareSettlement {

    @Id
    private String id;

    private String enterpriseId;

    private String enterpriseName;

    private String partnerId;

    private String partnerCompanyName;

    /** 账期 yyyy-MM */
    private String period;

    /** 测算省电费基数（元/月） */
    private Double savingBase;

    /** 平台留存分润比例 */
    private Double platformShareRatio;

    /** 平台留存额 */
    private Double platformShareAmount;

    /** 分给合作方额 */
    private Double partnerShareAmount;

    /** 状态：pending / settled */
    private String status;

    @CreatedDate
    private Instant createdAt;
}
