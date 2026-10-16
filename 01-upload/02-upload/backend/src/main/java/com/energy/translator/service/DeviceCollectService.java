package com.energy.translator.service;

import com.energy.translator.entity.Device;
import com.energy.translator.entity.EnergyRecord;
import com.energy.translator.repository.DeviceRepository;
import com.energy.translator.repository.EnergyRecordRepository;
import com.energy.translator.util.IpSecurityUtil;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/**
 * 设备数据采集服务
 * 支持 Modbus TCP、MQTT、HTTP 三种协议的数据采集
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceCollectService {

    private final DeviceRepository deviceRepository;
    private final EnergyRecordRepository energyRecordRepository;

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${collect.modbus.enabled:true}")
    private boolean modbusEnabled;

    @Value("${collect.mqtt.enabled:true}")
    private boolean mqttEnabled;

    @Value("${collect.http.enabled:true}")
    private boolean httpEnabled;

    @Value("${collect.thread-pool-size:10}")
    private int threadPoolSize;

    @Value("${collect.default-interval-seconds:60}")
    private int defaultInterval;

    @Value("${collect.modbus.timeout-ms:3000}")
    private int modbusTimeout;

    @Value("${collect.http.timeout-ms:5000}")
    private int httpTimeout;

    private ScheduledExecutorService executorService;

    @PostConstruct
    public void init() {
        executorService = Executors.newScheduledThreadPool(threadPoolSize);
        log.info("设备采集服务初始化完成, 线程池大小: {}", threadPoolSize);
        log.info("协议配置 - Modbus: {}, MQTT: {}, HTTP: {}",
                modbusEnabled, mqttEnabled, httpEnabled);
    }

    /**
     * 定时采集所有在线设备数据（每分钟执行一次）
     * 实际采集间隔由设备的 config.intervalSeconds 控制
     */
    @Scheduled(fixedDelayString = "${collect.poll-interval:60000}", initialDelay = 10000)
    public void collectAll() {
        try {
            List<Device> devices = deviceRepository.findAll();
            List<Device> onlineDevices = devices.stream()
                    .filter(d -> "online".equals(d.getStatus()))
                    .toList();

            if (onlineDevices.isEmpty()) {
                return;
            }

            log.debug("开始采集 {} 台在线设备数据", onlineDevices.size());

            for (Device device : onlineDevices) {
                try {
                    collectDevice(device);
                } catch (Exception e) {
                    log.error("设备 {} 采集失败: {}", device.getName(), e.getMessage());
                    handleCollectError(device, e);
                }
            }
        } catch (Exception e) {
            log.error("批量采集异常: {}", e.getMessage());
        }
    }

    /**
     * 采集单个设备数据
     */
    public Map<String, Object> collectDevice(Device device) {
        String protocol = device.getProtocol();
        Map<String, Object> config = device.getConfig();
        int interval = (config != null && config.containsKey("intervalSeconds")) ?
                ((Number) config.get("intervalSeconds")).intValue() : defaultInterval;

        // 检查是否需要采集（防止频繁采集）
        Device.DeviceStatistics stats = device.getStatistics();
        if (stats != null && stats.getLastDataTime() != null) {
            long secondsSinceLast = Instant.now().getEpochSecond() - stats.getLastDataTime().getEpochSecond();
            if (secondsSinceLast < interval) {
                return Map.of("skipped", true, "reason", "未到采集时间",
                        "nextCollectIn", interval - secondsSinceLast);
            }
        }

        double value = 0, power = 0, current = 0, voltage = 0, temperature = Double.NaN, powerFactor = 0;
        boolean success = false;

        try {
            switch (protocol != null ? protocol : "http") {
                case "modbus" -> {
                    if (modbusEnabled) {
                        Map<String, Object> result = collectModbus(device);
                        value = num(result, "value");
                        power = num(result, "power");
                        voltage = num(result, "voltage");
                        current = num(result, "current");
                        temperature = result.containsKey("temperature") ? num(result, "temperature") : Double.NaN;
                        success = true;
                    }
                }
                case "mqtt" -> {
                    if (mqttEnabled) {
                        return Map.of("skipped", true, "reason", "MQTT使用被动接收模式");
                    }
                }
                case "http" -> {
                    if (httpEnabled) {
                        Map<String, Object> result = collectHttp(device);
                        value = num(result, "value");
                        power = num(result, "power");
                        voltage = num(result, "voltage");
                        current = num(result, "current");
                        temperature = result.containsKey("temperature") ? num(result, "temperature") : Double.NaN;
                        powerFactor = num(result, "powerFactor");
                        success = true;
                    }
                }
                case "bacnet" -> {
                    Map<String, Object> result = collectBacnet(device);
                    value = num(result, "value");
                    power = num(result, "power");
                    success = true;
                }
                default -> log.warn("未知协议: {}，跳过设备 {}", protocol, device.getName());
            }
        } catch (Exception e) {
            log.error("设备 {} ({}) 采集异常: {}", device.getName(), protocol, e.getMessage());
            handleCollectError(device, e);
            return Map.of("success", false, "error", e.getMessage());
        }

        // 保存采集数据为能耗记录
        if (success && (value > 0 || power > 0)) {
            saveDeviceData(device, value, power, current, voltage, temperature, powerFactor);
            updateDeviceStatistics(device, value > 0 ? value : power);

            log.debug("设备 {} 采集成功: value={}kWh, power={}kW, V={}V, I={}A, T={}°C",
                    device.getName(), value, power, voltage, current, temperature);
        }

        return Map.of("success", success, "value", value, "power", power,
                "voltage", voltage, "current", current,
                "timestamp", Instant.now().toString());
    }

    /** 安全从 Map 取数值 */
    private double num(Map<String, Object> map, String key) {
        Object v = map.getOrDefault(key, 0);
        return v instanceof Number ? ((Number) v).doubleValue() : 0;
    }

    /**
     * Modbus TCP 数据采集
     * 使用 Modbus4J 读取设备的保持寄存器获取能耗/功率数据
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private Map<String, Object> collectModbus(Device device) {
        String ip = device.getIpAddress();

        // SSRF 防护：禁止向内网地址发起采集请求
        IpSecurityUtil.requireAllowedTargetIp(ip, "Modbus采集");

        int port = device.getPort() != null ? device.getPort() : 502;
        Map<String, Object> config = device.getConfig();

        // Modbus 参数
        int slaveId = config != null && config.containsKey("slaveId") ?
                ((Number) config.get("slaveId")).intValue() : 1;
        int energyAddress = config != null && config.containsKey("energyAddress") ?
                ((Number) config.get("energyAddress")).intValue() : 0;
        int powerAddress = config != null && config.containsKey("powerAddress") ?
                ((Number) config.get("powerAddress")).intValue() : 2;
        int voltageAddress = config != null && config.containsKey("voltageAddress") ?
                ((Number) config.get("voltageAddress")).intValue() : 8;
        int currentAddress = config != null && config.containsKey("currentAddress") ?
                ((Number) config.get("currentAddress")).intValue() : 12;
        String dataType = config != null && config.containsKey("dataType") ?
                (String) config.get("dataType") : "FLOAT"; // INT16, UINT16, INT32, FLOAT

        long startTime = System.currentTimeMillis();

        log.debug("Modbus采集: device={}, ip={}:{}, slaveId={}, energyAddr={}, powerAddr={}",
                device.getName(), ip, port, slaveId, energyAddress, powerAddress);

        com.serotonin.modbus4j.ModbusMaster master = null;
        try {
            // TCP 连接参数
            com.serotonin.modbus4j.ip.IpParameters ipParams =
                    new com.serotonin.modbus4j.ip.IpParameters();
            ipParams.setHost(ip);
            ipParams.setPort(port);
            ipParams.setEncapsulated(false);

            // 创建 Modbus Master
            com.serotonin.modbus4j.ModbusFactory factory = new com.serotonin.modbus4j.ModbusFactory();
            master = factory.createTcpMaster(ipParams, false);
            master.setTimeout(modbusTimeout);
            master.setRetries(2);
            master.init();

            // 构建批量读取
            com.serotonin.modbus4j.BatchRead batch = new com.serotonin.modbus4j.BatchRead();
            com.serotonin.modbus4j.locator.BaseLocator<Number> energyLocator =
                    com.serotonin.modbus4j.locator.BaseLocator.holdingRegister(
                            slaveId, energyAddress, getModbusDataType(dataType));
            batch.addLocator("energy", energyLocator);

            com.serotonin.modbus4j.locator.BaseLocator<Number> powerLocator =
                    com.serotonin.modbus4j.locator.BaseLocator.holdingRegister(
                            slaveId, powerAddress, getModbusDataType(dataType));
            batch.addLocator("power", powerLocator);

            com.serotonin.modbus4j.locator.BaseLocator<Number> voltageLocator =
                    com.serotonin.modbus4j.locator.BaseLocator.holdingRegister(
                            slaveId, voltageAddress, getModbusDataType(dataType));
            batch.addLocator("voltage", voltageLocator);

            com.serotonin.modbus4j.locator.BaseLocator<Number> currentLocator =
                    com.serotonin.modbus4j.locator.BaseLocator.holdingRegister(
                            slaveId, currentAddress, getModbusDataType(dataType));
            batch.addLocator("current", currentLocator);

            com.serotonin.modbus4j.BatchResults results = master.send(batch);

            double energyValue = ((Number) results.getValue("energy")).doubleValue();
            double powerValue = ((Number) results.getValue("power")).doubleValue();
            double voltageValue = ((Number) results.getValue("voltage")).doubleValue();
            double currentValue = ((Number) results.getValue("current")).doubleValue();

            long elapsed = System.currentTimeMillis() - startTime;

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("value", energyValue);
            result.put("power", powerValue);
            result.put("voltage", voltageValue);
            result.put("current", currentValue);
            result.put("responseTime", elapsed);
            result.put("success", true);
            log.info("Modbus采集成功: device={}, energy={}kWh, power={}kW, voltage={}V, current={}A,耗时={}ms",
                    device.getName(), energyValue, powerValue, voltageValue, currentValue, elapsed);
            return result;

        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.error("Modbus采集失败: device={}, ip={}:{}, error={},耗时={}ms",
                    device.getName(), ip, port, e.getMessage(), elapsed);
            throw new RuntimeException("Modbus采集失败: " + e.getMessage());
        } finally {
            if (master != null) {
                try {
                    master.destroy();
                } catch (Exception ignored) {}
            }
        }
    }

    /**
     * 根据字符串数据类型映射为 Modbus4J DataType 常量
     * modbus4j 3.x 中 DataType 在 com.serotonin.modbus4j.code 包中，为 int 常量接口
     */
    private int getModbusDataType(String dataType) {
        return switch (dataType != null ? dataType.toUpperCase() : "FLOAT") {
            case "INT16" -> com.serotonin.modbus4j.code.DataType.TWO_BYTE_INT_SIGNED;
            case "UINT16" -> com.serotonin.modbus4j.code.DataType.TWO_BYTE_INT_UNSIGNED;
            case "INT32" -> com.serotonin.modbus4j.code.DataType.FOUR_BYTE_INT_SIGNED;
            case "UINT32" -> com.serotonin.modbus4j.code.DataType.FOUR_BYTE_INT_UNSIGNED;
            default -> com.serotonin.modbus4j.code.DataType.FOUR_BYTE_FLOAT_SWAPPED; // Modbus 通常使用 AB-CD 字节序
        };
    }

    /**
     * HTTP 轮询数据采集
     * 通过 RestTemplate 请求设备 REST API 获取能耗数据
     */
    private Map<String, Object> collectHttp(Device device) {
        String ip = device.getIpAddress();
        int port = device.getPort() != null ? device.getPort() : 80;
        Map<String, Object> config = device.getConfig();

        // HTTP 采集参数
        String dataPath = config != null && config.containsKey("dataPath") ?
                (String) config.get("dataPath") : "/api/energy/current";
        String method = config != null && config.containsKey("httpMethod") ?
                (String) config.get("httpMethod") : "GET";
        String authHeader = config != null && config.containsKey("authHeader") ?
                (String) config.get("authHeader") : null;
        String energyField = config != null && config.containsKey("energyField") ?
                (String) config.get("energyField") : "energy";
        String powerField = config != null && config.containsKey("powerField") ?
                (String) config.get("powerField") : "power";
        String voltageField = config != null && config.containsKey("voltageField") ?
                (String) config.get("voltageField") : "voltage";
        String currentField = config != null && config.containsKey("currentField") ?
                (String) config.get("currentField") : "current";
        String temperatureField = config != null && config.containsKey("temperatureField") ?
                (String) config.get("temperatureField") : "temperature";

        String url = "http://" + ip + ":" + port + dataPath;
        long startTime = System.currentTimeMillis();

        log.debug("HTTP采集: device={}, url={}, method={}", device.getName(), url, method);

        try {
            // 设置请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Accept", MediaType.APPLICATION_JSON_VALUE);
            if (authHeader != null) {
                headers.set("Authorization", authHeader);
            }

            HttpEntity<Void> entity = new HttpEntity<>(headers);

            // 发送请求
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.valueOf(method.toUpperCase()), entity, Map.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new RuntimeException("设备返回非成功状态: " + response.getStatusCode());
            }

            Map<String, Object> body = response.getBody();
            // 从响应中提取能耗和功率值
            double energyValue = extractNumericValue(body, energyField);
            double powerValue = extractNumericValue(body, powerField);
            double voltageValue = extractNumericValue(body, voltageField);
            double currentValue = extractNumericValue(body, currentField);
            double temperatureValue = extractNumericValue(body, temperatureField);

            long elapsed = System.currentTimeMillis() - startTime;

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("value", energyValue);
            result.put("power", powerValue);
            result.put("voltage", voltageValue);
            result.put("current", currentValue);
            result.put("temperature", temperatureValue);
            result.put("responseTime", elapsed);
            result.put("rawData", body);
            result.put("success", true);

            log.info("HTTP采集成功: device={}, energy={}kWh, power={}kW, voltage={}V, current={}A, temp={}°C,耗时={}ms",
                    device.getName(), energyValue, powerValue, voltageValue, currentValue, temperatureValue, elapsed);
            return result;

        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.error("HTTP采集失败: device={}, url={}, error={},耗时={}ms",
                    device.getName(), url, e.getMessage(), elapsed);
            throw new RuntimeException("HTTP采集失败: " + e.getMessage());
        }
    }

    /**
     * 从 Map 中按点分隔路径提取数值
     * 支持 "data.energy" 这种嵌套路径
     */
    private double extractNumericValue(Map<String, Object> data, String fieldPath) {
        if (fieldPath == null || fieldPath.isEmpty()) return 0;

        String[] parts = fieldPath.split("\\.");
        Object current = data;

        for (String part : parts) {
            if (current instanceof Map) {
                current = ((Map<?, ?>) current).get(part);
            } else {
                return 0;
            }
        }

        if (current instanceof Number) {
            return ((Number) current).doubleValue();
        } else if (current instanceof String) {
            try {
                return Double.parseDouble((String) current);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    /**
     * BACnet 数据采集（预留，需集成 BACnet4J 或类似库）
     * 当前不支持 BACnet 协议，抛出明确异常而非返回零值
     */
    private Map<String, Object> collectBacnet(Device device) {
        log.error("设备 {} ({}) 使用 BACnet 协议，但后端尚未集成 BACnet4J，采集失败", device.getName(), device.getId());
        throw new UnsupportedOperationException(
                "BACnet 协议暂未支持，设备 " + device.getName() + " 需切换到 Modbus/MQTT/HTTP 协议");
    }

    /**
     * 保存设备采集数据为能耗记录
     */
    private void saveDeviceData(Device device, double value, double power,
                                 double current, double voltage, double temperature,
                                 double powerFactor) {
        EnergyRecord record = EnergyRecord.builder()
                .enterpriseId(device.getEnterpriseId())
                .resourceId(device.getId())
                .siteId(device.getSiteId())
                .timestamp(Instant.now())
                .value(value)
                .power(power)
                .current(current > 0 ? current : null)
                .voltage(voltage > 0 ? voltage : null)
                .temperature(temperature > -273 ? temperature : null)
                .powerFactor(powerFactor > 0 ? powerFactor : null)
                .carbonEmission(value > 0 ? value * 0.785 : null)
                .unit("kWh")
                .source("device")
                .build();

        energyRecordRepository.save(record);
    }

    /**
     * 保存设备采集数据为能耗记录（兼容旧调用，不含电气参数）
     */
    private void saveDeviceData(Device device, double value, double power) {
        saveDeviceData(device, value, power, 0, 0, Double.NaN, 0);
    }

    /**
     * 更新设备统计信息
     */
    private void updateDeviceStatistics(Device device, double latestValue) {
        Device.DeviceStatistics stats = device.getStatistics();
        if (stats == null) {
            stats = Device.DeviceStatistics.builder().build();
        }

        long totalPoints = (stats.getTotalDataPoints() != null ? stats.getTotalDataPoints() : 0) + 1;
        double totalEnergy = (stats.getTotalEnergy() != null ? stats.getTotalEnergy() : 0) + latestValue;

        device.setStatistics(Device.DeviceStatistics.builder()
                .totalDataPoints(totalPoints)
                .totalEnergy(totalEnergy)
                .lastDataTime(Instant.now())
                .avgResponseTime(stats.getAvgResponseTime())
                .build());

        deviceRepository.save(device);
    }

    /**
     * 处理采集错误
     */
    private void handleCollectError(Device device, Exception e) {
        int failCount = 0;
        Map<String, Object> config = device.getConfig();
        if (config != null && config.containsKey("failCount")) {
            failCount = ((Number) config.get("failCount")).intValue();
        }
        failCount++;

        // 连续3次采集失败，标记设备为离线
        if (failCount >= 3) {
            device.setStatus("offline");
            log.warn("设备 {} 连续 {} 次采集失败，标记为离线", device.getName(), failCount);
        }

        // 保存失败次数
        if (config == null) {
            config = new HashMap<>();
        }
        config.put("failCount", failCount);
        config.put("lastError", e.getMessage());
        config.put("lastErrorTime", Instant.now().toString());
        device.setConfig(config);
        deviceRepository.save(device);
    }

    /**
     * 接收 MQTT 设备上报的数据（由 MqttDataSubscriber 调用）
     * MQTT 是被动接收模式：设备通过 MQTT 发布数据到此服务
     */
    public void onMqttDataReceived(String deviceId, Map<String, Object> payload) {
        try {
            Device device = deviceRepository.findById(deviceId).orElse(null);
            if (device == null) {
                log.warn("MQTT数据: 设备 {} 不存在", deviceId);
                return;
            }

            double value = payload.containsKey("energy") ?
                    ((Number) payload.get("energy")).doubleValue() : 0;
            double power = payload.containsKey("power") ?
                    ((Number) payload.get("power")).doubleValue() : 0;
            double current = payload.containsKey("current") ?
                    ((Number) payload.get("current")).doubleValue() : 0;
            double voltage = payload.containsKey("voltage") ?
                    ((Number) payload.get("voltage")).doubleValue() : 0;
            double temperature = payload.containsKey("temperature") ?
                    ((Number) payload.get("temperature")).doubleValue() : 0;
            double powerFactor = payload.containsKey("powerFactor") ?
                    ((Number) payload.get("powerFactor")).doubleValue() : 0;

            // 支持 cumulativeEnergy 作为能耗值
            if (value == 0 && payload.containsKey("cumulativeEnergy")) {
                value = ((Number) payload.get("cumulativeEnergy")).doubleValue();
            }

            // 设备上报电源状态（on/off/idle）：回写 device.state（R2）
            if (payload.containsKey("state")) {
                Object st = payload.get("state");
                String stateVal = st != null ? st.toString() : null;
                if (stateVal != null && ("on".equals(stateVal) || "off".equals(stateVal) || "idle".equals(stateVal))) {
                    if (!stateVal.equals(device.getState())) {
                        device.setState(stateVal);
                        deviceRepository.save(device);
                    }
                }
            }

            if (value > 0 || power > 0) {
                saveDeviceData(device, value, power, current, voltage, temperature, powerFactor);
                updateDeviceStatistics(device, value > 0 ? value : power);
                log.debug("MQTT数据接收: device={}, energy={}kWh, power={}kW, voltage={}V, current={}A, temp={}°C",
                        device.getName(), value, power, voltage, current, temperature);
            }
        } catch (Exception e) {
            log.error("处理MQTT数据失败: deviceId={}, error={}", deviceId, e.getMessage());
        }
    }

    /**
     * 手动触发采集单个设备
     */
    public Map<String, Object> manualCollect(String deviceId, String enterpriseId) {
        Device device = deviceRepository.findById(deviceId)
                .filter(d -> enterpriseId.equals(d.getEnterpriseId()))
                .orElseThrow(() -> new RuntimeException("设备不存在"));
        return collectDevice(device);
    }
}
