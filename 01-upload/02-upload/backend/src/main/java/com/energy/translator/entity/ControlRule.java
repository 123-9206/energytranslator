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
@Document(collection = "control_rules")
public class ControlRule {
    
    @Id
    private String id;
    
    private String enterpriseId;
    
    private String name;
    
    private String type;  // auto, timer
    
    private String targetType;  // device, site, all
    
    private List<String> targetIds;
    
    private String action;  // turn_on, turn_off, set_mode, set_temp
    
    private Object actionParams;
    
    private RuleCondition condition;
    
    private String cronExpression;
    
    private Boolean enabled;
    
    private Integer priority;
    
    @CreatedDate
    private Instant createdAt;
    
    @LastModifiedDate
    private Instant updatedAt;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RuleCondition {
        private String type;  // time, threshold, manual
        private Object params;
    }
}
