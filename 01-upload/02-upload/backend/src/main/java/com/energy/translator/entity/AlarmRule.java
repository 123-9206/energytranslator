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

/**
 * 告警规则实体
 * 定义触发告警的条件（阈值、指标、严重等级等）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "alarm_rules")
public class AlarmRule {

    @Id
    private String id;

    private String enterpriseId;

    private String name;

    /** 关联设备名称（可为空表示全局规则） */
    private String deviceName;

    /** 关联设备ID（可为空） */
    private String deviceId;

    /** 监控指标: energy, power, temperature, carbon, voltage, current */
    private String metric;

    /** 条件运算符: gt(大于), lt(小于), eq(等于), gte(大于等于), lte(小于等于) */
    private String condition;

    /** 阈值 */
    private Double threshold;

    /** 持续时间（秒），超过此时长才触发告警，0表示即时触发 */
    private Integer durationSeconds;

    /** 严重等级: info, warning, critical */
    private String severity;

    /** 是否启用 */
    private Boolean enabled;

    /** 告警通知方式: app, sms, email */
    private String notifyMethod;

    /** 冷却期（秒），同规则触发后在此期间内不再重复触发 */
    private Integer cooldownSeconds;

    /** 描述 */
    private String description;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
