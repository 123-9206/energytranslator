package com.energy.translator.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;
import java.util.List;

@Data
@Document(collection = "system_settings")
public class SystemSettings {

    @Id
    private String id;

    private Integer collectInterval = 60;
    private Integer pollInterval = 60;
    private Integer threadPool = 10;

    private String mqttBroker = "tcp://localhost:1883";
    private String mqttClientId = "energy_translator_backend";
    private String mqttTopic = "energy/device/+/data";

    private Integer alarmCooldown = 300;
    private List<String> notifyMethods = List.of("email");

    private Double carbonFactor = 0.785;
    private Double electricityPrice = 0.60;

    private Integer jwtExpiry = 2;
    private Integer maxLoginAttempts = 5;

    private Date createdAt;
    private Date updatedAt;
}
