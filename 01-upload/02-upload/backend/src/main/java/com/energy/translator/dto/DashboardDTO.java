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
public class DashboardDTO {
    private Integer score;
    private String todayUsage;
    private String todayCost;
    private String todayCarbon;
    private String todayRank;
    private String monthUsage;
    private String monthCost;
    private String monthCarbon;
    private TrendInfo todayTrend;
    private List<String> tips;
    private BannerTip bannerTip;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrendInfo {
        private String className;
        private String text;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BannerTip {
        private String title;
        private String desc;
    }
}
