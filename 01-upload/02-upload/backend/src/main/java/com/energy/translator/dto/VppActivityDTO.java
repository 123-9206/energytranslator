package com.energy.translator.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VppActivityDTO {
    private String id;
    private String title;
    private String type;
    private String typeName;
    private String description;
    private String status;
    private String statusName;
    private Instant registrationDeadline;
    private Instant startAt;
    private Instant endAt;
    private Double totalCapacity;
    private Integer minDuration;
    private Integer maxResponseTime;
    private Double capacityPrice;
    private Double energyPrice;
    private Double minimumCapacity;
    private Integer enrolledCount;
    private Double committedCapacity;
    private Double fillRate;
    private Boolean isEnrolled;
    private Boolean canEnroll;
    private Long timeUntilStart;
    private Instant createdAt;
}
