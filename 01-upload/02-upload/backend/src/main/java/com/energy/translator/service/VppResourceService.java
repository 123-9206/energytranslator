package com.energy.translator.service;

import com.energy.translator.dto.PageResponse;
import com.energy.translator.dto.VppResourceDTO;
import com.energy.translator.entity.EnergyRecord;
import com.energy.translator.entity.Enterprise;
import com.energy.translator.entity.VppResource;
import com.energy.translator.repository.EnergyRecordRepository;
import com.energy.translator.repository.EnterpriseRepository;
import com.energy.translator.repository.VppResourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class VppResourceService {

    private final VppResourceRepository resourceRepository;
    private final EnterpriseRepository enterpriseRepository;
    private final EnergyRecordRepository energyRecordRepository;

    @Value("${system.vpp.max-resources-per-enterprise:50}")
    private int maxResourcesPerEnterprise;

    private static final Map<String, String> TYPE_NAMES = Map.of(
            "adjustable_load", "可调负荷",
            "generator", "发电机",
            "storage", "储能设备"
    );

    private static final Map<String, String> STATUS_NAMES = Map.of(
            "pending", "待审核",
            "online", "在线",
            "offline", "离线",
            "maintenance", "维护中"
    );

    public PageResponse<VppResourceDTO> getResources(String enterpriseId, String role, 
                                                      String status, String type, String keyword,
                                                      int page, int pageSize) {
        PageRequest pageRequest = PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        
        Page<VppResource> resources;
        if (Arrays.asList("enterprise_admin", "user").contains(role)) {
            resources = resourceRepository.findByEnterpriseId(enterpriseId, pageRequest);
        } else if (status != null) {
            resources = resourceRepository.findByStatus(status, pageRequest);
        } else {
            resources = resourceRepository.findAll(pageRequest);
        }

        // 获取企业信息
        Set<String> enterpriseIds = resources.getContent().stream()
                .map(VppResource::getEnterpriseId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        
        Map<String, Enterprise> enterpriseMap = new HashMap<>();
        for (String entId : enterpriseIds) {
            enterpriseRepository.findById(entId).ifPresent(e -> enterpriseMap.put(entId, e));
        }

        List<VppResourceDTO> dtos = resources.getContent().stream()
                .map(r -> convertToDTO(r, enterpriseMap.get(r.getEnterpriseId())))
                .collect(Collectors.toList());

        return PageResponse.of(dtos, resources.getTotalElements(), page, pageSize);
    }

    public VppResourceDTO getResourceDetail(String resourceId) {
        VppResource resource = resourceRepository.findById(resourceId)
                .orElseThrow(() -> new RuntimeException("资源不存在"));

        Enterprise enterprise = null;
        if (resource.getEnterpriseId() != null) {
            enterprise = enterpriseRepository.findById(resource.getEnterpriseId()).orElse(null);
        }

        return convertToDTO(resource, enterprise);
    }

    @Transactional
    public String registerResource(VppResource resource, String enterpriseId) {
        Enterprise enterprise = enterpriseRepository.findById(enterpriseId)
                .orElseThrow(() -> new RuntimeException("企业信息不存在"));

        if (!"verified".equals(enterprise.getStatus())) {
            throw new RuntimeException("企业尚未审核通过");
        }

        // 检查资源数量限制
        long currentCount = resourceRepository.countByEnterpriseIdAndStatus(enterpriseId, "online");
        if (currentCount >= maxResourcesPerEnterprise) {
            throw new RuntimeException("企业资源数量已达上限(" + maxResourcesPerEnterprise + ")");
        }

        resource.setEnterpriseId(enterpriseId);
        resource.setStatus("pending");
        resource.setStatistics(VppResource.ResourceStatistics.builder()
                .totalResponseCount(0)
                .completedResponseCount(0)
                .totalAdjustment(0.0)
                .avgResponseRate(0.95)
                .build());

        resource = resourceRepository.save(resource);

        // 更新企业容量
        updateEnterpriseCapacity(enterpriseId);

        return resource.getId();
    }

    @Transactional
    public void updateResource(String resourceId, VppResource updateData, String enterpriseId, String role) {
        VppResource resource = resourceRepository.findById(resourceId)
                .orElseThrow(() -> new RuntimeException("资源不存在"));

        // 权限检查
        if (!"admin".equals(role) && !enterpriseId.equals(resource.getEnterpriseId())) {
            throw new RuntimeException("无权修改该资源");
        }

        // 在线状态不允许直接修改
        if ("online".equals(resource.getStatus()) && updateData.getStatus() != null) {
            throw new RuntimeException("在线资源状态不可直接修改");
        }

        if (updateData.getName() != null) resource.setName(updateData.getName());
        if (updateData.getLocation() != null) resource.setLocation(updateData.getLocation());
        if (updateData.getAdjustableRange() != null) resource.setAdjustableRange(updateData.getAdjustableRange());
        if (updateData.getResponseTime() != null) resource.setResponseTime(updateData.getResponseTime());
        if (updateData.getContinuousTime() != null) resource.setContinuousTime(updateData.getContinuousTime());
        if (updateData.getDeviceInfo() != null) resource.setDeviceInfo(updateData.getDeviceInfo());
        if ("admin".equals(role) && updateData.getStatus() != null) {
            resource.setStatus(updateData.getStatus());
        }

        resourceRepository.save(resource);

        // 更新企业容量
        if (resource.getEnterpriseId() != null) {
            updateEnterpriseCapacity(resource.getEnterpriseId());
        }
    }

    @Transactional
    public void deleteResource(String resourceId, String enterpriseId, String role) {
        VppResource resource = resourceRepository.findById(resourceId)
                .orElseThrow(() -> new RuntimeException("资源不存在"));

        // 权限检查
        if (!"admin".equals(role) && !enterpriseId.equals(resource.getEnterpriseId())) {
            throw new RuntimeException("无权删除该资源");
        }

        if ("online".equals(resource.getStatus())) {
            throw new RuntimeException("在线资源不允许删除，请先下线");
        }

        String entId = resource.getEnterpriseId();
        resourceRepository.delete(resource);

        // 更新企业容量
        if (entId != null) {
            updateEnterpriseCapacity(entId);
        }
    }

    public Map<String, Object> getResourceStatus(String resourceId) {
        VppResource resource = resourceRepository.findById(resourceId)
                .orElseThrow(() -> new RuntimeException("资源不存在"));

        // 从最近能耗数据获取实际当前负荷
        double currentLoad = 0.0;
        if (resource.getEnterpriseId() != null) {
            List<EnergyRecord> recentRecords = energyRecordRepository
                    .findTop100ByEnterpriseIdAndResourceIdOrderByTimestampDesc(resource.getEnterpriseId(), resourceId);
            if (!recentRecords.isEmpty()) {
                EnergyRecord latest = recentRecords.get(0);
                currentLoad = latest.getPower() != null ? latest.getPower() : 
                              (latest.getValue() != null ? latest.getValue() : 0.0);
            }
        }

        Map<String, Object> status = new LinkedHashMap<>();
        status.put("resourceId", resource.getId());
        status.put("name", resource.getName());
        status.put("status", resource.getStatus());
        status.put("ratedPower", resource.getRatedPower());
        status.put("adjustableRange", resource.getAdjustableRange());
        status.put("currentLoad", currentLoad);
        status.put("lastUpdateTime", Instant.now());
        status.put("loadRate", resource.getRatedPower() > 0 ? 
                String.format("%.1f%%", currentLoad / resource.getRatedPower() * 100) : "0%");

        return status;
    }

    public List<Map<String, Object>> batchGetStatus(List<String> resourceIds) {
        List<VppResource> resources = resourceRepository.findAllById(resourceIds);
        
        return resources.stream().map(r -> {
            Map<String, Object> status = new HashMap<>();
            status.put("resourceId", r.getId());
            status.put("name", r.getName());
            status.put("status", r.getStatus());
            status.put("currentLoad", 0.0);
            status.put("lastUpdateTime", Instant.now());
            return status;
        }).collect(Collectors.toList());
    }

    private void updateEnterpriseCapacity(String enterpriseId) {
        List<VppResource> onlineResources = resourceRepository
                .findByEnterpriseIdAndStatus(enterpriseId, "online");

        double totalAdjustable = onlineResources.stream()
                .mapToDouble(r -> (r.getAdjustableRange() != null && r.getAdjustableRange().getMax() != null)
                        ? r.getAdjustableRange().getMax() : 0.0)
                .sum();

        double totalRated = onlineResources.stream()
                .mapToDouble(r -> r.getRatedPower() != null ? r.getRatedPower() : 0.0)
                .sum();

        enterpriseRepository.findById(enterpriseId).ifPresent(enterprise -> {
            if (enterprise.getCapacityInfo() == null) {
                enterprise.setCapacityInfo(new Enterprise.CapacityInfo());
            }
            enterprise.getCapacityInfo().setAdjustableCapacity(totalAdjustable);
            enterprise.getCapacityInfo().setTotalLoad(totalRated);
            enterpriseRepository.save(enterprise);
        });
    }

    private VppResourceDTO convertToDTO(VppResource resource, Enterprise enterprise) {
        return VppResourceDTO.builder()
                .id(resource.getId())
                .name(resource.getName())
                .type(resource.getType())
                .typeName(TYPE_NAMES.getOrDefault(resource.getType(), resource.getType()))
                .location(resource.getLocation())
                .ratedPower(resource.getRatedPower())
                .adjustableMin(resource.getAdjustableRange() != null ? resource.getAdjustableRange().getMin() : null)
                .adjustableMax(resource.getAdjustableRange() != null ? resource.getAdjustableRange().getMax() : null)
                .responseTime(resource.getResponseTime())
                .continuousTime(resource.getContinuousTime())
                .status(resource.getStatus())
                .statusName(STATUS_NAMES.getOrDefault(resource.getStatus(), resource.getStatus()))
                .manufacturer(resource.getDeviceInfo() != null ? resource.getDeviceInfo().getManufacturer() : null)
                .model(resource.getDeviceInfo() != null ? resource.getDeviceInfo().getModel() : null)
                .protocol(resource.getDeviceInfo() != null ? resource.getDeviceInfo().getProtocol() : null)
                .totalResponseCount(resource.getStatistics() != null ? resource.getStatistics().getTotalResponseCount() : 0)
                .completedResponseCount(resource.getStatistics() != null ? resource.getStatistics().getCompletedResponseCount() : 0)
                .avgResponseRate(resource.getStatistics() != null ? resource.getStatistics().getAvgResponseRate() : 0.0)
                .enterpriseName(enterprise != null ? enterprise.getName() : null)
                .createdAt(resource.getCreatedAt() != null ? resource.getCreatedAt().toString() : null)
                .build();
    }
}
