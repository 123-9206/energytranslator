package com.energy.translator.service;

import com.energy.translator.entity.Enterprise;
import com.energy.translator.entity.GreenPowerContract;
import com.energy.translator.repository.EnterpriseRepository;
import com.energy.translator.repository.GreenPowerContractRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 绿电交易台账服务
 * 企业侧：合同录入/查看/汇总（绿电消纳、绿证、碳减排、节约成本）
 * 平台侧：全部企业合同管理、状态流转、合同编号生成
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GreenPowerService {

    /** 电网碳排放因子 kg CO₂/kWh（与 CarbonService 一致） */
    private static final double CARBON_FACTOR = 0.785;

    private final GreenPowerContractRepository contractRepository;
    private final EnterpriseRepository enterpriseRepository;

    /**
     * 企业侧：分页查询自己的绿电合同
     */
    public Page<GreenPowerContract> getMyContracts(String enterpriseId, int page, int pageSize) {
        Pageable pageable = PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        return contractRepository.findByEnterpriseId(enterpriseId, pageable);
    }

    /**
     * 平台侧：查询全部合同（可按企业名/状态过滤）
     */
    public Page<GreenPowerContract> getAllContracts(String keyword, String status, int page, int pageSize) {
        Pageable pageable = PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        if (keyword != null && !keyword.isBlank()) {
            return contractRepository.findByEnterpriseNameContainingIgnoreCase(keyword, pageable);
        }
        if (status != null && !status.isBlank()) {
            return contractRepository.findByStatus(status, pageable);
        }
        return contractRepository.findAll(pageable);
    }

    /**
     * 获取单个合同（校验企业归属）
     */
    public GreenPowerContract getContract(String id, String enterpriseId, boolean isAdmin) {
        GreenPowerContract contract = contractRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("绿电合同不存在"));
        if (!isAdmin && !contract.getEnterpriseId().equals(enterpriseId)) {
            throw new RuntimeException("无权访问该合同");
        }
        return contract;
    }

    /**
     * 企业侧：新增绿电合同
     */
    public String createContract(GreenPowerContract contract, String enterpriseId) {
        Enterprise enterprise = enterpriseRepository.findById(enterpriseId).orElse(null);
        contract.setEnterpriseId(enterpriseId);
        contract.setEnterpriseName(enterprise != null ? enterprise.getName() : "未命名企业");
        if (contract.getStatus() == null || contract.getStatus().isBlank()) {
            contract.setStatus("active");
        }
        if (contract.getCertificateStatus() == null) {
            contract.setCertificateStatus("unclaimed");
        }
        contract.setContractNo(generateContractNo());
        // 自动计算
        double volume = contract.getEnergyVolume() != null ? contract.getEnergyVolume() : 0;
        double premium = contract.getPricePremium() != null ? contract.getPricePremium() : 0;
        contract.setTotalAmount(volume * premium);
        contract.setCarbonOffset(volume * CARBON_FACTOR);
        if (contract.getCertificateCount() == null) {
            contract.setCertificateCount((int) Math.round(volume / 1000.0));
        }
        contractRepository.save(contract);
        log.info("绿电合同创建成功: contractNo={}, enterpriseId={}, volume={}kWh",
                contract.getContractNo(), enterpriseId, volume);
        return contract.getContractNo();
    }

    /**
     * 更新合同（企业只能更新自己的草稿/进行中合同）
     */
    public void updateContract(String id, GreenPowerContract updates, String enterpriseId, boolean isAdmin) {
        GreenPowerContract contract = getContract(id, enterpriseId, isAdmin);
        if (updates.getSupplierName() != null) contract.setSupplierName(updates.getSupplierName());
        if (updates.getSupplierContact() != null) contract.setSupplierContact(updates.getSupplierContact());
        if (updates.getSupplierPhone() != null) contract.setSupplierPhone(updates.getSupplierPhone());
        if (updates.getEnergyVolume() != null) contract.setEnergyVolume(updates.getEnergyVolume());
        if (updates.getPricePremium() != null) contract.setPricePremium(updates.getPricePremium());
        if (updates.getStartDate() != null) contract.setStartDate(updates.getStartDate());
        if (updates.getEndDate() != null) contract.setEndDate(updates.getEndDate());
        if (updates.getStatus() != null) contract.setStatus(updates.getStatus());
        if (updates.getActualVolume() != null) {
            contract.setActualVolume(updates.getActualVolume());
            contract.setCarbonOffset(updates.getActualVolume() * CARBON_FACTOR);
        }
        if (updates.getCertificateStatus() != null) contract.setCertificateStatus(updates.getCertificateStatus());
        if (updates.getEstimatedSaving() != null) contract.setEstimatedSaving(updates.getEstimatedSaving());
        if (updates.getNotes() != null) contract.setNotes(updates.getNotes());
        // 重新计算总额
        if (updates.getEnergyVolume() != null || updates.getPricePremium() != null) {
            double volume = contract.getEnergyVolume() != null ? contract.getEnergyVolume() : 0;
            double premium = contract.getPricePremium() != null ? contract.getPricePremium() : 0;
            contract.setTotalAmount(volume * premium);
        }
        contractRepository.save(contract);
    }

    /**
     * 删除合同（仅管理员）
     */
    public void deleteContract(String id) {
        if (!contractRepository.existsById(id)) {
            throw new RuntimeException("绿电合同不存在");
        }
        contractRepository.deleteById(id);
    }

    /**
     * 企业侧汇总统计
     */
    public Map<String, Object> getSummary(String enterpriseId) {
        List<GreenPowerContract> contracts = contractRepository.findByEnterpriseIdAndStatus(enterpriseId, "active");
        if (contracts.isEmpty()) {
            contracts = contractRepository.findByEnterpriseId(enterpriseId, PageRequest.of(0, 100)).getContent();
        }
        double totalVolume = contracts.stream()
                .mapToDouble(c -> c.getEnergyVolume() != null ? c.getEnergyVolume() : 0).sum();
        double actualVolume = contracts.stream()
                .mapToDouble(c -> c.getActualVolume() != null ? c.getActualVolume() : 0).sum();
        double totalAmount = contracts.stream()
                .mapToDouble(c -> c.getTotalAmount() != null ? c.getTotalAmount() : 0).sum();
        double carbonOffset = contracts.stream()
                .mapToDouble(c -> c.getCarbonOffset() != null ? c.getCarbonOffset() : 0).sum();
        double saving = contracts.stream()
                .mapToDouble(c -> c.getEstimatedSaving() != null ? c.getEstimatedSaving() : 0).sum();
        int certificates = contracts.stream()
                .mapToInt(c -> c.getCertificateCount() != null ? c.getCertificateCount() : 0).sum();

        Map<String, Object> summary = new HashMap<>();
        summary.put("contractCount", contracts.size());
        summary.put("totalVolume", Math.round(totalVolume * 100.0) / 100.0);
        summary.put("actualVolume", Math.round(actualVolume * 100.0) / 100.0);
        summary.put("totalAmount", Math.round(totalAmount * 100.0) / 100.0);
        summary.put("carbonOffset", Math.round(carbonOffset * 100.0) / 100.0);
        summary.put("estimatedSaving", Math.round(saving * 100.0) / 100.0);
        summary.put("certificateCount", certificates);
        return summary;
    }

    /**
     * 平台侧：全平台绿电统计
     */
    public Map<String, Object> getPlatformSummary() {
        List<GreenPowerContract> all = contractRepository.findAll();
        double totalVolume = all.stream()
                .mapToDouble(c -> c.getEnergyVolume() != null ? c.getEnergyVolume() : 0).sum();
        double carbonOffset = all.stream()
                .mapToDouble(c -> c.getCarbonOffset() != null ? c.getCarbonOffset() : 0).sum();
        long activeCount = all.stream().filter(c -> "active".equals(c.getStatus())).count();
        Map<String, Object> summary = new HashMap<>();
        summary.put("totalContracts", all.size());
        summary.put("activeContracts", activeCount);
        summary.put("totalVolume", Math.round(totalVolume * 100.0) / 100.0);
        summary.put("totalCarbonOffset", Math.round(carbonOffset * 100.0) / 100.0);
        return summary;
    }

    private String generateContractNo() {
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        long count = contractRepository.count() + 1;
        return "GP-" + date + "-" + String.format("%04d", count);
    }
}
