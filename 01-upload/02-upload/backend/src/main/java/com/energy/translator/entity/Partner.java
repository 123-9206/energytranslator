package com.energy.translator.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * 合作售电公司（持牌方）
 * 能耗翻译官不持售电牌照，通过与合作售电公司分润落地"省电费"合规能力。
 * enterpriseId 为空表示平台级通用合作方；否则仅对该企业生效。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "partners")
public class Partner {

    @Id
    private String id;

    /** 适用企业ID（空=平台通用） */
    private String enterpriseId;

    /** 合作售电公司名称 */
    private String partnerCompanyName;

    /** 牌照可开展省份，如 江苏 */
    private String licenseRegion;

    /** 售电牌照编号 */
    private String licenseNo;

    private String contactName;

    private String contactPhone;

    /** 平台留存分润比例 0-1（其余分给合作方） */
    private Double platformShareRatio;

    /** 状态：active */
    private String status;

    @CreatedDate
    private Instant createdAt;
}
