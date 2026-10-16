package com.energy.translator.service;

import com.energy.translator.entity.Enterprise;
import com.energy.translator.entity.VppActivity;
import com.energy.translator.entity.VppEnrollment;
import com.energy.translator.entity.VppSettlement;
import com.energy.translator.repository.EnterpriseRepository;
import com.energy.translator.repository.VppActivityRepository;
import com.energy.translator.repository.VppEnrollmentRepository;
import com.energy.translator.repository.VppSettlementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * VPP 结算服务（VPP增强）
 * 按企业 × 结算周期（yyyy-MM）生成结算单：
 * 容量补偿 + 电量补偿 + 完成奖励 - 未完成扣罚
 * 状态流转：pending → approved → paid（仅管理员审批）
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VppSettlementService {

    /** 默认响应率（无历史数据时） */
    private static final double DEFAULT_RESPONSE_RATE = 0.95;
    /** 完成率低于该值触发扣罚 */
    private static final double PENALTY_THRESHOLD = 0.8;

    private final VppSettlementRepository settlementRepository;
    private final VppEnrollmentRepository enrollmentRepository;
    private final VppActivityRepository activityRepository;
    private final EnterpriseRepository enterpriseRepository;

    /**
     * 企业侧：查询我的结算单
     */
    public Page<VppSettlement> getMySettlements(String enterpriseId, int page, int pageSize) {
        Pageable pageable = PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        return settlementRepository.findByEnterpriseId(enterpriseId, pageable);
    }

    /**
     * 平台侧：查询全部结算单（可按状态过滤）
     */
    public Page<VppSettlement> getAllSettlements(String status, int page, int pageSize) {
        Pageable pageable = PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        if (status != null && !status.isBlank()) {
            return settlementRepository.findByStatus(status, pageable);
        }
        return settlementRepository.findAll(pageable);
    }

    /**
     * 生成/刷新某企业的月度结算单
     * period 格式：yyyy-MM
     */
    public String generateSettlement(String enterpriseId, String period) {
        Enterprise enterprise = enterpriseRepository.findById(enterpriseId).orElse(null);
        // 该企业全部报名（enrolled）
        Page<VppEnrollment> enrollmentPage = enrollmentRepository.findByEnterpriseId(enterpriseId, PageRequest.of(0, 100));
        List<VppEnrollment> enrollments = enrollmentPage.getContent();
        if (enrollments.isEmpty()) {
            throw new RuntimeException("该企业本期无报名记录");
        }

        VppSettlement settlement = buildSettlement(enterpriseId, enterprise, period, enrollments);
        // upsert：同企业同周期覆盖
        Optional<VppSettlement> existing = settlementRepository.findByEnterpriseIdAndPeriod(enterpriseId, period);
        if (existing.isPresent()) {
            settlement.setId(existing.get().getId());
            settlement.setStatus(existing.get().getStatus());
            if (existing.get().getApprovedAt() != null) settlement.setApprovedAt(existing.get().getApprovedAt());
            if (existing.get().getPaidAt() != null) settlement.setPaidAt(existing.get().getPaidAt());
        }
        settlementRepository.save(settlement);
        log.info("VPP结算单生成: enterpriseId={}, period={}, amount={}元",
                enterpriseId, period, settlement.getSummary().getTotalAmount());
        return settlement.getId();
    }

    private VppSettlement buildSettlement(String enterpriseId, Enterprise enterprise, String period,
                                          List<VppEnrollment> enrollments) {
        LocalDate periodStart = LocalDate.parse(period + "-01");
        Instant periodFrom = periodStart.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant periodTo = periodStart.plusMonths(1).atStartOfDay(ZoneId.systemDefault()).toInstant();

        List<VppSettlement.SettlementItem> items = new ArrayList<>();
        double totalCapacityComp = 0, totalEnergyComp = 0, totalBonus = 0, totalPenalty = 0, totalCapacity = 0;

        for (VppEnrollment enrollment : enrollments) {
            Instant enrolledAt = enrollment.getEnrolledAt() != null ? enrollment.getEnrolledAt() : enrollment.getCreatedAt();
            if (enrolledAt == null || enrolledAt.isBefore(periodFrom) || !enrolledAt.isBefore(periodTo)) {
                continue;
            }
            VppActivity activity = activityRepository.findById(enrollment.getActivityId()).orElse(null);
            if (activity == null || activity.getCompensation() == null) {
                continue;
            }
            VppActivity.ActivityCompensation comp = activity.getCompensation();
            double committed = enrollment.getCommittedCapacity() != null ? enrollment.getCommittedCapacity() : 0;
            double rate = enrollment.getCompletionRate() != null ? enrollment.getCompletionRate() : DEFAULT_RESPONSE_RATE;
            double actual = committed * Math.max(rate, 0.5);

            double capacityComp = committed * nz(comp.getCapacityPrice());
            int minDurationMin = activity.getRequirements() != null && activity.getRequirements().getMinDuration() != null
                    ? activity.getRequirements().getMinDuration() : 60;
            double durationHours = minDurationMin / 60.0;
            double energyComp = actual * durationHours * nz(comp.getEnergyPrice());
            double bonus = rate >= 1.0 ? nz(comp.getCompletionBonus()) : 0;
            double penalty = rate < PENALTY_THRESHOLD
                    ? (PENALTY_THRESHOLD - rate) * committed * nz(comp.getFailurePenalty()) : 0;
            double itemTotal = capacityComp + energyComp + bonus - penalty;

            items.add(VppSettlement.SettlementItem.builder()
                    .activityId(activity.getId())
                    .activityTitle(activity.getTitle())
                    .committedCapacity(committed)
                    .actualCapacity(Math.round(actual * 100.0) / 100.0)
                    .completionRate(Math.round(rate * 100.0) / 100.0)
                    .capacityCompensation(round2(capacityComp))
                    .energyCompensation(round2(energyComp))
                    .bonus(round2(bonus))
                    .penalty(round2(penalty))
                    .total(round2(itemTotal))
                    .build());

            totalCapacityComp += capacityComp;
            totalEnergyComp += energyComp;
            totalBonus += bonus;
            totalPenalty += penalty;
            totalCapacity += actual;
        }

        if (items.isEmpty()) {
            throw new RuntimeException("该企业本期无已结算活动");
        }

        double totalAmount = totalCapacityComp + totalEnergyComp + totalBonus - totalPenalty;

        // 详情页派生字段
        long completedCount = items.stream().filter(it -> it.getCompletionRate() != null && it.getCompletionRate() >= 1.0).count();
        double avgRate = items.stream().filter(it -> it.getCompletionRate() != null)
                .mapToDouble(VppSettlement.SettlementItem::getCompletionRate).average().orElse(0);
        double carbonReduction = round2(totalCapacity * 0.785); // kg CO2/kWh（电网排放因子）

        return VppSettlement.builder()
                .enterpriseId(enterpriseId)
                .enterpriseName(enterprise != null ? enterprise.getName() : "未命名企业")
                .period(period)
                .startDate(periodFrom)
                .endDate(periodTo.minusNanos(1))
                .summary(VppSettlement.SettlementSummary.builder()
                        .totalActivities(items.size())
                        .totalCapacity(round2(totalCapacity))
                        .totalEnergy(round2(totalCapacity * 1.0))
                        .capacityCompensation(round2(totalCapacityComp))
                        .energyCompensation(round2(totalEnergyComp))
                        .bonusCompensation(round2(totalBonus))
                        .penaltyAmount(round2(totalPenalty))
                        .totalAmount(round2(totalAmount))
                        // 详情页字段
                        .totalCapacityCompensation(round2(totalCapacityComp))
                        .totalEnergyCompensation(round2(totalEnergyComp))
                        .totalCompensation(round2(totalAmount))
                        .totalCarbonReduction(carbonReduction)
                        .totalResponseCount(items.size())
                        .completedResponseCount((int) completedCount)
                        .avgResponseRate(round2(avgRate * 100))
                        .build())
                .items(items)
                .status("pending")
                .build();
    }

    /**
     * 审批结算单（管理员）
     */
    public void approveSettlement(String id, String adminId) {
        VppSettlement settlement = settlementRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("结算单不存在"));
        if (!"pending".equals(settlement.getStatus())) {
            throw new RuntimeException("只有待审批的结算单可以审批");
        }
        settlement.setStatus("approved");
        settlement.setApprovedBy(adminId);
        settlement.setApprovedAt(Instant.now());
        settlementRepository.save(settlement);
    }

    /**
     * 确认付款（管理员）
     */
    public void paySettlement(String id) {
        VppSettlement settlement = settlementRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("结算单不存在"));
        if (!"approved".equals(settlement.getStatus())) {
            throw new RuntimeException("只有已审批的结算单可以付款");
        }
        settlement.setStatus("paid");
        settlement.setPaidAt(Instant.now());
        settlementRepository.save(settlement);
    }

    /**
     * 删除结算单（管理员）
     */
    public void deleteSettlement(String id) {
        if (!settlementRepository.existsById(id)) {
            throw new RuntimeException("结算单不存在");
        }
        settlementRepository.deleteById(id);
    }

    /**
     * 查询结算单详情（企业仅可看本企业，管理员可看任意）
     */
    public VppSettlement getSettlementDetail(String id, String enterpriseId, boolean isAdmin) {
        VppSettlement settlement = settlementRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("结算单不存在"));
        if (!isAdmin && !enterpriseId.equals(settlement.getEnterpriseId())) {
            throw new RuntimeException("无权查看该结算单");
        }
        return settlement;
    }

    /**
     * 企业发起提现：paid -> withdrawn
     */
    public void withdrawSettlement(String id, String enterpriseId, String account) {
        VppSettlement settlement = settlementRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("结算单不存在"));
        if (!enterpriseId.equals(settlement.getEnterpriseId())) {
            throw new RuntimeException("只能对本企业的结算单提现");
        }
        if (!"paid".equals(settlement.getStatus())) {
            throw new RuntimeException("只有已付款的结算单可以提现");
        }
        settlement.setStatus("withdrawn");
        settlement.setWithdrawnBy(enterpriseId);
        settlement.setWithdrawnAt(Instant.now());
        settlement.setWithdrawalAccount(account);
        settlementRepository.save(settlement);
    }

    private double nz(Double v) {
        return v != null ? v : 0;
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
