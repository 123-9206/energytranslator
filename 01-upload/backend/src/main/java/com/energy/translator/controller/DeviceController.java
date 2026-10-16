package com.energy.translator.controller;

import com.energy.translator.dto.ApiResponse;
import com.energy.translator.entity.Device;
import com.energy.translator.entity.EnergyRecord;
import com.energy.translator.repository.DeviceRepository;
import com.energy.translator.repository.EnergyRecordRepository;
import com.energy.translator.security.UserPrincipal;
import com.energy.translator.service.ControlService;
import com.energy.translator.service.DeviceCollectService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceRepository deviceRepository;
    private final EnergyRecordRepository energyRecordRepository;
    private final ControlService controlService;
    private final DeviceCollectService deviceCollectService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<Device>>> getDevices(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String siteId,
            @RequestParam(required = false) String status,
            @AuthenticationPrincipal UserPrincipal principal) {

        String enterpriseId = principal.getEnterpriseId();
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<Device> devices;
        if (siteId != null) {
            devices = deviceRepository.findBySiteId(siteId, pageRequest);
        } else {
            devices = deviceRepository.findByEnterpriseId(enterpriseId, pageRequest);
        }

        return ResponseEntity.ok(ApiResponse.success(devices));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Device>> getDevice(
            @PathVariable String id,
            @AuthenticationPrincipal UserPrincipal principal) {

        Device device = deviceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("设备不存在"));
        return ResponseEntity.ok(ApiResponse.success(device));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Device>> createDevice(
            @RequestBody Device device,
            @AuthenticationPrincipal UserPrincipal principal) {

        device.setEnterpriseId(principal.getEnterpriseId());
        Device saved = deviceRepository.save(device);
        return ResponseEntity.ok(ApiResponse.success(saved));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Device>> updateDevice(
            @PathVariable String id,
            @RequestBody Map<String, Object> updates,
            @AuthenticationPrincipal UserPrincipal principal) {

        Device device = deviceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("设备不存在"));

        if (updates.containsKey("name")) {
            device.setName((String) updates.get("name"));
        }
        if (updates.containsKey("type")) {
            device.setType((String) updates.get("type"));
        }
        if (updates.containsKey("model")) {
            device.setModel((String) updates.get("model"));
        }
        if (updates.containsKey("manufacturer")) {
            device.setManufacturer((String) updates.get("manufacturer"));
        }
        if (updates.containsKey("serialNumber")) {
            device.setSerialNumber((String) updates.get("serialNumber"));
        }
        if (updates.containsKey("status")) {
            device.setStatus((String) updates.get("status"));
        }
        if (updates.containsKey("protocol")) {
            device.setProtocol((String) updates.get("protocol"));
        }
        if (updates.containsKey("ipAddress")) {
            device.setIpAddress((String) updates.get("ipAddress"));
        }
        if (updates.containsKey("port")) {
            device.setPort(((Number) updates.get("port")).intValue());
        }
        if (updates.containsKey("config")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> config = (Map<String, Object>) updates.get("config");
            device.setConfig(config);
        }
        if (updates.containsKey("siteId")) {
            device.setSiteId((String) updates.get("siteId"));
        }

        Device saved = deviceRepository.save(device);
        return ResponseEntity.ok(ApiResponse.success(saved));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Map<String, String>>> deleteDevice(
            @PathVariable String id,
            @AuthenticationPrincipal UserPrincipal principal) {

        deviceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("设备不存在"));
        deviceRepository.deleteById(id);
        return ResponseEntity.ok(ApiResponse.success(Map.of("message", "设备已删除")));
    }

    @GetMapping("/status-summary")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStatusSummary(
            @AuthenticationPrincipal UserPrincipal principal) {

        String enterpriseId = principal.getEnterpriseId();
        long total = deviceRepository.countByEnterpriseId(enterpriseId);
        long online = deviceRepository.findByEnterpriseIdAndStatus(enterpriseId, "online").size();
        long offline = deviceRepository.findByEnterpriseIdAndStatus(enterpriseId, "offline").size();
        long maintenance = deviceRepository.findByEnterpriseIdAndStatus(enterpriseId, "maintenance").size();

        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "total", total,
                "online", online,
                "offline", offline,
                "maintenance", maintenance
        )));
    }

    /**
     * 接收设备数据上传（HTTP协议设备主动上报）
     */
    @PostMapping("/{id}/data")
    public ResponseEntity<ApiResponse<Map<String, String>>> receiveDeviceData(
            @PathVariable String id,
            @RequestBody Map<String, Object> data,
            @AuthenticationPrincipal UserPrincipal principal) {

        Device device = deviceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("设备不存在"));

        // 保存能耗记录
        EnergyRecord record = EnergyRecord.builder()
                .enterpriseId(device.getEnterpriseId())
                .resourceId(device.getId())
                .siteId(device.getSiteId())
                .timestamp(Instant.now())
                .value(data.containsKey("value") ? ((Number) data.get("value")).doubleValue() : null)
                .power(data.containsKey("power") ? ((Number) data.get("power")).doubleValue() : null)
                .unit("kWh")
                .source("device")
                .build();

        energyRecordRepository.save(record);

        // 更新设备统计
        Device.DeviceStatistics stats = device.getStatistics();
        if (stats == null) {
            stats = new Device.DeviceStatistics();
        }
        stats.setTotalDataPoints(stats.getTotalDataPoints() != null ? stats.getTotalDataPoints() + 1 : 1L);
        if (record.getValue() != null) {
            stats.setTotalEnergy(stats.getTotalEnergy() != null ? stats.getTotalEnergy() + record.getValue() : record.getValue());
        }
        stats.setLastDataTime(Instant.now());
        device.setStatistics(stats);

        // 更新设备在线状态
        device.setStatus("online");
        deviceRepository.save(device);

        return ResponseEntity.ok(ApiResponse.success(Map.of("message", "数据接收成功")));
    }

    /**
     * 设备控制指令（便捷端点，内部转发给 ControlService）
     */
    @PostMapping("/{id}/control")
    public ResponseEntity<ApiResponse<Map<String, Object>>> controlDevice(
            @PathVariable String id,
            @RequestBody Map<String, Object> command,
            @AuthenticationPrincipal UserPrincipal principal) {

        Map<String, Object> result = controlService.sendControlCommand(
                id, command, principal.getEnterpriseId(), principal.getUserId());
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * 获取设备统计数据
     */
    @GetMapping("/{id}/statistics")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getDeviceStatistics(
            @PathVariable String id,
            @RequestParam(defaultValue = "day") String period,
            @AuthenticationPrincipal UserPrincipal principal) {

        Device device = deviceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("设备不存在"));

        // 计算时间段
        LocalDate now = LocalDate.now();
        Instant start, end;
        switch (period) {
            case "today":
            case "day":
                start = now.atStartOfDay(ZoneId.systemDefault()).toInstant();
                end = now.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
                break;
            case "week":
                start = now.minusWeeks(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
                end = now.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
                break;
            case "month":
                start = now.withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
                end = now.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
                break;
            default:
                start = now.minusDays(7).atStartOfDay(ZoneId.systemDefault()).toInstant();
                end = now.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
        }

        List<EnergyRecord> records = energyRecordRepository.findByTimeRange(
                device.getEnterpriseId(), start, end);

        // 过滤该设备的记录
        records = records.stream()
                .filter(r -> id.equals(r.getResourceId()))
                .toList();

        double totalEnergy = records.stream().mapToDouble(r -> r.getValue() != null ? r.getValue() : 0).sum();
        double avgPower = records.stream()
                .filter(r -> r.getPower() != null)
                .mapToDouble(EnergyRecord::getPower)
                .average()
                .orElse(0);

        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "deviceId", id,
                "deviceName", device.getName() != null ? device.getName() : "",
                "period", period,
                "totalEnergy", totalEnergy,
                "avgPower", avgPower,
                "dataPointCount", records.size(),
                "status", device.getStatus() != null ? device.getStatus() : "unknown",
                "statistics", device.getStatistics() != null ? device.getStatistics() : new Device.DeviceStatistics()
        )));
    }

    /**
     * 手动触发设备数据采集
     */
    @PostMapping("/{id}/collect")
    public ResponseEntity<ApiResponse<Map<String, String>>> manualCollect(
            @PathVariable String id,
            @AuthenticationPrincipal UserPrincipal principal) {

        Device device = deviceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("设备不存在"));

        try {
            deviceCollectService.manualCollect(id, device.getEnterpriseId());
            return ResponseEntity.ok(ApiResponse.success(Map.of("message", "采集指令已下发")));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.error("采集失败: " + e.getMessage()));
        }
    }
}
