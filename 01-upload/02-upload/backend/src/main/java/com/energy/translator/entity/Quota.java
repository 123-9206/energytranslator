package com.energy.translator.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
@Document(collection = "quotas")
public class Quota {

    @Id
    private String id;

    private String enterpriseId;

    /** 年度 */
    private int year;

    /** 核定配额 kg */
    private double allocatedQuota;

    /** 已购碳汇 kg */
    private double purchasedQuota;

    /** 已消耗 kg */
    private double consumedQuota;

    /** 预警阈值 %（缺省 80） */
    private int alertThreshold;

    @CreatedDate
    @JsonIgnore
    private Instant createdAt;

    @LastModifiedDate
    @JsonIgnore
    private Instant updatedAt;
}
