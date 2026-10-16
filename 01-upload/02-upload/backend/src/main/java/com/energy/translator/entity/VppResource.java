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
@Document(collection = "vpp_resources")
public class VppResource {
    
    @Id
    private String id;
    
    private String enterpriseId;
    
    private String name;
    
    private String type;  // adjustable_load, generator, storage
    
    private String location;
    
    private Double ratedPower;  // kW
    
    private AdjustableRange adjustableRange;
    
    private Integer responseTime;  // 秒
    
    private Integer continuousTime;  // 分钟
    
    private String status;  // pending, online, offline, maintenance
    
    private DeviceInfo deviceInfo;
    
    private List<String> certifications;
    
    private ResourceStatistics statistics;
    
    @CreatedDate
    private Instant createdAt;
    
    @LastModifiedDate
    private Instant updatedAt;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdjustableRange {
        private Double min;
        private Double max;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeviceInfo {
        private String manufacturer;
        private String model;
        private String serialNumber;
        private String protocol;  // modbus, mqtt, http, bacnet
        private String ipAddress;
        private Integer port;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResourceStatistics {
        private Integer totalResponseCount;
        private Integer completedResponseCount;
        private Double totalAdjustment;
        private Double avgResponseRate;
    }
}
