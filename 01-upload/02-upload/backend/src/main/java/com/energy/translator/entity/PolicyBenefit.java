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
import java.util.List;

/**
 * 政策福利中心
 * 汇聚绿电/碳交易/需求响应/节能改造等可申报政策，按企业画像智能匹配
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "policy_benefits")
public class PolicyBenefit {

    @Id
    private String id;

    /** 政策标题 */
    private String title;

    /** 政策类型：green_power(绿电)/carbon(碳交易)/demand_response(需求响应)/energy_saving(节能改造)/green_factory(绿色工厂)/subsidy(专项补贴) */
    @Indexed
    private String policyType;

    /** 适用区域：江苏省/苏州市/全国 */
    private String region;

    /** 政策内容描述 */
    private String description;

    /** 适用对象（如：年用电量500万度以上工商业企业） */
    private String eligibility;

    /** 预估福利金额（元/年） */
    private Double benefitAmount;

    /** 申报截止日期 yyyy-MM-dd */
    private String applicationDeadline;

    /** 状态：active/expired */
    private String status;

    /** 政策文号或来源（如苏发改能源〔2026〕XX号） */
    private String source;

    /** 申报入口链接 */
    private String applyUrl;

    /** 匹配规则：适用行业列表 */
    private List<String> matchIndustries;

    /** 匹配规则：最低年用电量（kWh） */
    private Double minAnnualUsage;

    /** 已申报企业数 */
    private Integer applyCount;

    /** 政策要点（结构化列表） */
    private List<String> highlights;

    /** 平台操作备注 */
    private String notes;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
