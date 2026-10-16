package com.energy.translator.service;

import com.energy.translator.entity.Device;
import com.energy.translator.repository.DeviceRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MQTT 数据订阅器
 * 订阅设备上报的能耗数据，并通过 DeviceCollectService 保存到数据库。
 * 
 * 支持通配符主题：
 *   - energy/device/+/data   : 设备数据上报
 *   - energy/device/+/status  : 设备状态上报
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MqttDataSubscriber {

    private final DeviceCollectService deviceCollectService;

    @Value("${mqtt.broker-url:tcp://localhost:1883}")
    private String brokerUrl;

    @Value("${mqtt.client-id:energy-translator-subscriber}")
    private String clientId;

    @Value("${mqtt.username:}")
    private String username;

    @Value("${mqtt.password:}")
    private String password;

    @Value("${collect.mqtt.enabled:true}")
    private boolean mqttEnabled;

    private MqttClient mqttClient;
    private volatile boolean connected = false;

    @PostConstruct
    public void init() {
        if (!mqttEnabled) {
            log.info("MQTT 数据订阅已禁用");
            return;
        }

        try {
            String uniqueClientId = clientId + "-" + System.currentTimeMillis();
            mqttClient = new MqttClient(brokerUrl, uniqueClientId, new MemoryPersistence());

            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            options.setConnectionTimeout(10);
            options.setKeepAliveInterval(60);
            options.setAutomaticReconnect(true);
            options.setMaxReconnectDelay(30000);

            if (username != null && !username.isEmpty()) {
                options.setUserName(username);
                if (password != null && !password.isEmpty()) {
                    options.setPassword(password.toCharArray());
                }
            }

            mqttClient.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                    connected = false;
                    log.warn("MQTT 连接断开: {}", cause.getMessage());
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    handleMessage(topic, message);
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                    // 订阅端不需要处理
                }
            });

            mqttClient.connect(options);
            connected = true;

            // 订阅设备数据和状态主题
            String[] topics = {
                "energy/device/+/data",
                "energy/device/+/status",
                "energy/device/+/power",
                "energy/device/+/energy"
            };
            int[] qos = {1, 1, 1, 1};
            mqttClient.subscribe(topics, qos);

            log.info("MQTT 数据订阅器启动成功: broker={}, clientId={}, topics={}", 
                    brokerUrl, uniqueClientId, String.join(",", topics));

        } catch (Exception e) {
            log.error("MQTT 数据订阅器启动失败 (broker={}): {}. 设备数据采集将通过定时轮询方式继续。", 
                    brokerUrl, e.getMessage());
            connected = false;
        }
    }

    /**
     * 处理接收到的 MQTT 消息
     */
    private void handleMessage(String topic, MqttMessage message) {
        try {
            String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
            log.debug("MQTT消息: topic={}, payload={}", topic, payload);

            // 从 topic 中提取设备ID: energy/device/{deviceId}/data
            String[] parts = topic.split("/");
            if (parts.length < 4) {
                log.warn("无法解析MQTT topic格式: {}", topic);
                return;
            }
            String deviceId = parts[2];

            // 解析 payload 为 Map
            Map<String, Object> data = parsePayload(payload);
            if (data == null) return;

            // 转发给 DeviceCollectService 处理
            deviceCollectService.onMqttDataReceived(deviceId, data);

        } catch (Exception e) {
            log.error("处理MQTT消息失败: topic={}, error={}", topic, e.getMessage());
        }
    }

    /**
     * 简单 JSON 解析（避免引入额外依赖的不弹窗解析）
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parsePayload(String payload) {
        try {
            // 使用简单的手工解析处理常见格式
            Map<String, Object> result = new ConcurrentHashMap<>();

            // 尝试 JSON 格式 {"energy": 123.5, "power": 15.2}
            if (payload.trim().startsWith("{")) {
                String content = payload.trim();
                content = content.substring(1, content.length() - 1);
                String[] pairs = content.split(",");
                for (String pair : pairs) {
                    String[] kv = pair.split(":", 2);
                    if (kv.length == 2) {
                        String key = kv[0].trim().replaceAll("\"", "");
                        String value = kv[1].trim().replaceAll("\"", "");
                        try {
                            result.put(key, Double.parseDouble(value));
                        } catch (NumberFormatException e) {
                            result.put(key, value);
                        }
                    }
                }
            } else {
                // 纯数值格式，当作 energy 值
                try {
                    result.put("energy", Double.parseDouble(payload.trim()));
                } catch (NumberFormatException e) {
                    result.put("raw", payload.trim());
                }
            }

            return result;
        } catch (Exception e) {
            log.warn("MQTT消息解析失败: payload={}, error={}", payload, e.getMessage());
            return null;
        }
    }

    /**
     * 检查 MQTT 连接状态
     */
    public boolean isConnected() {
        return connected && mqttClient != null && mqttClient.isConnected();
    }

    /**
     * 发布消息到指定主题
     */
    public void publish(String topic, String payload, int qos, boolean retained) {
        if (!isConnected()) {
            log.warn("MQTT未连接，无法发布消息: topic={}", topic);
            return;
        }
        try {
            MqttMessage msg = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
            msg.setQos(qos);
            msg.setRetained(retained);
            mqttClient.publish(topic, msg);
            log.debug("MQTT发布成功: topic={}", topic);
        } catch (Exception e) {
            log.error("MQTT发布失败: topic={}, error={}", topic, e.getMessage());
        }
    }

    @PreDestroy
    public void destroy() {
        try {
            if (mqttClient != null && mqttClient.isConnected()) {
                mqttClient.disconnect();
                mqttClient.close();
                log.info("MQTT 数据订阅器已关闭");
            }
        } catch (Exception e) {
            log.error("MQTT 关闭异常: {}", e.getMessage());
        }
    }
}
