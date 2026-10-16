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
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "devices")
public class Device {
    
    @Id
    private String id;
    
    private String enterpriseId;
    
    private String siteId;
    
    private String name;
    
    private String type;  // meter, controller, sensor, gateway
    
    private String model;
    
    private String manufacturer;
    
    private String serialNumber;
    
    private String status;  // online, offline, maintenance（连接状态）

    /** 设备运行/电源状态：on / off / idle（与连接状态 status 区分，R2） */
    private String state;

    /** 最近一次控制设定值或采集读数（R2） */
    private Double currentValue;

    /** 额定参数（电路安全评估用，缺省时按默认值判定） */
    private Double ratedCurrent;   // A 额定电流

    private Double ratedVoltage;   // V 额定电压（默认220）

    private Integer phaseCount;    // 相数 1/3

    private Double ratedPower;     // kW 额定功率

    private String protocol;  // modbus, mqtt, http, bacnet
    
    private String ipAddress;
    
    private Integer port;
    
    private Map<String, Object> config;
    
    private DeviceStatistics statistics;
    
    @CreatedDate
    private Instant createdAt;
    
    @LastModifiedDate
    private Instant updatedAt;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeviceStatistics {
        private Long totalDataPoints;
        private Double totalEnergy;
        private Instant lastDataTime;
        private Double avgResponseTime;
    }
}
