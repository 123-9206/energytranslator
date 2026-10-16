package com.energy.translator.service;

import com.energy.translator.config.BillCheckProperties;
import com.energy.translator.dto.BillData;
import com.energy.translator.entity.BillCheckReport.Finding;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 报告生成：模板填数。数字全部来自校验通过的字段和规则引擎输出，
 * 本类不做任何"估算"之外的创作，不调用任何模型。
 */
@Service
@RequiredArgsConstructor
public class BillReportService {

    private final BillCheckProperties props;

    public String render(String factoryName, BillData d, List<Finding> findings) {
        StringBuilder sb = new StringBuilder();
        String name = (factoryName == null || factoryName.isBlank())
                ? (d.getCustomerName() == null ? "贵厂" : d.getCustomerName())
                : factoryName;

        sb.append("【电费单体检报告】").append(name);
        if (d.getBillMonth() != null) sb.append("（").append(d.getBillMonth()).append("账期）");
        sb.append("\n\n");

        sb.append("一、账单概览\n");
        if (d.getTotalKwh() != null) sb.append("· 总电量：").append(fmt0(d.getTotalKwh())).append(" kWh\n");
        if (d.getTotalAmount() != null) sb.append("· 总电费：").append(fmt0(d.getTotalAmount())).append(" 元\n");
        if (d.getTotalKwh() != null && d.getTotalAmount() != null && d.getTotalKwh() > 0) {
            sb.append("· 平均电价：").append(String.format("%.3f", d.getTotalAmount() / d.getTotalKwh())).append(" 元/kWh\n");
        }
        if (d.getBasicFee() != null && d.getTotalAmount() != null && d.getTotalAmount() > 0) {
            sb.append("· 基本电费占比：").append(String.format("%.0f%%", d.getBasicFee() / d.getTotalAmount() * 100)).append("\n");
        }
        sb.append("\n");

        List<Finding> actionable = findings.stream()
                .filter(f -> !"INFO".equals(f.getSeverity()))
                .toList();
        double totalSaving = findings.stream()
                .mapToDouble(f -> f.getMonthlySavingEstimate() == null ? 0 : f.getMonthlySavingEstimate())
                .sum();

        if (actionable.isEmpty()) {
            sb.append("二、体检结论\n本月账单未发现明显可优化项，用电结构整体健康。\n\n");
        } else {
            sb.append("二、发现 ").append(actionable.size()).append(" 个可优化点，合计月节省空间约 ")
                    .append(fmt0(totalSaving)).append(" 元\n\n");
            int idx = 1;
            for (Finding f : actionable) {
                sb.append(idx++).append(") ").append(f.getTitle());
                if (f.getMonthlySavingEstimate() != null && f.getMonthlySavingEstimate() > 0) {
                    sb.append("——月省约 ").append(fmt0(f.getMonthlySavingEstimate())).append(" 元");
                }
                sb.append("\n").append(f.getDetail()).append("\n");
                sb.append("（依据：").append(f.getBasis()).append("）\n\n");
            }
        }

        sb.append("三、下一步建议\n");
        sb.append("以上为单张账单的静态分析。装一只电表做持续监测后，可定位到具体设备、具体时段的浪费，")
                .append("并在电费异常时自动告警。\n\n");
        sb.append("数据来源：").append(props.getTariff().getSourceNote())
                .append("；账单字段经四时段电量、费用加总两道勾稽校验。");
        return sb.toString();
    }

    private String fmt0(double v) { return String.format("%,.0f", v); }
}
