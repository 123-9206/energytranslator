package com.energy.translator.service;

import com.energy.translator.entity.EnergyRecord;
import com.energy.translator.entity.Enterprise;
import com.energy.translator.repository.EnergyRecordRepository;
import com.energy.translator.repository.EnterpriseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 分布式协同调度器（多企业可调负荷聚合优化器）
 * ------------------------------------------------------------------
 * 吸收竞品专利 CN120767796B「电网用电侧离散小负荷分布式协同管理系统」中
 * 的"分布式协同控制模块"思想，但为能耗翻译官【独立实现】：
 *
 *  - 协调器只获取每个节点(企业)的有限信息：(可调负荷, 响应延迟, 可调性评分)，
 *    不掌握各节点的内部生产过程中细节（有限信息协同）；
 *  - 按权重【比例分配】区域目标削减量，权重 = 可调性 / (1 + 响应延迟/基准延迟)，
 *    优先调度高可调、快响应的节点；
 *  - 对一次分配后的余量，在各仍有余量的节点间做【二次填充(water-filling)】，
 *    在不超过各节点可调上限的前提下最大化满足目标；
 *  - 每个节点拿到本地决策(削减量 + 启动延迟)后就地执行，形成分散决策 + 集中协调的闭环。
 *
 * 该算法与公开专利思路兼容，但模型、代码、参数均为自有实现，不构成对外部专利的依赖或复制。
 */
@Service
@RequiredArgsConstructor
public class CoordinationService {

    private final EnterpriseRepository enterpriseRepository;
    private final EnergyRecordRepository energyRecordRepository;
    private final AIDiagnosisService aiDiagnosisService;

    /** 基准延迟(分钟)：用于归一化响应延迟对权重的影响 */
    private static final double REF_DELAY_MIN = 30.0;
    /** 演示池最小成员数：真实在网企业不足时，补充示例成员使聚合有意义（synthetic=true 明确标注） */
    private static final int MIN_POOL_SIZE = 4;

    /**
     * 执行一次区域需求响应协同调度。
     *
     * @param targetReductionKw   区域目标削减量(kW)
     * @param windowMinutes       响应窗口(分钟)，用于展示
     * @param currentEnterpriseId 当前调用企业ID（在结果中标记 isCurrentUser）
     * @return 区域容量、分配结果、缺口、各节点本地决策
     */
    public Map<String, Object> aggregate(double targetReductionKw, int windowMinutes, String currentEnterpriseId) {
        double R = targetReductionKw > 0 ? targetReductionKw : 500.0;
        int window = windowMinutes > 0 ? windowMinutes : 60;

        // 1. 组建协同节点池：真实在网(verified)企业 + 必要时的示例成员
        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Enterprise> enterprises = enterpriseRepository.findAll();
        for (Enterprise e : enterprises) {
            if (!"verified".equals(e.getStatus())) continue;
            nodes.add(buildNode(e, false, currentEnterpriseId));
        }
        if (nodes.size() < MIN_POOL_SIZE) {
            nodes.addAll(buildSyntheticPeers(MIN_POOL_SIZE - nodes.size(), currentEnterpriseId));
        }

        // 2. 计算各节点权重
        double totalWeight = 0;
        for (Map<String, Object> n : nodes) {
            double adjScore = ((Number) n.get("adjustabilityScore")).doubleValue();
            int respDelay = ((Number) n.get("responseDelayMin")).intValue();
            double w = (adjScore > 0 ? adjScore : 50) / (1 + respDelay / REF_DELAY_MIN);
            n.put("weight", round2(w));
            totalWeight += w;
        }

        // 3. 第一遍：按比例权重分配（受各节点可调上限约束）
        double allocatedSum = 0;
        for (Map<String, Object> n : nodes) {
            double adjustable = ((Number) n.get("adjustableLoadKw")).doubleValue();
            double w = ((Number) n.get("weight")).doubleValue();
            double alloc = Math.min(R * (totalWeight > 0 ? w / totalWeight : 0), adjustable);
            n.put("allocatedKw", round2(alloc));
            allocatedSum += alloc;
        }

        // 4. 第二遍：余量在仍有余量的节点间二次填充(water-filling)
        double residual = R - allocatedSum;
        for (int iter = 0; iter < 12 && residual > 0.001; iter++) {
            double spareSumW = 0;
            for (Map<String, Object> n : nodes) {
                double adjustable = ((Number) n.get("adjustableLoadKw")).doubleValue();
                double alloc = ((Number) n.get("allocatedKw")).doubleValue();
                double w = ((Number) n.get("weight")).doubleValue();
                double spare = Math.max(0, adjustable - alloc);
                spareSumW += spare * w;
            }
            if (spareSumW <= 0) break;
            for (Map<String, Object> n : nodes) {
                double adjustable = ((Number) n.get("adjustableLoadKw")).doubleValue();
                double alloc = ((Number) n.get("allocatedKw")).doubleValue();
                double w = ((Number) n.get("weight")).doubleValue();
                double spare = Math.max(0, adjustable - alloc);
                double add = Math.min(spare, residual * (spare * w) / spareSumW);
                alloc += add;
                n.put("allocatedKw", round2(alloc));
                residual -= add;
            }
        }

        // 5. 组装各节点本地决策 + 区域汇总
        double regionalCapacity = 0, totalAllocated = 0;
        for (Map<String, Object> n : nodes) {
            double adjustable = ((Number) n.get("adjustableLoadKw")).doubleValue();
            double alloc = ((Number) n.get("allocatedKw")).doubleValue();
            int respDelay = ((Number) n.get("responseDelayMin")).intValue();
            n.put("startOffsetMin", respDelay);
            n.put("action", alloc > 0.5
                    ? String.format("削减 %.0f kW，自 T+%dmin 起就地执行", alloc, respDelay)
                    : "不参与本次响应");
            regionalCapacity += adjustable;
            totalAllocated += alloc;
        }
        double shortfall = Math.max(0, R - totalAllocated);
        double utilization = regionalCapacity > 0 ? totalAllocated / regionalCapacity * 100 : 0;
        double fulfilled = R > 0 ? totalAllocated / R * 100 : 100;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("targetReductionKw", round2(R));
        result.put("windowMinutes", window);
        result.put("memberCount", nodes.size());
        result.put("realMemberCount", countReal(nodes));
        result.put("regionalAdjustableCapacityKw", round2(regionalCapacity));
        result.put("totalAllocatedKw", round2(totalAllocated));
        result.put("shortfallKw", round2(shortfall));
        result.put("utilizationPct", round2(utilization));
        result.put("fulfilledPct", round2(fulfilled));
        result.put("nodes", nodes);
        result.put("algorithmNote",
                "有限信息协同：协调器仅获取各节点(可调负荷,响应延迟,可调性)，按权重比例分配区域目标削减，" +
                "余量在有余量节点间二次填充(water-filling)；各节点据本地决策就地执行。");
        result.put("ipNote",
                "本调度器为能耗翻译官独立实现，模型与代码均为自有，思路与公开专利 CN120767796B 兼容但不构成对其专利实现的复制或依赖。");
        return result;
    }

    private Map<String, Object> buildNode(Enterprise e, boolean synthetic, String currentEnterpriseId) {
        Map<String, Object> profile = aiDiagnosisService.getLoadPatternProfile(e.getId(), 30);
        double adjScore = ((Number) profile.get("adjustabilityScore")).doubleValue();
        String adjLevel = (String) profile.get("adjustabilityLevel");
        int respDelay = ((Number) profile.get("responseDelayMin")).intValue();
        double adjustable = estimateAdjustableLoad(e, adjScore);

        Map<String, Object> node = new LinkedHashMap<>();
        node.put("enterpriseId", e.getId());
        node.put("name", e.getName());
        node.put("isCurrentUser", e.getId() != null && e.getId().equals(currentEnterpriseId));
        node.put("synthetic", synthetic);
        node.put("adjustableLoadKw", round2(adjustable));
        node.put("responseDelayMin", respDelay);
        node.put("adjustabilityScore", (int) adjScore);
        node.put("adjustabilityLevel", adjLevel);
        return node;
    }

    /** 估算企业可调负荷(kW)：优先用登记的 adjustableCapacity，否则用近30天峰值负荷×可调性 */
    private double estimateAdjustableLoad(Enterprise e, double adjScore) {
        if (e.getCapacityInfo() != null && e.getCapacityInfo().getAdjustableCapacity() != null
                && e.getCapacityInfo().getAdjustableCapacity() > 0) {
            return e.getCapacityInfo().getAdjustableCapacity();
        }
        List<EnergyRecord> recs = energyRecordRepository.findTop100ByEnterpriseIdOrderByTimestampDesc(e.getId());
        double peak = recs.stream()
                .mapToDouble(r -> r.getValue() != null ? r.getValue() : 0)
                .max().orElse(0);
        return peak * (adjScore / 100.0);
    }

    /** 示例协同成员（明确标注 synthetic），用于演示多企业聚合；真实企业上量后自动稀释 */
    private List<Map<String, Object>> buildSyntheticPeers(int n, String currentEnterpriseId) {
        // 名称 / 可调负荷(kW) / 响应延迟(min) / 可调性评分
        Object[][] samples = {
                {"示例成员·精密电子厂", 800.0, 30, 80},
                {"示例成员·食品冷链厂", 500.0, 5, 90},
                {"示例成员·纺织印染厂", 1200.0, 120, 55},
                {"示例成员·商业综合体", 300.0, 30, 65},
        };
        List<Map<String, Object>> list = new ArrayList<>();
        for (int i = 0; i < n && i < samples.length; i++) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("enterpriseId", "synthetic-" + (i + 1));
            node.put("name", samples[i][0]);
            node.put("isCurrentUser", false);
            node.put("synthetic", true);
            node.put("adjustableLoadKw", (Double) samples[i][1]);
            node.put("responseDelayMin", (Integer) samples[i][2]);
            node.put("adjustabilityScore", (Integer) samples[i][3]);
            node.put("adjustabilityLevel", (Integer) samples[i][3] >= 60 ? "高" : "中");
            list.add(node);
        }
        return list;
    }

    private int countReal(List<Map<String, Object>> nodes) {
        int c = 0;
        for (Map<String, Object> n : nodes) if (!((Boolean) n.get("synthetic"))) c++;
        return c;
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
