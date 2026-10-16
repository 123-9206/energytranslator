package com.energy.translator.entity;

import com.energy.translator.dto.BillData;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "bill_check_reports")
public class BillCheckReport {

    public enum Status {
        READING,            // AI读单中
        PENDING_REVIEW,     // 已生成草稿，待人工复核
        NEEDS_MANUAL_READ,  // AI读单失败或勾稽校验不通过，需人工录入
        APPROVED,           // 复核通过
        DELIVERED,          // 已交付客户
        REJECTED            // 作废
    }

    @Id
    private String id;

    @Indexed
    private Status status;

    // ---- 客户留资 ----
    private String contactName;
    @Indexed
    private String contactPhone;
    private String factoryName;

    // ---- 图片 ----
    private String imagePath;
    private String imageOriginalName;

    // ---- AI读单结果 ----
    private BillData billData;
    private String aiModel;
    private String aiRawResponse;   // 原始返回，便于复核排错

    // ---- 勾稽校验 ----
    private Boolean validationPassed;
    private List<CheckItem> checkItems;

    // ---- 规则引擎分析 ----
    private List<Finding> findings;
    private Double totalMonthlySavingEstimate;

    // ---- 报告（模板填数生成，模型不生成数字） ----
    private String reportText;

    // ---- 复核 ----
    private String reviewerNote;
    private String reviewedBy;
    private Instant reviewedAt;

    @CreatedDate
    private Instant createdAt;
    @LastModifiedDate
    private Instant updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CheckItem {
        private String name;        // 校验项名称
        private String expected;    // 期望值
        private String actual;      // 实际值
        private Double diffPct;     // 偏差%
        private Boolean passed;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Finding {
        private String analyzer;    // 分析器: CAPACITY_DEMAND / PEAK_VALLEY / POWER_FACTOR / ANOMALY
        private String title;       // 结论标题
        private Double monthlySavingEstimate; // 月节省估算（元）
        private String basis;       // 数据来源行（用了哪些字段、哪个电价）
        private String detail;      // 说明
        private String severity;    // HIGH / MEDIUM / LOW / INFO
    }
}
