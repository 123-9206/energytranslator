package com.energy.translator.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SettlementDTO {
    private String id;
    private String period;
    private Integer totalActivities;
    private Double totalCapacity;
    private Double totalEnergy;
    private Double capacityCompensation;
    private Double energyCompensation;
    private Double bonusCompensation;
    private Double penaltyAmount;
    private Double totalAmount;
    private String status;
    private String statusName;
    private String createdAt;
}
