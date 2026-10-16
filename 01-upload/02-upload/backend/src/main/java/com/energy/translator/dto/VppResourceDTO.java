package com.energy.translator.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VppResourceDTO {
    private String id;
    private String name;
    private String type;
    private String typeName;
    private String location;
    private Double ratedPower;
    private Double adjustableMin;
    private Double adjustableMax;
    private Integer responseTime;
    private Integer continuousTime;
    private String status;
    private String statusName;
    private String manufacturer;
    private String model;
    private String protocol;
    private Integer totalResponseCount;
    private Integer completedResponseCount;
    private Double avgResponseRate;
    private String enterpriseName;
    private String createdAt;
}
