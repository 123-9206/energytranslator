package com.energy.translator.service;

import com.energy.translator.entity.Enterprise;
import com.energy.translator.entity.EnergyRecord;
import com.energy.translator.entity.PolicyBenefit;
import com.energy.translator.repository.EnergyRecordRepository;
import com.energy.translator.repository.EnterpriseRepository;
import com.energy.translator.repository.PolicyBenefitRepository;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 政策福利中心服务
 * 平台侧：政策库管理（收录/更新/失效）
 * 企业侧：政策浏览 + 基于企业画像（行业/年用电量）的智能匹配推荐
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PolicyService {

    private final PolicyBenefitRepository policyRepository;
    private final EnterpriseRepository enterpriseRepository;
    private final EnergyRecordRepository energyRecordRepository;

    /**
     * 平台侧：政策分页列表（可按类型/关键词过滤）
     */
    public Page<PolicyBenefit> getPolicies(String status, String policyType, String keyword, int page, int pageSize) {
        Pageable pageable = PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        String effectiveStatus = (status == null || status.isBlank()) ? "active" : status;
        if (keyword != null && !keyword.isBlank()) {
            return policyRepository.findByStatusAndTitleContainingIgnoreCase(effectiveStatus, keyword, pageable);
        }
        if (policyType != null && !policyType.isBlank()) {
            return policyRepository.findByPolicyType(policyType, pageable);
        }
        return policyRepository.findByStatus(effectiveStatus, pageable);
    }

    /**
     * 企业侧：获取全部可申报政策（含企业匹配标记）
     */
    public Map<String, Object> getAvailablePolicies(String enterpriseId, String policyType) {
        List<PolicyBenefit> all = policyRepository.findByStatus("active");
        if (policyType != null && !policyType.isBlank()) {
            all = all.stream().filter(p -> policyType.equals(p.getPolicyType())).collect(Collectors.toList());
        }
        Enterprise enterprise = enterpriseRepository.findById(enterpriseId).orElse(null);
        double annualUsage = calculateAnnualUsage(enterpriseId);

        List<Map<String, Object>> list = new ArrayList<>();
        for (PolicyBenefit p : all) {
            Map<String, Object> item = new HashMap<>();
            item.put("policy", p);
            item.put("matched", matchPolicy(p, enterprise, annualUsage));
            item.put("matchScore", matchScore(p, enterprise, annualUsage));
            list.add(item);
        }
        // 已匹配优先
        list.sort((a, b) -> Boolean.compare((Boolean) b.get("matched"), (Boolean) a.get("matched")));

        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("total", list.size());
        result.put("matchedCount", list.stream().filter(m -> (Boolean) m.get("matched")).count());
        result.put("annualUsage", Math.round(annualUsage));
        result.put("industry", enterprise != null ? enterprise.getIndustry() : null);
        return result;
    }

    /**
     * 平台侧：创建政策
     */
    public String createPolicy(PolicyBenefit policy) {
        if (policy.getStatus() == null || policy.getStatus().isBlank()) {
            policy.setStatus("active");
        }
        if (policy.getApplyCount() == null) {
            policy.setApplyCount(0);
        }
        policyRepository.save(policy);
        log.info("政策创建成功: title={}, type={}", policy.getTitle(), policy.getPolicyType());
        return policy.getId();
    }

    /**
     * 平台侧：更新政策
     */
    public void updatePolicy(String id, PolicyBenefit updates) {
        PolicyBenefit policy = policyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("政策不存在"));
        if (updates.getTitle() != null) policy.setTitle(updates.getTitle());
        if (updates.getPolicyType() != null) policy.setPolicyType(updates.getPolicyType());
        if (updates.getRegion() != null) policy.setRegion(updates.getRegion());
        if (updates.getDescription() != null) policy.setDescription(updates.getDescription());
        if (updates.getEligibility() != null) policy.setEligibility(updates.getEligibility());
        if (updates.getBenefitAmount() != null) policy.setBenefitAmount(updates.getBenefitAmount());
        if (updates.getApplicationDeadline() != null) policy.setApplicationDeadline(updates.getApplicationDeadline());
        if (updates.getStatus() != null) policy.setStatus(updates.getStatus());
        if (updates.getSource() != null) policy.setSource(updates.getSource());
        if (updates.getApplyUrl() != null) policy.setApplyUrl(updates.getApplyUrl());
        if (updates.getMatchIndustries() != null) policy.setMatchIndustries(updates.getMatchIndustries());
        if (updates.getMinAnnualUsage() != null) policy.setMinAnnualUsage(updates.getMinAnnualUsage());
        if (updates.getHighlights() != null) policy.setHighlights(updates.getHighlights());
        if (updates.getNotes() != null) policy.setNotes(updates.getNotes());
        policyRepository.save(policy);
    }

    /**
     * 平台侧：删除政策
     */
    public void deletePolicy(String id) {
        if (!policyRepository.existsById(id)) {
            throw new RuntimeException("政策不存在");
        }
        policyRepository.deleteById(id);
    }

    /**
     * 企业侧：政策汇总（可用数/预估总福利/已匹配数）
     */
    public Map<String, Object> getSummary(String enterpriseId) {
        List<PolicyBenefit> active = policyRepository.findByStatus("active");
        Enterprise enterprise = enterpriseRepository.findById(enterpriseId).orElse(null);
        double annualUsage = calculateAnnualUsage(enterpriseId);

        List<PolicyBenefit> matched = active.stream()
                .filter(p -> matchPolicy(p, enterprise, annualUsage))
                .collect(Collectors.toList());
        double totalBenefit = matched.stream()
                .mapToDouble(p -> p.getBenefitAmount() != null ? p.getBenefitAmount() : 0).sum();

        Map<String, Object> summary = new HashMap<>();
        summary.put("totalPolicies", active.size());
        summary.put("matchedCount", matched.size());
        summary.put("totalBenefit", Math.round(totalBenefit));
        // 按类型分布
        Map<String, Long> typeDist = active.stream()
                .collect(Collectors.groupingBy(PolicyBenefit::getPolicyType, Collectors.counting()));
        summary.put("typeDistribution", typeDist);
        return summary;
    }

    private boolean matchPolicy(PolicyBenefit policy, Enterprise enterprise, double annualUsage) {
        // 行业匹配：配置了行业限制则必须命中
        if (policy.getMatchIndustries() != null && !policy.getMatchIndustries().isEmpty()) {
            if (enterprise == null || enterprise.getIndustry() == null
                    || !policy.getMatchIndustries().contains(enterprise.getIndustry())) {
                return false;
            }
        }
        // 用电量匹配
        if (policy.getMinAnnualUsage() != null && policy.getMinAnnualUsage() > 0) {
            if (annualUsage < policy.getMinAnnualUsage()) {
                return false;
            }
        }
        return true;
    }

    private double matchScore(PolicyBenefit policy, Enterprise enterprise, double annualUsage) {
        double score = 50;
        if (policy.getMatchIndustries() != null && !policy.getMatchIndustries().isEmpty()
                && enterprise != null && policy.getMatchIndustries().contains(enterprise.getIndustry())) {
            score += 30;
        }
        if (policy.getMinAnnualUsage() != null && annualUsage >= policy.getMinAnnualUsage()) {
            score += 20;
        }
        return Math.min(score, 100);
    }

    private double calculateAnnualUsage(String enterpriseId) {
        LocalDate now = LocalDate.now();
        Instant start = now.minusYears(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant end = now.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
        List<EnergyRecord> records = energyRecordRepository.findByTimeRange(enterpriseId, start, end);
        return records.stream().mapToDouble(r -> r.getValue() != null ? r.getValue() : 0).sum();
    }
}
