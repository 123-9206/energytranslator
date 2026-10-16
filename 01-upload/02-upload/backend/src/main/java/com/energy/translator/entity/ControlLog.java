package com.energy.translator.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * 设备控制操作日志实体
 * 持久化到 MongoDB，防止重启丢失
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "control_logs")
@CompoundIndex(name = "idx_enterprise_device", def = "{'enterpriseId': 1, 'deviceId': 1}")
public class ControlLog {

    @Id
    private String id;

    /** 企业ID（多租户隔离） */
    @Indexed
    private String enterpriseId;

    /** 设备ID */
    @Indexed
    private String deviceId;

    /** 控制动作/状态 */
    private String state;

    /** 控制值（如设定温度、转速等） */
    private Object value;

    /** 执行状态: success / failed / sent */
    private String status;

    /** 执行结果消息 */
    private String message;

    /** 操作人ID */
    private String operator;

    /** 操作时间 */
    @Indexed
    private Instant timestamp;
}
