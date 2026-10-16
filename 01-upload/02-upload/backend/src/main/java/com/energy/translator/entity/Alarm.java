package com.energy.translator.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "alarms")
public class Alarm {
    
    @Id
    private String id;
    
    private String enterpriseId;
    
    private String deviceId;
    
    private String deviceName;
    
    private String ruleId;
    
    private String ruleName;
    
    private Double triggerValue;
    
    private Double thresholdValue;
    
    private String condition;
    
    private String type;  // over_limit, under_limit, offline, anomaly
    
    private String level;  // info, warning, critical
    
    private String title;
    
    private String description;
    
    private String status;  // active, acknowledged, resolved, ignored
    
    private Instant occurredAt;
    
    private Instant acknowledgedAt;
    
    private String acknowledgedBy;
    
    private Instant resolvedAt;
    
    private String resolvedBy;
    
    private String solution;
    
    @CreatedDate
    private Instant createdAt;
}
