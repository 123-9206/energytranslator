package com.energy.translator.service;

import com.energy.translator.dto.PageResponse;
import com.energy.translator.dto.VppActivityDTO;
import com.energy.translator.entity.Enterprise;
import com.energy.translator.entity.VppActivity;
import com.energy.translator.entity.VppEnrollment;
import com.energy.translator.entity.VppResource;
import com.energy.translator.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class VppActivityService {

    private final VppActivityRepository activityRepository;
    private final VppEnrollmentRepository enrollmentRepository;
    private final VppResourceRepository resourceRepository;
    private final EnterpriseRepository enterpriseRepository;
    private final MongoTemplate mongoTemplate;

    private static final Map<String, String> TYPE_NAMES = Map.of(
            "peak_shaving", "削峰",
            "valley_filling", "填谷",
            "emergency", "应急响应",
            "ancillary", "调频辅助"
    );

    private static final Map<String, String> STATUS_NAMES = Map.of(
            "draft", "草稿",
            "registration", "报名中",
            "confirmed", "已确认",
            "executing", "执行中",
            "completed", "已完成",
            "cancelled", "已取消"
    );

    public PageResponse<VppActivityDTO> getActivities(String status, String type, String keyword, 
                                                       boolean upcomingOnly, int page, int pageSize, String enterpriseId) {
        PageRequest pageRequest = PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.ASC, "time.startAt"));
        
        Page<VppActivity> activities;
        if (status != null) {
            activities = activityRepository.findByStatus(status, pageRequest);
        } else if (type != null) {
            activities = activityRepository.findByType(type, pageRequest);
        } else {
            activities = activityRepository.findAll(pageRequest);
        }

        // 获取用户已报名的活动
        final Set<String> enrolledActivityIds = enterpriseId != null ?
                enrollmentRepository.findByActivityIdAndStatus(enterpriseId, "enrolled").stream()
                        .map(VppEnrollment::getActivityId)
                        .collect(Collectors.toSet()) :
                new HashSet<>();

        List<VppActivityDTO> dtos = activities.getContent().stream()
                .filter(a -> !upcomingOnly || 
                        (a.getTime().getStartAt() != null && 
                         a.getTime().getStartAt().isAfter(Instant.now()) &&
                         Arrays.asList("registration", "confirmed").contains(a.getStatus())))
                .map(a -> convertToDTO(a, enrolledActivityIds.contains(a.getId())))
                .collect(Collectors.toList());

        return PageResponse.of(dtos, activities.getTotalElements(), page, pageSize);
    }

    public VppActivityDTO getActivityDetail(String activityId, String enterpriseId) {
        VppActivity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new RuntimeException("活动不存在"));

        boolean isEnrolled = false;
        if (enterpriseId != null) {
            isEnrolled = enrollmentRepository.findByActivityIdAndEnterpriseId(activityId, enterpriseId).isPresent();
        }

        return convertToDTO(activity, isEnrolled);
    }

    @Transactional
    public String createActivity(VppActivity activity, String userId) {
        activity.setStatus("draft");
        activity.setEnrolledResources(new ArrayList<>());
        activity.setEnrolledCount(0);
        activity.setCommittedCapacity(0.0);
        activity.setActualResponse(VppActivity.ActualResponse.builder()
                .totalCapacity(0.0)
                .completionRate(0.0)
                .build());
        activity.setCreatedBy(userId);
        
        activity = activityRepository.save(activity);
        return activity.getId();
    }

    @Transactional
    public void updateActivity(String activityId, VppActivity updateData) {
        VppActivity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new RuntimeException("活动不存在"));

        if (Arrays.asList("executing", "completed", "cancelled").contains(activity.getStatus())) {
            throw new RuntimeException("进行中或已结束的活动不允许修改");
        }

        if (updateData.getTitle() != null) activity.setTitle(updateData.getTitle());
        if (updateData.getType() != null) activity.setType(updateData.getType());
        if (updateData.getTime() != null) activity.setTime(updateData.getTime());
        if (updateData.getRequirements() != null) activity.setRequirements(updateData.getRequirements());
        if (updateData.getCompensation() != null) activity.setCompensation(updateData.getCompensation());
        if (updateData.getDescription() != null) activity.setDescription(updateData.getDescription());
        if (updateData.getStatus() != null) activity.setStatus(updateData.getStatus());

        activityRepository.save(activity);
    }

    @Transactional
    public void cancelActivity(String activityId, String reason) {
        VppActivity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new RuntimeException("活动不存在"));

        if (Arrays.asList("completed", "cancelled").contains(activity.getStatus())) {
            throw new RuntimeException("活动状态不允许取消");
        }

        activity.setStatus("cancelled");
        activity.setCancelReason(reason);
        activity.setCancelledAt(Instant.now());

        activityRepository.save(activity);
    }

    @Transactional
    public String enrollActivity(String activityId, List<String> resourceIds, Double committedCapacity,
                                  String enterpriseId, String contactName, String contactPhone) {
        VppActivity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new RuntimeException("活动不存在"));

        if (!"registration".equals(activity.getStatus())) {
            throw new RuntimeException("活动不在报名阶段");
        }

        if (activity.getTime().getRegistrationDeadline() != null && 
            activity.getTime().getRegistrationDeadline().isBefore(Instant.now())) {
            throw new RuntimeException("报名已截止");
        }

        // 检查资源
        List<VppResource> resources = resourceRepository.findAllById(resourceIds);
        List<VppResource> onlineResources = resources.stream()
                .filter(r -> enterpriseId.equals(r.getEnterpriseId()) && "online".equals(r.getStatus()))
                .collect(Collectors.toList());

        if (onlineResources.isEmpty()) {
            throw new RuntimeException("所选资源均未上线");
        }

        double totalAdjustableMax = onlineResources.stream()
                .mapToDouble(r -> r.getAdjustableRange().getMax())
                .sum();

        if (committedCapacity > totalAdjustableMax) {
            throw new RuntimeException("承诺容量超出资源可调范围");
        }

        if (committedCapacity < activity.getCompensation().getMinimumCapacity()) {
            throw new RuntimeException("承诺容量不低于" + activity.getCompensation().getMinimumCapacity() + "kW");
        }

        // 检查是否已报名
        if (enrollmentRepository.findByActivityIdAndEnterpriseId(activityId, enterpriseId).isPresent()) {
            throw new RuntimeException("您已报名此活动");
        }

        // 创建报名记录
        VppEnrollment enrollment = VppEnrollment.builder()
                .activityId(activityId)
                .enterpriseId(enterpriseId)
                .resourceIds(onlineResources.stream().map(VppResource::getId).collect(Collectors.toList()))
                .committedCapacity(committedCapacity)
                .contactName(contactName)
                .contactPhone(contactPhone)
                .status("enrolled")
                .enrolledAt(Instant.now())
                .build();

        enrollment = enrollmentRepository.save(enrollment);

        // 更新活动统计
        activity.setEnrolledCount(activity.getEnrolledCount() + 1);
        activity.setCommittedCapacity(activity.getCommittedCapacity() + committedCapacity);
        activity.getEnrolledResources().addAll(onlineResources.stream().map(VppResource::getId).collect(Collectors.toList()));
        activityRepository.save(activity);

        return enrollment.getId();
    }

    @Transactional
    public void cancelEnroll(String activityId, String enterpriseId) {
        VppActivity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new RuntimeException("活动不存在"));

        VppEnrollment enrollment = enrollmentRepository.findByActivityIdAndEnterpriseId(activityId, enterpriseId)
                .orElseThrow(() -> new RuntimeException("您未报名此活动"));

        // 检查是否在可取消时间内
        if (activity.getTime().getStartAt() != null) {
            long hoursUntilStart = Duration.between(Instant.now(), activity.getTime().getStartAt()).toHours();
            if (hoursUntilStart < 12) {
                throw new RuntimeException("活动开始前12小时内不允许取消");
            }
        }

        enrollment.setStatus("cancelled");
        enrollment.setCancelledAt(Instant.now());
        enrollmentRepository.save(enrollment);

        // 更新活动统计
        activity.setEnrolledCount(Math.max(0, activity.getEnrolledCount() - 1));
        activity.setCommittedCapacity(Math.max(0, activity.getCommittedCapacity() - enrollment.getCommittedCapacity()));
        activityRepository.save(activity);
    }

    public PageResponse<VppEnrollment> getMyEnrollments(String enterpriseId, int page, int pageSize) {
        PageRequest pageRequest = PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.DESC, "enrolledAt"));
        Page<VppEnrollment> enrollments = enrollmentRepository.findByEnterpriseId(enterpriseId, pageRequest);
        return PageResponse.of(enrollments.getContent(), enrollments.getTotalElements(), page, pageSize);
    }

    @Transactional
    public void startActivity(String activityId) {
        VppActivity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new RuntimeException("活动不存在"));

        if (!Arrays.asList("confirmed", "registration").contains(activity.getStatus())) {
            throw new RuntimeException("活动状态不允许开始");
        }

        activity.setStatus("executing");
        activity.setActualStartAt(Instant.now());
        activityRepository.save(activity);
    }

    @Transactional
    public void endActivity(String activityId) {
        VppActivity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new RuntimeException("活动不存在"));

        if (!"executing".equals(activity.getStatus())) {
            throw new RuntimeException("活动未在执行中");
        }

        activity.setStatus("completed");
        activity.setActualEndAt(Instant.now());
        activityRepository.save(activity);

        // 触发结算评估
        evaluateActivity(activityId);
    }

    private void evaluateActivity(String activityId) {
        VppActivity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new RuntimeException("活动不存在"));

        List<VppEnrollment> enrollments = enrollmentRepository.findByActivityIdAndStatus(activityId, "enrolled");

        double totalActualCapacity = 0;
        for (VppEnrollment enrollment : enrollments) {
            // 基于资源响应率计算实际响应容量（而非随机数）
            double totalResourceMax = 0;
            double weightedResponseRate = 0;
            List<VppResource> resources = resourceRepository.findAllById(enrollment.getResourceIds());
            
            for (VppResource resource : resources) {
                if (resource.getAdjustableRange() != null) {
                    totalResourceMax += resource.getAdjustableRange().getMax();
                }
                if (resource.getStatistics() != null && resource.getStatistics().getAvgResponseRate() > 0) {
                    weightedResponseRate += resource.getStatistics().getAvgResponseRate() * 
                            (resource.getAdjustableRange() != null ? resource.getAdjustableRange().getMax() : 0);
                }
            }
            
            // 基于历史响应率计算实际容量（默认95%若历史数据）
            double avgRate = totalResourceMax > 0 ? 
                    weightedResponseRate / totalResourceMax : 0.95;
            double actualCapacity = enrollment.getCommittedCapacity() * Math.max(avgRate, 0.8);
            double completionRate = actualCapacity / Math.max(enrollment.getCommittedCapacity(), 1);
            totalActualCapacity += actualCapacity;

            // 更新报名记录的完成率
            enrollment.setCompletionRate(completionRate);
            enrollmentRepository.save(enrollment);
        }

        activity.setActualResponse(VppActivity.ActualResponse.builder()
                .totalCapacity(totalActualCapacity)
                .completionRate(activity.getCommittedCapacity() > 0 ? 
                        totalActualCapacity / activity.getCommittedCapacity() : 0.0)
                .build());
        activity.setEvaluated(true);
        activity.setEvaluatedAt(Instant.now());
        activityRepository.save(activity);

        log.info("VPP活动评估完成: activity={}, committed={}kW, actual={}kW, rate={}%",
                activity.getTitle(), activity.getCommittedCapacity(), totalActualCapacity,
                String.format("%.1f", activity.getActualResponse().getCompletionRate() * 100));
    }

    private VppActivityDTO convertToDTO(VppActivity activity, boolean isEnrolled) {
        VppActivityDTO dto = VppActivityDTO.builder()
                .id(activity.getId())
                .title(activity.getTitle())
                .type(activity.getType())
                .typeName(TYPE_NAMES.getOrDefault(activity.getType(), activity.getType()))
                .description(activity.getDescription())
                .status(activity.getStatus())
                .statusName(STATUS_NAMES.getOrDefault(activity.getStatus(), activity.getStatus()))
                .startAt(activity.getTime() != null ? activity.getTime().getStartAt() : null)
                .endAt(activity.getTime() != null ? activity.getTime().getEndAt() : null)
                .registrationDeadline(activity.getTime() != null ? activity.getTime().getRegistrationDeadline() : null)
                .totalCapacity(activity.getRequirements() != null ? activity.getRequirements().getTotalCapacity() : null)
                .minDuration(activity.getRequirements() != null ? activity.getRequirements().getMinDuration() : null)
                .maxResponseTime(activity.getRequirements() != null ? activity.getRequirements().getMaxResponseTime() : null)
                .capacityPrice(activity.getCompensation() != null ? activity.getCompensation().getCapacityPrice() : null)
                .energyPrice(activity.getCompensation() != null ? activity.getCompensation().getEnergyPrice() : null)
                .minimumCapacity(activity.getCompensation() != null ? activity.getCompensation().getMinimumCapacity() : null)
                .enrolledCount(activity.getEnrolledCount())
                .committedCapacity(activity.getCommittedCapacity())
                .fillRate(activity.getRequirements() != null && activity.getRequirements().getTotalCapacity() > 0 ?
                        activity.getCommittedCapacity() / activity.getRequirements().getTotalCapacity() * 100 : 0.0)
                .isEnrolled(isEnrolled)
                .canEnroll("registration".equals(activity.getStatus()) && 
                        (activity.getTime() == null || 
                         activity.getTime().getRegistrationDeadline() == null ||
                         activity.getTime().getRegistrationDeadline().isAfter(Instant.now())))
                .createdAt(activity.getCreatedAt())
                .build();

        if (activity.getTime() != null && activity.getTime().getStartAt() != null) {
            dto.setTimeUntilStart(Math.max(0, activity.getTime().getStartAt().toEpochMilli() - Instant.now().toEpochMilli()));
        }

        return dto;
    }
}
