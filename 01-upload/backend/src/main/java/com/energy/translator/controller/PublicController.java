package com.energy.translator.controller;

import com.energy.translator.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 公开接口（无需登录）
 * 小程序启动时调用 /api/public/version 进行版本对比，
 * 实现"服务端驱动的版本更新提示 + 更新说明 + 强制更新"。
 */
@RestController
@RequestMapping("/api/public")
public class PublicController {

    /**
     * 当前线上小程序最新版本信息。
     * 每次发布小程序新版本时，同步修改此处三个字段。
     */
    private static final String LATEST_VERSION = "1.4.0";
    private static final String MIN_SUPPORT_VERSION = "1.3.0";
    private static final String VERSION_NAME = "v1.4.0 绿电+VPP升级版";
    private static final String VERSION_NOTE =
            "1. 新增【绿电中心】：绿电合同台账、绿电交易记录查询\n" +
            "2. 新增【政策福利】：可申报政策自动匹配，补贴预估一目了然\n" +
            "3. 新增【VPP结算】：虚拟电厂结算单查询，收益明细透明\n" +
            "4. 修复：能耗趋势、告警统计等数据展示问题\n" +
            "5. 修复：若干已知Bug，体验更流畅";
    private static final boolean FORCE_UPDATE = false;

    @GetMapping("/version")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getVersion() {
        Map<String, Object> data = new HashMap<>();
        data.put("version", LATEST_VERSION);
        data.put("minVersion", MIN_SUPPORT_VERSION);
        data.put("name", VERSION_NAME);
        data.put("note", VERSION_NOTE);
        data.put("forceUpdate", FORCE_UPDATE);
        data.put("publishedAt", "2026-08-12");
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    @GetMapping("/health")
    public ResponseEntity<ApiResponse<Map<String, Object>>> health() {
        Map<String, Object> data = new HashMap<>();
        data.put("status", "UP");
        data.put("service", "energy-translator-backend");
        data.put("time", System.currentTimeMillis());
        return ResponseEntity.ok(ApiResponse.success(data));
    }
}
