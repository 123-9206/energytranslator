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
@Document(collection = "sites")
public class Site {
    
    @Id
    private String id;
    
    private String enterpriseId;
    
    private String name;
    
    private String address;
    
    private Double latitude;
    
    private Double longitude;
    
    private String type;  // factory, office, store, warehouse
    
    private Double totalArea;  // 平方米
    
    private Double ratedPower;  // kW
    
    @CreatedDate
    private Instant createdAt;
    
    @LastModifiedDate
    private Instant updatedAt;
}
