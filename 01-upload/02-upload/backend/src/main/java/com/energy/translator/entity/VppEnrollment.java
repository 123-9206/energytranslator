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
@Document(collection = "vpp_enrollments")
public class VppEnrollment {
    
    @Id
    private String id;
    
    private String activityId;
    
    private String enterpriseId;
    
    private List<String> resourceIds;
    
    private Double committedCapacity;  // kW
    
    private String contactName;
    
    private String contactPhone;
    
    private String status;  // enrolled, confirmed, cancelled
    
    private Double completionRate;  // 活动完成率 0.0-1.0
    
    private Instant enrolledAt;
    
    private Instant cancelledAt;
    
    @CreatedDate
    private Instant createdAt;
    
    @LastModifiedDate
    private Instant updatedAt;
}
