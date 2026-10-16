package com.energy.translator.service;

import com.energy.translator.config.BillCheckProperties;
import com.energy.translator.dto.BillData;
import com.energy.translator.entity.BillCheckReport;
import com.energy.translator.repository.BillCheckReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 电费体检编排：上传 → AI读单 → 勾稽校验 → 规则引擎 → 报告草稿 → 人工复核。
 * 任何一步失败都不丢单，转入 NEEDS_MANUAL_READ 由人工兜底。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BillCheckService {

    private static final Set<String> ALLOWED_EXT = Set.of(".jpg", ".jpeg", ".png", ".webp");
    private static final long MAX_SIZE = 10 * 1024 * 1024;

    private final BillCheckReportRepository repository;
    private final BillAiReaderService aiReader;
    private final BillValidationService validationService;
    private final BillRuleEngineService ruleEngine;
    private final BillReportService reportService;
    private final BillCheckProperties props;

    public BillCheckReport submit(MultipartFile file, String contactName, String contactPhone, String factoryName) {
        validateFile(file);
        String saved = saveFile(file);

        BillCheckReport report = BillCheckReport.builder()
                .status(BillCheckReport.Status.READING)
                .contactName(contactName)
                .contactPhone(contactPhone)
                .factoryName(factoryName)
                .imagePath(saved)
                .imageOriginalName(file.getOriginalFilename())
                .build();
        report = repository.save(report);

        try {
            // 第1步：AI读单（只抄不算）
            BillAiReaderService.ReadResult rr = aiReader.readBill(Paths.get(saved));
            report.setBillData(rr.data);
            report.setAiRawResponse(rr.rawResponse);
            report.setAiModel(props.getAi().getModel());

            // 第2步：勾稽校验（±1%）
            BillValidationService.ValidationResult vr = validationService.validate(rr.data);
            report.setCheckItems(vr.items);
            report.setValidationPassed(vr.passed);

            if (!vr.passed) {
                report.setStatus(BillCheckReport.Status.NEEDS_MANUAL_READ);
                log.info("体检单 {} 勾稽校验未通过，转人工", report.getId());
                return repository.save(report);
            }

            // 第3步：规则引擎 + 报告草稿
            runAnalysis(report);
            report.setStatus(BillCheckReport.Status.PENDING_REVIEW);
        } catch (Exception e) {
            log.error("体检单 {} 处理失败: {}", report.getId(), e.getMessage());
            report.setStatus(BillCheckReport.Status.NEEDS_MANUAL_READ);
        }
        return repository.save(report);
    }

    /** 人工录入/修正账单数据后重跑分析与报告（admin 复核入口） */
    public BillCheckReport rerunWithData(String id, BillData data) {
        BillCheckReport report = get(id);
        report.setBillData(data);
        BillValidationService.ValidationResult vr = validationService.validate(data);
        report.setCheckItems(vr.items);
        report.setValidationPassed(vr.passed);
        runAnalysis(report);
        report.setStatus(BillCheckReport.Status.PENDING_REVIEW);
        return repository.save(report);
    }

    private void runAnalysis(BillCheckReport report) {
        List<BillCheckReport.Finding> findings = ruleEngine.analyze(report.getBillData());
        report.setFindings(findings);
        report.setTotalMonthlySavingEstimate(findings.stream()
                .mapToDouble(f -> f.getMonthlySavingEstimate() == null ? 0 : f.getMonthlySavingEstimate())
                .sum());
        report.setReportText(reportService.render(report.getFactoryName(), report.getBillData(), findings));
    }

    // ---- admin 操作 ----

    public Page<BillCheckReport> list(BillCheckReport.Status status, int page, int size) {
        PageRequest pr = PageRequest.of(page, size);
        return status == null
                ? repository.findAllByOrderByCreatedAtDesc(pr)
                : repository.findByStatusOrderByCreatedAtDesc(status, pr);
    }

    public BillCheckReport get(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("体检单不存在: " + id));
    }

    public BillCheckReport approve(String id, String reviewer, String note, String finalReportText) {
        BillCheckReport report = get(id);
        if (finalReportText != null && !finalReportText.isBlank()) {
            report.setReportText(finalReportText);
        }
        report.setStatus(BillCheckReport.Status.APPROVED);
        report.setReviewedBy(reviewer);
        report.setReviewerNote(note);
        report.setReviewedAt(Instant.now());
        return repository.save(report);
    }

    public BillCheckReport deliver(String id) {
        BillCheckReport report = get(id);
        if (report.getStatus() != BillCheckReport.Status.APPROVED) {
            throw new IllegalStateException("仅复核通过的体检单可交付");
        }
        report.setStatus(BillCheckReport.Status.DELIVERED);
        return repository.save(report);
    }

    public BillCheckReport reject(String id, String reviewer, String note) {
        BillCheckReport report = get(id);
        report.setStatus(BillCheckReport.Status.REJECTED);
        report.setReviewedBy(reviewer);
        report.setReviewerNote(note);
        report.setReviewedAt(Instant.now());
        return repository.save(report);
    }

    // ---- 客户端查询：手机号+单号，仅交付后可见报告 ----

    public BillCheckReport queryForCustomer(String id, String phone) {
        BillCheckReport report = get(id);
        if (report.getContactPhone() == null || !report.getContactPhone().equals(phone)) {
            throw new SecurityException("手机号与体检单不匹配");
        }
        return report;
    }

    // ---- 文件 ----

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("请上传电费单照片");
        if (file.getSize() > MAX_SIZE) throw new IllegalArgumentException("照片不能超过10MB");
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        boolean ok = ALLOWED_EXT.stream().anyMatch(name::endsWith);
        if (!ok) throw new IllegalArgumentException("仅支持 jpg/png/webp 图片");
    }

    /**
     * 体检漏斗统计（admin 看板）：
     * 已上传 → AI读单成功 → 复核通过 → 已交付。读单失败/复核中单独计数。
     */
    public Map<String, Object> funnel() {
        long uploaded = repository.count();
        long reading = repository.countByStatus(BillCheckReport.Status.READING);
        long pendingReview = repository.countByStatus(BillCheckReport.Status.PENDING_REVIEW);
        long needsManual = repository.countByStatus(BillCheckReport.Status.NEEDS_MANUAL_READ);
        long approved = repository.countByStatus(BillCheckReport.Status.APPROVED);
        long delivered = repository.countByStatus(BillCheckReport.Status.DELIVERED);
        long rejected = repository.countByStatus(BillCheckReport.Status.REJECTED);

        long readOk = pendingReview + approved + delivered + rejected; // 走到复核环节的均视为读单成功
        long reviewPassed = approved + delivered;

        // 已交付单的平均月节省估算
        List<BillCheckReport> deliveredList = repository.findByStatusIn(
                List.of(BillCheckReport.Status.DELIVERED, BillCheckReport.Status.APPROVED));
        double totalSaving = 0;
        int savingCount = 0;
        for (BillCheckReport r : deliveredList) {
            if (r.getTotalMonthlySavingEstimate() != null) {
                totalSaving += r.getTotalMonthlySavingEstimate();
                savingCount++;
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("uploaded", uploaded);
        result.put("reading", reading);
        result.put("readOk", readOk);
        result.put("needsManualRead", needsManual);
        result.put("pendingReview", pendingReview);
        result.put("reviewPassed", reviewPassed);
        result.put("delivered", delivered);
        result.put("rejected", rejected);
        result.put("readOkRate", uploaded > 0 ? round2(readOk * 100.0 / uploaded) : 0);
        result.put("reviewPassRate", readOk > 0 ? round2(reviewPassed * 100.0 / readOk) : 0);
        result.put("deliverRate", reviewPassed > 0 ? round2(delivered * 100.0 / reviewPassed) : 0);
        result.put("avgMonthlySaving", savingCount > 0 ? round2(totalSaving / savingCount) : 0);
        return result;
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private String saveFile(MultipartFile file) {
        try {
            Path dir = Paths.get(props.getUploadDir());
            Files.createDirectories(dir);
            String name = file.getOriginalFilename().toLowerCase();
            String ext = name.substring(name.lastIndexOf('.'));
            Path target = dir.resolve(UUID.randomUUID() + ext);
            file.transferTo(target);
            return target.toString();
        } catch (IOException e) {
            throw new RuntimeException("照片保存失败: " + e.getMessage());
        }
    }
}
