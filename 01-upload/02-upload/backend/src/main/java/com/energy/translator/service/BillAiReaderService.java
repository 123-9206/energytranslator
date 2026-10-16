package com.energy.translator.service;

import com.energy.translator.config.BillCheckProperties;
import com.energy.translator.dto.BillData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * AI读单服务。职责边界：只让多模态模型"抄字段"为 JSON，禁止模型做任何计算。
 * 模型返回无法解析时抛异常，由上层转人工录入（NEEDS_MANUAL_READ）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BillAiReaderService {

    private final BillCheckProperties props;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String READ_PROMPT = """
            你是电费单字段抄录员。请从这张电费单照片中抄录字段，输出严格 JSON，不要输出任何其他文字。
            规则：
            1. 只抄录，不计算。任何需要加减乘除才能得到的值，输出 null。
            2. 看不清或单据上没有的字段，输出 null。
            3. 数字只保留数值，不带单位、不带千分位逗号。
            4. meterType 只能是 TWO_PART（两部制）或 SINGLE（单一制）；pricingMode 只能是 CAPACITY（按容量）、DEMAND（按核定需量）、ACTUAL_DEMAND（按实际需量）。
            5. billMonth 格式 yyyy-MM。
            输出 JSON 结构：
            {"billMonth":"","customerName":"","customerNo":"","meterType":"","voltageLevel":"",
             "pricingMode":"","contractCapacityKva":null,"demandValueKw":null,"maxDemandKw":null,
             "tipKwh":null,"peakKwh":null,"flatKwh":null,"valleyKwh":null,"totalKwh":null,
             "basicFee":null,"energyFee":null,"powerFactorAdjustmentFee":null,"totalAmount":null,
             "powerFactor":null}
            """;

    public static class ReadResult {
        public final BillData data;
        public final String rawResponse;
        public ReadResult(BillData data, String rawResponse) {
            this.data = data;
            this.rawResponse = rawResponse;
        }
    }

    public ReadResult readBill(Path imagePath) throws Exception {
        BillCheckProperties.Ai ai = props.getAi();
        if (ai.getApiKey() == null || ai.getApiKey().isBlank()) {
            throw new IllegalStateException("billcheck.ai.api-key 未配置");
        }

        byte[] bytes = Files.readAllBytes(imagePath);
        String base64 = Base64.getEncoder().encodeToString(bytes);
        String mime = guessMime(imagePath);
        String dataUrl = "data:" + mime + ";base64," + base64;

        Map<String, Object> body = Map.of(
                "model", ai.getModel(),
                "temperature", 0,
                "messages", List.of(Map.of(
                        "role", "user",
                        "content", List.of(
                                Map.of("type", "text", "text", READ_PROMPT),
                                Map.of("type", "image_url", "image_url", Map.of("url", dataUrl))
                        )
                ))
        );

        Exception last = null;
        for (int attempt = 1; attempt <= ai.getMaxRetries(); attempt++) {
            try {
                String raw = callApi(body);
                String json = extractJson(raw);
                BillData data = objectMapper.readValue(json, BillData.class);
                return new ReadResult(data, raw);
            } catch (Exception e) {
                last = e;
                log.warn("AI读单第{}次失败: {}", attempt, e.getMessage());
            }
        }
        throw new RuntimeException("AI读单失败（已重试" + ai.getMaxRetries() + "次）: "
                + (last == null ? "未知原因" : last.getMessage()));
    }

    private String callApi(Map<String, Object> body) {
        BillCheckProperties.Ai ai = props.getAi();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(ai.getTimeoutMs());
        factory.setReadTimeout(ai.getTimeoutMs());
        RestTemplate rest = new RestTemplate(factory);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(ai.getApiKey());

        ResponseEntity<String> resp = rest.postForEntity(
                ai.getBaseUrl() + "/chat/completions",
                new HttpEntity<>(body, headers),
                String.class);

        if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
            throw new RuntimeException("AI接口返回异常: " + resp.getStatusCode());
        }
        try {
            JsonNode root = objectMapper.readTree(resp.getBody());
            return root.path("choices").get(0).path("message").path("content").asText();
        } catch (Exception e) {
            throw new RuntimeException("AI接口响应解析失败: " + e.getMessage());
        }
    }

    /** 模型可能包裹 ```json 代码块或前后多余文字，截取第一个 { 到最后一个 } */
    private String extractJson(String raw) {
        if (raw == null) throw new RuntimeException("AI返回为空");
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new RuntimeException("AI返回中未找到JSON");
        }
        return raw.substring(start, end + 1);
    }

    private String guessMime(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".webp")) return "image/webp";
        return "image/jpeg";
    }
}
