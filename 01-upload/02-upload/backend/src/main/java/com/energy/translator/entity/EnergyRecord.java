package com.energy.translator.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "energy_records")
public class EnergyRecord {
    
    @Id
    private String id;
    
    @Indexed
    private String enterpriseId;
    
    private String resourceId;
    
    private String siteId;
    
    private Instant timestamp;
    
    private Double value;  // kWh 累计/瞬时能耗
    
    private Double power;  // kW 有功功率
    
    private Double current;  // A 电流（单相额定值，三相取平均值）
    
    private Double voltage;  // V 电压（单相额定值，三相取平均值）
    
    private Double temperature;  // °C 设备/环境温度
    
    private Double powerFactor;  // 功率因数 0.0-1.0
    
    private Double carbonEmission;  // kg CO₂ 碳排放量
    
    private String unit;  // kWh
    
    private String source;  // manual, device, calculation
    
    @CreatedDate
    private Instant createdAt;
}
