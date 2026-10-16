package com.energy.translator.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 电费体检配置。所有电价参数必须可由运维更新——电价表调整后只改配置不改代码。
 * ⚠️ tariff 下的默认值为江苏一般工商业参考值，正式上线前必须替换为用户提供的
 *    最新江苏目录电价表，并在报告中输出"来源行"。
 */
@Data
@Component
@ConfigurationProperties(prefix = "billcheck")
public class BillCheckProperties {

    /** 图片存储目录（服务器绝对路径） */
    private String uploadDir = "./data/uploads/bills";

    private Ai ai = new Ai();
    private Tariff tariff = new Tariff();

    @Data
    public static class Ai {
        /** OpenAI 兼容接口地址 */
        private String baseUrl = "https://api.siliconflow.cn/v1";
        /** 从环境变量注入，禁止硬编码 */
        private String apiKey = "";
        /** 多模态模型名 */
        private String model = "Qwen/Qwen3-VL-32B-Instruct";
        private Integer timeoutMs = 60000;
        /** 最大重试次数（读单失败时） */
        private Integer maxRetries = 2;
    }

    @Data
    public static class Tariff {
        // ---- 基本电价（两部制）----
        /** 按容量：元/kVA·月 */
        private Double capacityPricePerKva = 30.0;
        /** 按核定需量：元/kW·月 */
        private Double demandPricePerKw = 40.0;
        /** 需量计费下限系数（实际需量不足容量×该系数时按下限计） */
        private Double demandFloorRatio = 0.40;

        // ---- 电度电价（元/kWh，一般工商业 1-10kV，待用户电价表核实）----
        private Double tipPrice = 1.20;
        private Double peakPrice = 1.00;
        private Double flatPrice = 0.65;
        private Double valleyPrice = 0.35;

        /** 功率因数考核标准（大工业一般 0.90） */
        private Double powerFactorStandard = 0.90;

        /** 可转移负荷占平+峰电量的保守比例（估算谷段转移收益用） */
        private Double shiftableRatio = 0.10;

        /** 轻载判定：负载率低于该值提示减容/报停评估 */
        private Double lowLoadFactor = 0.30;

        /** 电价来源说明，原样输出到报告"来源行" */
        private String sourceNote = "江苏省一般工商业目录电价（待替换为用户提供的最新电价表后更新本说明）";
    }
}
