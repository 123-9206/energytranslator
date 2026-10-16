package com.energy.translator.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "vpp_settlements")
public class VppSettlement {
    
    @Id
    private String id;
    
    private String enterpriseId;
    
    private String enterpriseName;
    
    private String period;  // 2024-01, 2024-Q1
    
    private Instant startDate;
    
    private Instant endDate;
    
    private SettlementSummary summary;
    
    private List<SettlementItem> items;
    
    private String status;  // pending, approved, paid, withdrawn
    
    private String approvedBy;
    
    private Instant approvedAt;
    
    private Instant paidAt;

    // 提现相关
    private String withdrawnBy;
    private Instant withdrawnAt;
    private String withdrawalAccount;

    private String remarks;
    
    @CreatedDate
    private Instant createdAt;
    
    @LastModifiedDate
    private Instant updatedAt;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SettlementSummary {
        private Integer totalActivities;
        private Double totalCapacity;  // kW
        private Double totalEnergy;  // kWh
        private Double capacityCompensation;  // 容量补贴
        private Double energyCompensation;  // 电量补贴
        private Double bonusCompensation;  // 完成奖励
        private Double penaltyAmount;  // 未完成扣罚
        private Double totalAmount;  // 合计

        // ---- 前端 settlement 详情页字段（与上面字段一一对应，供详情页零改动渲染）----
        private Double totalCapacityCompensation;   // = capacityCompensation
        private Double totalEnergyCompensation;     // = energyCompensation
        private Double totalCompensation;          // = totalAmount（净额）
        private Double totalCarbonReduction;       // 碳减排量 kg
        private Integer totalResponseCount;        // = totalActivities
        private Integer completedResponseCount;    // 完成响应的活动数
        private Double avgResponseRate;            // 平均响应率 %
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SettlementItem {
        private String activityId;
        private String activityTitle;
        private Double committedCapacity;
        private Double actualCapacity;
        private Double completionRate;
        private Double capacityCompensation;
        private Double energyCompensation;
        private Double bonus;
        private Double penalty;
        private Double total;
    }
}
