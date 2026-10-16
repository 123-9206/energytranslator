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
@Document(collection = "vpp_activities")
public class VppActivity {
    
    @Id
    private String id;
    
    private String title;
    
    private String type;  // peak_shaving, valley_filling, emergency, ancillary
    
    private String description;
    
    private String status;  // draft, registration, confirmed, executing, completed, cancelled
    
    private ActivityTime time;
    
    private ActivityRequirements requirements;
    
    private ActivityCompensation compensation;
    
    private List<String> enrolledResources;
    
    private Integer enrolledCount;
    
    private Double committedCapacity;
    
    private ActualResponse actualResponse;
    
    private String createdBy;
    
    private String cancelReason;
    
    private Instant cancelledAt;
    
    private Instant actualStartAt;
    
    private Instant actualEndAt;
    
    private Boolean evaluated;
    
    private Instant evaluatedAt;
    
    @CreatedDate
    private Instant createdAt;
    
    @LastModifiedDate
    private Instant updatedAt;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActivityTime {
        private Instant publishAt;
        private Instant registrationDeadline;
        private Instant startAt;
        private Instant endAt;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActivityRequirements {
        private Double totalCapacity;
        private Integer minDuration;  // 分钟
        private Integer maxResponseTime;  // 秒
        private List<String> qualifications;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActivityCompensation {
        private Double capacityPrice;  // 元/kW
        private Double energyPrice;  // 元/kWh
        private Double minimumCapacity;  // kW
        private Double completionBonus;
        private Double failurePenalty;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActualResponse {
        private Double totalCapacity;
        private Double completionRate;
    }
}
