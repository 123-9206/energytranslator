package com.energy.translator.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CarbonReportDTO {
    private Double totalCarbon;
    private Double todayCarbon;
    private Double monthCarbon;
    private Double yearCarbon;
    private Double carbonIntensity;
    private String equivalentReduction;
    private List<CarbonTrend> trends;
    private List<CarbonSource> sources;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CarbonTrend {
        private String date;
        private Double value;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CarbonSource {
        private String name;
        private Double value;
        private Double percentage;
    }
}
