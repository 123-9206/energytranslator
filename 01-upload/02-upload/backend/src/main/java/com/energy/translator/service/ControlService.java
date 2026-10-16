package com.energy.translator.service;

import com.energy.translator.entity.ControlLog;
import com.energy.translator.entity.ControlRule;
import com.energy.translator.entity.Device;
import com.energy.translator.repository.ControlLogRepository;
import com.energy.translator.repository.ControlRuleRepository;
import com.energy.translator.repository.DeviceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ControlService {

    private final DeviceRepository deviceRepository;
    private final ControlRuleRepository controlRuleRepository;
    private final ControlLogRepository controlLogRepository;
    private final ObjectMapper objectMapper;

    @Value("${mqtt.broker-url:tcp://localhost:1883}")
    private String mqttBrokerUrl;

    @Value("${mqtt.client-id:energy-translator-control}")
    private String mqttClientId;

    @Value("${mqtt.username:}")
    private String mqttUsername;

    @Value("${mqtt.password:}")
    private String mqttPassword;

    @Value("${control.enabled:false}")
    private boolean controlEnabled;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 下发设备控制指令
     */
    public Map<String, Object> sendControlCommand(String deviceId, Map<String, Object> command,
                                                    String enterpriseId, String userId) {
        if (!controlEnabled) {
            throw new IllegalStateException("设备控制未启用；本地展示默认不向设备发送指令");
        }
        // 1. 查找并验证设备
        Device device = deviceRepository.findById(deviceId)
                .filter(d -> enterpriseId.equals(d.getEnterpriseId()))
                .orElseThrow(() -> new RuntimeException("设备不存在"));

        if (!"online".equals(device.getStatus())) {
            throw new RuntimeException("设备不在线，无法控制");
        }

        // 2. 根据设备协议分发控制指令
        String protocol = device.getProtocol();
        String state = (String) command.getOrDefault("action", command.getOrDefault("state", "on"));
        Object value = command.get("value");

        boolean success = false;
        String message;

        try {
            success = switch (protocol != null ? protocol : "http") {
                case "modbus" -> sendModbusCommand(device, state, value);
                case "mqtt" -> sendMqttCommand(device, state, value);
                case "http" -> sendHttpCommand(device, state, value);
                default -> {
                    log.warn("不支持的协议: {}，使用HTTP模拟", protocol);
                    yield sendHttpCommand(device, state, value);
                }
            };
        } catch (Exception e) {
            log.error("设备控制失败: deviceId={}, error={}", deviceId, e.getMessage());
            message = "控制指令发送失败: " + e.getMessage();
            recordControlLog(deviceId, state, value, "failed", message, userId);
            throw new RuntimeException(message);
        }

        message = success ? "控制指令已成功发送" : "控制指令发送完成（异步执行）";

        // 3. 记录控制日志
        recordControlLog(deviceId, state, value, success ? "success" : "sent", message, userId);

        // 4. 更新设备运行/电源状态（R1 修复：on/off 不再误写为 online；
        //    status 字段仅表示连接状态，下发控制指令后保持不变）
        if (success && state != null) {
            device.setState("on".equals(state) ? "on" : "off");
            if (value != null && value instanceof Number) {
                device.setCurrentValue(((Number) value).doubleValue());
            }
            device.setUpdatedAt(Instant.now());
            deviceRepository.save(device);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("deviceId", deviceId);
        result.put("command", command);
        result.put("success", success);
        result.put("message", message);
        result.put("timestamp", Instant.now().toString());

        return result;
    }

    /**
     * 批量控制
     */
    public Map<String, Object> batchControl(List<String> deviceIds, Map<String, Object> command,
                                             String enterpriseId, String userId) {
        int success = 0;
        int failed = 0;
        List<String> failedDevices = new ArrayList<>();

        for (String deviceId : deviceIds) {
            try {
                sendControlCommand(deviceId, command, enterpriseId, userId);
                success++;
            } catch (Exception e) {
                failed++;
                failedDevices.add(deviceId);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", deviceIds.size());
        result.put("success", success);
        result.put("failed", failed);
        result.put("failedDevices", failedDevices);
        result.put("message", String.format("成功 %d 台，失败 %d 台", success, failed));

        return result;
    }

    /**
     * 获取控制日志（从 MongoDB 分页查询，不再依赖内存）
     */
    public Map<String, Object> getControlLogs(String enterpriseId, String deviceId,
                                               int page, int size) {
        PageRequest pageRequest = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "timestamp"));

        Page<ControlLog> logPage;
        if (deviceId != null && !deviceId.isEmpty()) {
            logPage = controlLogRepository.findByEnterpriseIdAndDeviceId(enterpriseId, deviceId, pageRequest);
        } else {
            logPage = controlLogRepository.findByEnterpriseId(enterpriseId, pageRequest);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("list", logPage.getContent());
        result.put("total", logPage.getTotalElements());
        result.put("page", page);
        result.put("size", size);
        result.put("totalPages", logPage.getTotalPages());
        return result;
    }

    /**
     * 获取控制规则
     */
    public Map<String, Object> getRules(String enterpriseId) {
        PageRequest pageRequest = PageRequest.of(0, 100, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<ControlRule> rulePage = controlRuleRepository.findByEnterpriseId(enterpriseId, pageRequest);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("list", rulePage.getContent());
        result.put("total", rulePage.getTotalElements());
        return result;
    }

    /**
     * 创建控制规则
     */
    public ControlRule createRule(ControlRule rule) {
        if (rule.getEnabled() == null) {
            rule.setEnabled(true);
        }
        if (rule.getPriority() == null) {
            rule.setPriority(0);
        }
        return controlRuleRepository.save(rule);
    }

    /**
     * 更新控制规则
     */
    public ControlRule updateRule(String id, Map<String, Object> updates, String enterpriseId) {
        ControlRule rule = controlRuleRepository.findById(id)
                .filter(r -> enterpriseId.equals(r.getEnterpriseId()))
                .orElseThrow(() -> new RuntimeException("规则不存在"));

        if (updates.containsKey("name")) rule.setName((String) updates.get("name"));
        if (updates.containsKey("type")) rule.setType((String) updates.get("type"));
        if (updates.containsKey("action")) rule.setAction((String) updates.get("action"));
        if (updates.containsKey("actionParams")) rule.setActionParams(updates.get("actionParams"));
        if (updates.containsKey("cronExpression")) rule.setCronExpression((String) updates.get("cronExpression"));
        if (updates.containsKey("priority")) rule.setPriority((Integer) updates.get("priority"));
        if (updates.containsKey("enabled")) rule.setEnabled((Boolean) updates.get("enabled"));

        return controlRuleRepository.save(rule);
    }

    /**
     * 删除控制规则
     */
    public void deleteRule(String id, String enterpriseId) {
        controlRuleRepository.findById(id)
                .filter(r -> enterpriseId.equals(r.getEnterpriseId()))
                .orElseThrow(() -> new RuntimeException("规则不存在"));
        controlRuleRepository.deleteById(id);
    }

    /**
     * 启用/禁用规则
     */
    public ControlRule toggleRule(String id, String enterpriseId) {
        ControlRule rule = controlRuleRepository.findById(id)
                .filter(r -> enterpriseId.equals(r.getEnterpriseId()))
                .orElseThrow(() -> new RuntimeException("规则不存在"));
        rule.setEnabled(!rule.getEnabled());
        return controlRuleRepository.save(rule);
    }

    /**
     * 获取控制统计（规则数/启用数/日志数）
     */
    public Map<String, Object> getControlStats(String enterpriseId) {
        PageRequest pageRequest = PageRequest.of(0, 1000, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<ControlRule> rulePage = controlRuleRepository.findByEnterpriseId(enterpriseId, pageRequest);
        List<ControlRule> rules = rulePage.getContent();
        long enabled = rules.stream().filter(ControlRule::getEnabled).count();
        long totalLogs = controlLogRepository.findByEnterpriseId(enterpriseId, PageRequest.of(0, 1)).getTotalElements();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalRules", rules.size());
        result.put("enabledRules", enabled);
        result.put("totalLogs", totalLogs);
        return result;
    }

    /**
     * 管理员获取全部控制规则（不分企业）
     */
    public Map<String, Object> getAllRules() {
        PageRequest pageRequest = PageRequest.of(0, 1000, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<ControlRule> rulePage = controlRuleRepository.findAll(pageRequest);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("list", rulePage.getContent());
        result.put("total", rulePage.getTotalElements());
        return result;
    }

    /**
     * 手动触发规则执行
     */
    public Map<String, Object> triggerRule(String id, String enterpriseId, String userId) {
        ControlRule rule = controlRuleRepository.findById(id)
                .filter(r -> enterpriseId.equals(r.getEnterpriseId()))
                .orElseThrow(() -> new RuntimeException("规则不存在"));

        List<String> targetIds = rule.getTargetIds();
        if (targetIds == null || targetIds.isEmpty()) {
            throw new RuntimeException("规则没有配置目标设备");
        }

        int success = 0;
        int failed = 0;
        for (String deviceId : targetIds) {
            try {
                Map<String, Object> command = new HashMap<>();
                command.put("action", rule.getAction());
                if (rule.getActionParams() != null) {
                    command.put("value", rule.getActionParams());
                }
                sendControlCommand(deviceId, command, enterpriseId, userId);
                success++;
            } catch (Exception e) {
                log.error("规则触发设备控制失败: deviceId={}", deviceId, e);
                failed++;
            }
        }

        // 记录控制日志
        ControlLog controlLog = new ControlLog();
        controlLog.setEnterpriseId(enterpriseId);
        controlLog.setState("trigger_rule:" + rule.getAction());
        controlLog.setStatus(failed == 0 ? "success" : (success > 0 ? "partial" : "failed"));
        controlLog.setTimestamp(Instant.now());
        controlLog.setOperator(userId);
        controlLogRepository.save(controlLog);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", success);
        result.put("failed", failed);
        result.put("ruleName", rule.getName());
        return result;
    }

    // ==================== 协议实现 ====================

    /**
     * Modbus TCP 控制指令
     * 通过 Modbus4J 写入线圈/寄存器来控制设备开关/设定值
     */
    private boolean sendModbusCommand(Device device, String state, Object value) {
        String ip = device.getIpAddress();
        int port = device.getPort() != null ? device.getPort() : 502;
        Map<String, Object> config = device.getConfig();

        // Modbus 寄存器地址
        int writeAddress = config != null && config.containsKey("writeAddress") ?
                ((Number) config.get("writeAddress")).intValue() : 0;
        int slaveId = config != null && config.containsKey("slaveId") ?
                ((Number) config.get("slaveId")).intValue() : 1;
        int writeCount = config != null && config.containsKey("writeCount") ?
                ((Number) config.get("writeCount")).intValue() : 1;
        String writeType = config != null && config.containsKey("writeType") ?
                (String) config.get("writeType") : "coil"; // coil 或 register

        log.info("Modbus控制下发: ip={}, port={}, slaveId={}, address={}, type={}, state={}",
                ip, port, slaveId, writeAddress, writeType, state);

        try {
            // Modbus4J TCP 连接参数
            com.serotonin.modbus4j.ip.IpParameters ipParams =
                    new com.serotonin.modbus4j.ip.IpParameters();
            ipParams.setHost(ip);
            ipParams.setPort(port);
            ipParams.setEncapsulated(false);

            // 创建 Modbus Master
            com.serotonin.modbus4j.ModbusFactory factory = new com.serotonin.modbus4j.ModbusFactory();
            com.serotonin.modbus4j.ModbusMaster master = factory.createTcpMaster(ipParams, false);
            master.setTimeout(3000);
            master.setRetries(2);
            master.init();

            try {
                if ("coil".equals(writeType)) {
                    // 写单个线圈（开关控制）
                    boolean coilValue = "on".equals(state) || "1".equals(String.valueOf(value));
                    com.serotonin.modbus4j.locator.BaseLocator<Boolean> locator =
                            com.serotonin.modbus4j.locator.BaseLocator.coilStatus(slaveId, writeAddress);
                    master.setValue(locator, coilValue);
                    log.info("Modbus线圈写入成功: slaveId={}, address={}, value={}", slaveId, writeAddress, coilValue);
                } else {
                    // 写保持寄存器（设定值控制）
                    short registerValue = value != null ? ((Number) value).shortValue() :
                            ("on".equals(state) ? (short) 1 : (short) 0);
                    com.serotonin.modbus4j.locator.BaseLocator<Number> locator =
                            com.serotonin.modbus4j.locator.BaseLocator.holdingRegister(slaveId, writeAddress, com.serotonin.modbus4j.code.DataType.TWO_BYTE_INT_UNSIGNED);
                    master.setValue(locator, registerValue);
                    log.info("Modbus寄存器写入成功: slaveId={}, address={}, value={}", slaveId, writeAddress, registerValue);
                }
                return true;
            } finally {
                master.destroy();
            }
        } catch (Exception e) {
            log.error("Modbus控制下发失败: ip={}:{}, error={}", ip, port, e.getMessage());
            throw new RuntimeException("Modbus通信失败: " + e.getMessage());
        }
    }

    /**
     * MQTT 控制指令
     * 通过 Eclipse Paho MQTT 客户端发布控制消息
     */
    private boolean sendMqttCommand(Device device, String state, Object value) {
        Map<String, Object> config = device.getConfig();
        String topic = config != null && config.containsKey("controlTopic") ?
                (String) config.get("controlTopic") : "devices/" + device.getId() + "/control";
        int qos = config != null && config.containsKey("mqttQos") ?
                ((Number) config.get("mqttQos")).intValue() : 1;
        boolean retained = config != null && Boolean.TRUE.equals(config.get("mqttRetained"));

        log.info("MQTT控制下发: topic={}, state={}, qos={}", topic, state, qos);

        // 构建控制消息 payload
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("deviceId", device.getId());
        payload.put("action", state);
        if (value != null) {
            payload.put("value", value);
        }
        payload.put("timestamp", Instant.now().toEpochMilli());

        MqttClient mqttClient = null;
        try {
            String clientId = mqttClientId + "-" + device.getId() + "-" + System.currentTimeMillis();
            mqttClient = new MqttClient(mqttBrokerUrl, clientId, new MemoryPersistence());

            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(true);
            options.setConnectionTimeout(5);
            options.setKeepAliveInterval(30);
            if (mqttUsername != null && !mqttUsername.isEmpty()) {
                options.setUserName(mqttUsername);
                options.setPassword(mqttPassword.toCharArray());
            }

            mqttClient.connect(options);

            MqttMessage message = new MqttMessage(objectMapper.writeValueAsBytes(payload));
            message.setQos(qos);
            message.setRetained(retained);
            mqttClient.publish(topic, message);

            log.info("MQTT控制指令发布成功: topic={}, payload={}", topic, payload);
            return true;
        } catch (Exception e) {
            log.error("MQTT控制下发失败: topic={}, error={}", topic, e.getMessage());
            throw new RuntimeException("MQTT发布失败: " + e.getMessage());
        } finally {
            if (mqttClient != null) {
                try {
                    mqttClient.disconnect();
                    mqttClient.close();
                } catch (Exception ignored) {}
            }
        }
    }

    /**
     * HTTP 控制指令
     * 通过 RestTemplate 调用设备 HTTP API 下发控制命令
     */
    private boolean sendHttpCommand(Device device, String state, Object value) {
        String ip = device.getIpAddress();
        int port = device.getPort() != null ? device.getPort() : 80;
        Map<String, Object> config = device.getConfig();
        String controlPath = config != null && config.containsKey("controlPath") ?
                (String) config.get("controlPath") : "/api/control";
        String method = config != null && config.containsKey("httpMethod") ?
                (String) config.get("httpMethod") : "POST";
        String authHeader = config != null && config.containsKey("authHeader") ?
                (String) config.get("authHeader") : null;

        String url = "http://" + ip + ":" + port + controlPath;
        log.info("HTTP控制下发: url={}, method={}, state={}", url, method, state);

        try {
            // 构建请求体
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("deviceId", device.getId());
            body.put("action", state);
            if (value != null) {
                body.put("value", value);
            }
            body.put("timestamp", Instant.now().toEpochMilli());

            // 设置请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (authHeader != null) {
                headers.set("Authorization", authHeader);
            }

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            // 发送请求
            HttpMethod httpMethod = HttpMethod.valueOf(method.toUpperCase());
            ResponseEntity<String> response = restTemplate.exchange(url, httpMethod, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("HTTP控制指令发送成功: url={}, response={}", url, response.getStatusCode());
                return true;
            } else {
                log.warn("HTTP控制返回非2xx: url={}, status={}", url, response.getStatusCode());
                return false;
            }
        } catch (Exception e) {
            log.error("HTTP控制下发失败: url={}, error={}", url, e.getMessage());
            throw new RuntimeException("HTTP请求失败: " + e.getMessage());
        }
    }

    /**
     * 更新设备字段
     */
    private void updateDeviceField(Device device, Map<String, Object> updates) {
        if (updates.containsKey("status")) {
            device.setStatus((String) updates.get("status"));
        }
        device.setUpdatedAt(Instant.now());
        deviceRepository.save(device);
    }

    /**
     * 记录控制日志到 MongoDB（持久化，重启不丢失）
     */
    private void recordControlLog(String deviceId, String state, Object value,
                                   String status, String message, String userId) {
        Device device = deviceRepository.findById(deviceId).orElse(null);
        String enterpriseId = device != null ? device.getEnterpriseId() : "unknown";

        ControlLog logEntry = ControlLog.builder()
                .enterpriseId(enterpriseId)
                .deviceId(deviceId)
                .state(state)
                .value(value)
                .status(status)
                .message(message)
                .operator(userId)
                .timestamp(Instant.now())
                .build();

        controlLogRepository.save(logEntry);
    }
}
