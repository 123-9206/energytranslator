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

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "enterprises")
public class Enterprise {
    
    @Id
    private String id;
    
    private String name;
    
    private String type;  // manufacturing, commercial, residential, general
    
    private String creditCode;
    
    private String status;  // pending, verified, rejected
    
    private String contactName;
    
    private String contactPhone;
    
    private String contactEmail;
    
    private String address;
    
    private String industry;
    
    private CapacityInfo capacityInfo;
    
    @CreatedDate
    private Instant createdAt;
    
    @LastModifiedDate
    private Instant updatedAt;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CapacityInfo {
        private Double totalLoad;
        private Double adjustableCapacity;
        private Double contractCapacity;
    }
}
