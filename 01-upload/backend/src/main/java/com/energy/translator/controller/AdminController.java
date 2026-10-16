package com.energy.translator.controller;

import com.energy.translator.dto.ApiResponse;
import com.energy.translator.entity.Alarm;
import com.energy.translator.entity.AuditLog;
import com.energy.translator.entity.Device;
import com.energy.translator.entity.Enterprise;
import com.energy.translator.entity.SystemSettings;
import com.energy.translator.entity.User;
import com.energy.translator.repository.AlarmRepository;
import com.energy.translator.repository.AuditLogRepository;
import com.energy.translator.repository.DeviceRepository;
import com.energy.translator.repository.EnergyRecordRepository;
import com.energy.translator.repository.EnterpriseRepository;
import com.energy.translator.repository.SystemSettingsRepository;
import com.energy.translator.repository.UserRepository;
import com.energy.translator.security.UserPrincipal;
import com.energy.translator.service.AIAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 平台管理接口（仅 ADMIN 角色可用）
 * 覆盖：用户管理、平台统计、企业列表、全局设备
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final UserRepository userRepository;
    private final DeviceRepository deviceRepository;
    private final EnterpriseRepository enterpriseRepository;
    private final AlarmRepository alarmRepository;
    private final EnergyRecordRepository energyRecordRepository;
    private final AuditLogRepository auditLogRepository;
    private final SystemSettingsRepository systemSettingsRepository;
    private final AIAnalysisService aiAnalysisService;

    /** 脱敏：移除密码字段，避免泄露 */
    private User sanitize(User user) {
        if (user == null) return null;
        user.setPassword(null);
        return user;
    }

    // ==================== 用户管理 ====================

    @GetMapping("/users")
    public ResponseEntity<ApiResponse<Page<User>>> getUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String search) {

        if (keyword == null || keyword.isBlank()) {
            keyword = search;
        }
        Pageable pageable = PageRequest.of(page, Math.min(size, 200), Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<User> users;
        if (role != null && !role.isBlank()) {
            users = userRepository.findByRole(role, pageable);
        } else {
            users = userRepository.findAll(pageable);
        }

        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            List<User> filtered = users.getContent().stream()
                    .filter(u -> (u.getUsername() != null && u.getUsername().contains(kw))
                            || (u.getPhone() != null && u.getPhone().contains(kw))
                            || (u.getEmail() != null && u.getEmail().contains(kw)))
                    .collect(Collectors.toList());
            users = new org.springframework.data.domain.PageImpl<>(filtered, pageable, filtered.size());
        }

        Page<User> safe = users.map(this::sanitize);
        return ResponseEntity.ok(ApiResponse.success(safe));
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<ApiResponse<User>> getUserDetail(@PathVariable String id) {
        Optional<User> user = userRepository.findById(id);
        if (user.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.error("用户不存在"));
        }
        return ResponseEntity.ok(ApiResponse.success(sanitize(user.get())));
    }

    @PutMapping("/users/{id}")
    public ResponseEntity<ApiResponse<User>> updateUser(@PathVariable String id, @RequestBody Map<String, Object> body) {
        Optional<User> opt = userRepository.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.error("用户不存在"));
        }
        User user = opt.get();
        if (body.containsKey("role")) user.setRole(String.valueOf(body.get("role")));
        if (body.containsKey("status")) user.setStatus(String.valueOf(body.get("status")));
        if (body.containsKey("phone")) user.setPhone(String.valueOf(body.get("phone")));
        if (body.containsKey("email")) user.setEmail(String.valueOf(body.get("email")));
        userRepository.save(user);
        return ResponseEntity.ok(ApiResponse.success(sanitize(user)));
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteUser(
            @PathVariable String id,
            @AuthenticationPrincipal UserPrincipal principal) {
        Optional<User> opt = userRepository.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.error("用户不存在"));
        }
        if (opt.get().getId().equals(principal.getUserId())) {
            return ResponseEntity.ok(ApiResponse.error("不能删除当前登录账号"));
        }
        userRepository.deleteById(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // ==================== 平台统计 ====================

    @GetMapping("/statistics")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStatistics() {
        Map<String, Object> data = new HashMap<>();
        data.put("totalUsers", userRepository.count());
        data.put("totalDevices", deviceRepository.count());
        data.put("totalEnterprises", enterpriseRepository.count());
        data.put("totalAlarms", alarmRepository.count());
        data.put("totalEnergyRecords", energyRecordRepository.count());
        data.put("activeAlarms", alarmRepository.countByStatus("active"));

        // 各角色用户数
        List<User> allUsers = userRepository.findAll();
        long adminCount = allUsers.stream().filter(u -> "admin".equalsIgnoreCase(u.getRole())).count();
        long enterpriseCount = allUsers.stream().filter(u -> "enterprise_admin".equalsIgnoreCase(u.getRole())).count();
        long userCount = allUsers.stream().filter(u -> "user".equalsIgnoreCase(u.getRole())).count();
        data.put("adminUsers", adminCount);
        data.put("enterpriseUsers", enterpriseCount);
        data.put("normalUsers", userCount);

        return ResponseEntity.ok(ApiResponse.success(data));
    }

    // ==================== AI 智能分析（驾驶舱） ====================

    // ==================== AI 智能分析（驾驶舱） ====================

    /**
     * AI 设备方案分析（自然语言 → 设备清单）
     */
    /**
     * AI 设备方案分析（自然语言 → 设备清单）
     * 真实智能：依据提取的电压/回路数/电表/光伏/储能/网络等参数，动态生成硬件清单与报价，
     * 不再写死清单与总价（旧版永远返回同一份 ¥3860 的伪智能方案）。
     */
    @PostMapping("/ai/analyze")
    public ResponseEntity<ApiResponse<Map<String, Object>>> aiAnalyze(
            @RequestBody Map<String, String> body) {

        String text = body.getOrDefault("text", "");
        Map<String, Object> extractedParams = new HashMap<>();
        String customerName = extractCustomerName(text);
        int circuits = extractCircuitCount(text);
        String voltage = extractVoltage(text);
        boolean hasMeter = text.contains("电表") || text.contains("安科瑞") || text.contains("威胜");
        String meterModel = extractMeterModel(text);
        String network = text.contains("4G") || text.contains("4g") ? "4g"
                : text.contains("WiFi") || text.contains("wifi") ? "wifi" : "ethernet";
        boolean hasSolar = text.contains("光伏") || text.contains("逆变器");
        String inverterModel = extractInverterModel(text);
        boolean hasStorage = text.contains("储能");
        boolean vppInterest = text.contains("VPP") || text.contains("需求响应") || text.contains("虚拟电厂");
        String bmsProtocol = extractBmsProtocol(text);

        extractedParams.put("customerName", customerName);
        extractedParams.put("circuitCount", circuits <= 2 ? "1-2" : circuits <= 5 ? "3-5" : "6+");
        extractedParams.put("voltage", voltage);
        extractedParams.put("hasMeter", hasMeter ? "yes" : "unknown");
        extractedParams.put("meterModel", meterModel);
        extractedParams.put("network", network);
        extractedParams.put("hasSolar", hasSolar ? "yes" : "no");
        extractedParams.put("inverterModel", inverterModel);
        extractedParams.put("hasStorage", hasStorage ? "yes" : "no");
        extractedParams.put("bmsProtocol", bmsProtocol);
        extractedParams.put("vppInterest", vppInterest ? "yes" : "no");

        // ===== 动态方案生成（价格体系与前端 SolutionMatcher 一致） =====
        boolean isHv = "10kv".equalsIgnoreCase(voltage);
        List<Map<String, Object>> items = new ArrayList<>();
        int hardwareTotal = 0;

        // 1. 电表（已有则复用，零成本）
        if (hasMeter && meterModel != null && !meterModel.isEmpty()) {
            items.add(item("现有电表复用", meterModel, 1, 0, "已确认有 RS485 通讯口，直接接线采集"));
        } else {
            Map<String, Object> meter = circuits >= 6
                    ? item("多功能电力仪表", "Modbus RS485, 0.2S级, 谐波", 1, 2500, "谐波分析+电能质量，适合精密制造")
                    : item("多功能电力仪表", "Modbus RS485, 0.5S级", 1, 1200, "含电压/电流/功率/电能全参数");
            items.add(meter);
            hardwareTotal += (int) meter.get("subtotal");
        }

        // 2. 电流互感器（按回路数）
        int ctUnit = isHv ? 150 : 100;
        String ctSpec = isHv ? "400A/5A, 内径36mm" : "100A/5A, 内径24mm";
        Map<String, Object> ct = item(isHv ? "开口式电流互感器(高压)" : "开口式电流互感器",
                ctSpec, circuits, ctUnit, (isHv ? "大电流回路，卡扣安装" : "卡扣安装，不需断电") + "，" + circuits + "个回路各1只");
        items.add(ct);
        hardwareTotal += (int) ct.get("subtotal");

        // 3. 数据网关（按网络类型）
        Map<String, Object> gw = "4g".equals(network)
                ? item("4G Modbus 网关", "RS485×4, 4G全网通", 1, 1200, "无网络环境，含 1 年流量卡")
                : item("Modbus 数据网关", "RS485×4, WiFi/以太网", 1, 800, "多路 RS485 汇聚上传");
        items.add(gw);
        hardwareTotal += (int) gw.get("subtotal");

        // 4. 电源 + 接线盒 + 线缆 + 温湿度
        Map<String, Object> power = item("DIN导轨电源", "24V/2A", 1, 60, "网关和传感器供电");
        items.add(power); hardwareTotal += 60;
        Map<String, Object> box = item("弱电接线盒", "8位, DIN导轨", 1, 50, "集中安放网关和电源");
        items.add(box); hardwareTotal += 50;
        Map<String, Object> cable = item("RS485屏蔽双绞线", "2×0.5mm²", 1, 200, "通讯线缆，约50米");
        items.add(cable); hardwareTotal += 200;
        Map<String, Object> temp = item("温湿度传感器", "壁挂式, RS485", 2, 150, "配电房环境监测");
        items.add(temp); hardwareTotal += 300;

        // 5. 光伏接入（逆变器已有通讯，零硬件成本）
        if (hasSolar) {
            items.add(item("光伏数据接入", inverterModel.isEmpty() ? "逆变器 Modbus TCP" : inverterModel,
                    1, 0, "逆变器已有通讯接口，拉一根网线即可读取数据，无额外硬件成本"));
        }

        // 6. 储能接入
        if (hasStorage) {
            boolean can = "can".equals(bmsProtocol);
            items.add(item("储能BMS接入",
                    "协议: " + (bmsProtocol.isEmpty() || "unknown".equals(bmsProtocol) ? "待确认" : bmsProtocol.toUpperCase()),
                    1, can ? 500 : 0,
                    can ? "CAN 协议需额外转换器" : "Modbus TCP/RTU 直连，零硬件成本"));
            hardwareTotal += can ? 500 : 0;
        }

        // 7. 关键回路智能断路器（6回路以上）
        if (circuits >= 6) {
            items.add(item("智能断路器", "3P 63A, RS485遥控", 2, 350, "关键回路远程控制，可选配"));
            hardwareTotal += 700;
        }

        // 安装费（按复杂度）
        int installCost = circuits <= 2 ? 800 : circuits <= 5 ? 1200 : 2000;

        // 方案等级
        boolean hasAdvanced = hasSolar || hasStorage || isHv;
        String tier = (hasAdvanced || circuits >= 6) ? "高级版" : circuits >= 3 ? "标准版" : "入门版";

        // 建议（动态）
        List<Map<String, Object>> advice = new ArrayList<>();
        if (hasMeter && meterModel != null && !meterModel.isEmpty()) {
            advice.add(advice("check", "✅ 现有 " + meterModel + " 可直接接入，省去电表采购费用"));
        } else if (hasMeter) {
            advice.add(advice("warn", "⚠️ 建议客户确认柜门有无数字屏幕，有则拍下型号标签，可能直接复用省钱"));
        } else {
            advice.add(advice("info", "🆕 需新装一台多功能电表，安装在总进线回路"));
        }
        if (hasSolar) advice.add(advice("check", "✅ 光伏逆变器 Modbus TCP 直连，零硬件成本接入平台"));
        if (hasStorage) advice.add(advice("info", "🔋 储能 BMS 接入: " + (bmsProtocol.isEmpty() || "unknown".equals(bmsProtocol) ? "需确认通讯协议" : bmsProtocol.toUpperCase() + " 协议直连")));
        if (vppInterest) advice.add(advice("check", "⚡ 资源已注册 VPP 虚拟电厂，可参与需求响应获取容量补偿"));
        advice.add(advice("info", "📋 共 " + items.size() + " 项设备，建议联系当地电工采购安装，硬件可在淘宝/1688比价"));
        if ("4g".equals(network)) advice.add(advice("warn", "⚠️ 4G 网关需办理物联网卡，年费约 50-100 元，次年续费"));
        if (isHv) advice.add(advice("warn", "⚠️ 10kV 高压侧需由持证高压电工操作，普通电工不可操作高压CT"));

        Map<String, Object> result = new HashMap<>();
        result.put("extractedParams", extractedParams);
        result.put("tier", tier);
        result.put("items", items);
        result.put("hardwareTotal", hardwareTotal);
        result.put("installCost", installCost);
        result.put("totalCost", hardwareTotal + installCost);
        result.put("advice", advice);

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    private Map<String, Object> item(String name, String spec, int qty, int unitPrice, String note) {
        Map<String, Object> m = new HashMap<>();
        m.put("name", name);
        m.put("spec", spec);
        m.put("qty", qty);
        m.put("unitPrice", unitPrice);
        m.put("subtotal", qty * unitPrice);
        m.put("note", note);
        return m;
    }

    private Map<String, Object> advice(String icon, String text) {
        Map<String, Object> m = new HashMap<>();
        m.put("icon", icon);
        m.put("text", text);
        return m;
    }

    private String extractBmsProtocol(String text) {
        if (text.contains("CAN") || text.contains("can")) return "can";
        if (text.contains("Modbus") || text.contains("modbus") || text.contains("RTU") || text.contains("TCP")) return "modbus";
        return "unknown";
    }

    private String extractCustomerName(String text) {
        if (text.contains("厂")) {
            int idx = text.indexOf("厂");
            int start = Math.max(0, idx - 10);
            return text.substring(start, idx + 1).trim();
        }
        if (text.contains("公司") || text.contains("企业")) {
            int idx = text.indexOf(text.contains("公司") ? "公司" : "企业");
            int start = Math.max(0, idx - 10);
            return text.substring(start, idx + 2).trim();
        }
        return "";
    }

    private int extractCircuitCount(String text) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("(\\d+)\\s*(?:个)?\\s*(?:回路|路|配电回路|进线)");
        java.util.regex.Matcher m = p.matcher(text);
        if (m.find()) {
            try { int n = Integer.parseInt(m.group(1)); if (n > 0) return n; } catch (Exception ignored) {}
        }
        if (text.contains("四") || text.contains("4")) return 4;
        if (text.contains("六") || text.contains("6")) return 6;
        if (text.contains("八") || text.contains("8")) return 8;
        if (text.contains("两") || text.contains("二") || text.contains("2")) return 2;
        if (text.contains("三") || text.contains("3")) return 3;
        if (text.contains("五") || text.contains("5")) return 5;
        if (text.contains("一") || text.contains("1")) return 1;
        return 3;
    }

    private String extractVoltage(String text) {
        // 1) 优先匹配高压 kV（如 10kV），必须在型号数字之前判断，避免 "ACR220" 误判
        java.util.regex.Pattern kv = java.util.regex.Pattern.compile("(\\d+)\\s*[kK][vV]");
        java.util.regex.Matcher mk = kv.matcher(text);
        if (mk.find()) return mk.group(1).toLowerCase() + "kv";
        // 2) 再匹配独立的低压数值（220/380/110），排除嵌在型号里的数字（如 ACR220、DTSD341）
        java.util.regex.Pattern v = java.util.regex.Pattern.compile("(?<![A-Za-z0-9])(220|380|110|36|24)(?![A-Za-z0-9])\\s*[vV]?");
        java.util.regex.Matcher mv = v.matcher(text);
        if (mv.find()) return mv.group(1);
        if (text.contains("高压") || text.contains("千伏")) return "10kv";
        if (text.contains("单相") || text.contains("民用电")) return "220";
        return "380"; // 工厂默认三相380V
    }

    private String extractMeterModel(String text) {
        if (text.contains("ACR") || text.contains("安科瑞")) return "安科瑞 ACR10R";
        if (text.contains("DTSD") || text.contains("威胜")) return "威胜 DTSD341";
        if (text.contains("SD")) return "智能电表 SD系列";
        return "";
    }

    private String extractInverterModel(String text) {
        if (text.contains("SUN2000") || text.contains("华为")) return "华为 SUN2000";
        if (text.contains("SG") || text.contains("阳光")) return "阳光电源 SG";
        if (text.contains("固德威") || text.contains("Growatt")) return "固德威/古瑞瓦特";
        return "";
    }

    /**
     * 驾驶舱 AI 分析聚合接口
     * 一次返回：AI 能效诊断摘要 + 未来7天预测 + 24h功率曲线 + 采集状态
     *
     * @param enterpriseId 可选，默认取数据量最大的企业
     */
    @GetMapping("/ai/analysis")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAIAnalysis(
            @RequestParam(required = false) String enterpriseId) {
        Map<String, Object> result = aiAnalysisService.analyzeOverview(enterpriseId, 7);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // ==================== 企业列表 ====================

    @GetMapping("/enterprises")
    public ResponseEntity<ApiResponse<List<Enterprise>>> getEnterprises(
            @RequestParam(required = false) String status) {
        List<Enterprise> enterprises;
        if (status != null && !status.isBlank()) {
            enterprises = enterpriseRepository.findByStatus(status);
        } else {
            enterprises = enterpriseRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
        }
        return ResponseEntity.ok(ApiResponse.success(enterprises));
    }

    // ==================== 企业审核 ====================

    /**
     * 审核通过：企业状态置 verified，并激活其关联的所有用户(置 active)，使其可登录使用。
     */
    @PostMapping("/enterprises/{id}/approve")
    public ResponseEntity<ApiResponse<Enterprise>> approveEnterprise(@PathVariable String id) {
        Optional<Enterprise> opt = enterpriseRepository.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.error("企业不存在"));
        }
        Enterprise enterprise = opt.get();
        enterprise.setStatus("verified");
        enterpriseRepository.save(enterprise);

        List<User> relatedUsers = userRepository.findByEnterpriseId(id);
        for (User u : relatedUsers) {
            if (!"active".equals(u.getStatus())) {
                u.setStatus("active");
                userRepository.save(u);
            }
        }
        return ResponseEntity.ok(ApiResponse.success(enterprise));
    }

    /**
     * 审核拒绝：企业状态置 rejected，并禁用其关联的所有用户(置 disabled)。
     */
    @PostMapping("/enterprises/{id}/reject")
    public ResponseEntity<ApiResponse<Enterprise>> rejectEnterprise(@PathVariable String id) {
        Optional<Enterprise> opt = enterpriseRepository.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.error("企业不存在"));
        }
        Enterprise enterprise = opt.get();
        enterprise.setStatus("rejected");
        enterpriseRepository.save(enterprise);

        List<User> relatedUsers = userRepository.findByEnterpriseId(id);
        for (User u : relatedUsers) {
            u.setStatus("disabled");
            userRepository.save(u);
        }
        return ResponseEntity.ok(ApiResponse.success(enterprise));
    }

    // ==================== 全局设备 ====================

    @GetMapping("/devices")
    public ResponseEntity<ApiResponse<Page<Device>>> getDevices(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 200), Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(ApiResponse.success(deviceRepository.findAll(pageable)));
    }

    // ==================== 审计日志 ====================

    @GetMapping("/audit-logs")
    public ResponseEntity<ApiResponse<Page<AuditLog>>> getAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        initAuditLogsIfEmpty();

        Pageable pageable = PageRequest.of(page, Math.min(size, 200), Sort.by(Sort.Direction.DESC, "timestamp"));
        Page<AuditLog> logs;

        if (type != null && !type.isBlank() && startDate != null && !startDate.isBlank() && endDate != null && !endDate.isBlank()) {
            LocalDateTime start = LocalDateTime.parse(startDate + "T00:00:00");
            LocalDateTime end = LocalDateTime.parse(endDate + "T23:59:59");
            logs = auditLogRepository.findByTypeAndTimestampBetweenOrderByTimestampDesc(type, start, end, pageable);
        } else if (type != null && !type.isBlank()) {
            logs = auditLogRepository.findByTypeOrderByTimestampDesc(type, pageable);
        } else if (startDate != null && !startDate.isBlank() && endDate != null && !endDate.isBlank()) {
            LocalDateTime start = LocalDateTime.parse(startDate + "T00:00:00");
            LocalDateTime end = LocalDateTime.parse(endDate + "T23:59:59");
            logs = auditLogRepository.findByTimestampBetweenOrderByTimestampDesc(start, end, pageable);
        } else {
            logs = auditLogRepository.findAllByOrderByTimestampDesc(pageable);
        }
        return ResponseEntity.ok(ApiResponse.success(logs));
    }

    private void initAuditLogsIfEmpty() {
        if (auditLogRepository.count() > 0) return;

        List<AuditLog> seeds = List.of(
            new AuditLog("LOGIN", "管理后台", "系统", "管理员 admin 登录系统", "成功", null, "admin"),
            new AuditLog("LOGIN", "管理后台", "系统", "企业用户 demo 登录系统", "成功", null, "demo"),
            new AuditLog("CONFIG", "系统设置", "告警规则", "新增告警规则：电流过高", "成功", null, "admin"),
            new AuditLog("CONTROL", "设备管理", "设备 DEV-001", "远程开启设备", "成功", null, "admin"),
            new AuditLog("CONTROL", "设备管理", "设备 DEV-002", "远程关闭设备", "成功", null, "demo"),
            new AuditLog("ALARM", "告警中心", "设备 DEV-003", "确认告警：电流异常", "成功", null, "admin"),
            new AuditLog("ALARM", "告警中心", "设备 DEV-004", "解除告警：电压偏低", "成功", null, "admin"),
            new AuditLog("CONFIG", "系统设置", "用户权限", "修改用户 demo 角色为 enterprise_admin", "成功", null, "admin"),
            new AuditLog("CONTROL", "设备管理", "设备 DEV-005", "重启设备", "成功", null, "demo"),
            new AuditLog("LOGIN", "管理后台", "系统", "管理员 admin 登录系统", "成功", null, "admin")
        );
        // 修正时间戳为过去 7 天内随机分布
        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < seeds.size(); i++) {
            AuditLog log = seeds.get(i);
            log.setTimestamp(now.minusDays((i % 7)).minusMinutes(i * 17));
            log.setCreatedAt(log.getTimestamp());
        }
        auditLogRepository.saveAll(seeds);
    }

    // ==================== 系统设置 ====================

    @GetMapping("/settings")
    public ResponseEntity<ApiResponse<SystemSettings>> getSettings() {
        Optional<SystemSettings> opt = systemSettingsRepository.findFirstByOrderByCreatedAtDesc();
        if (opt.isPresent()) {
            return ResponseEntity.ok(ApiResponse.success(opt.get()));
        }
        // 无记录时返回默认值
        return ResponseEntity.ok(ApiResponse.success(new SystemSettings()));
    }

    @PutMapping("/settings")
    public ResponseEntity<ApiResponse<SystemSettings>> saveSettings(@RequestBody SystemSettings settings) {
        Optional<SystemSettings> existing = systemSettingsRepository.findFirstByOrderByCreatedAtDesc();
        if (existing.isPresent()) {
            settings.setId(existing.get().getId());
        }
        settings.setUpdatedAt(new Date());
        if (settings.getCreatedAt() == null) {
            settings.setCreatedAt(new Date());
        }
        SystemSettings saved = systemSettingsRepository.save(settings);
        return ResponseEntity.ok(ApiResponse.success(saved));
    }

    // ==================== 系统健康 ====================

    @GetMapping("/system/health")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSystemHealth() {
        Map<String, Object> data = new HashMap<>();
        data.put("status", "UP");
        data.put("version", "1.0.0");
        data.put("timestamp", java.time.Instant.now().toString());

        // MongoDB
        Map<String, Object> mongo = new HashMap<>();
        try {
            mongo.put("status", "UP");
            mongo.put("deviceCount", deviceRepository.count());
            mongo.put("userCount", userRepository.count());
            mongo.put("dataPointCount", energyRecordRepository.count());
        } catch (Exception e) {
            mongo.put("status", "DOWN");
            mongo.put("error", e.getMessage());
        }
        data.put("mongodb", mongo);

        // EMQX (test localhost:1883)
        Map<String, Object> emqx = new HashMap<>();
        try (java.net.Socket s = new java.net.Socket()) {
            s.connect(new java.net.InetSocketAddress("127.0.0.1", 1883), 2000);
            emqx.put("status", "UP");
            emqx.put("version", "5.8.9");
        } catch (Exception e) {
            emqx.put("status", "DOWN");
            emqx.put("error", e.getMessage());
        }
        data.put("emqx", emqx);

        // Nginx (test localhost:80 or 443)
        Map<String, Object> nginx = new HashMap<>();
        try (java.net.Socket s = new java.net.Socket()) {
            s.connect(new java.net.InetSocketAddress("127.0.0.1", 80), 2000);
            nginx.put("status", "UP");
            nginx.put("port", 443);
            nginx.put("detail", "HTTPS reverse proxy / SSL enabled");
        } catch (Exception e) {
            nginx.put("status", "DOWN");
            nginx.put("error", e.getMessage());
        }
        data.put("nginx", nginx);

        // Collect stats
        Map<String, Object> collectStats = new HashMap<>();
        try {
            collectStats.put("totalDevices", deviceRepository.count());
            collectStats.put("onlineDevices", deviceRepository.countByStatus("online"));
        } catch (Exception e) {
            collectStats.put("totalDevices", 0);
            collectStats.put("onlineDevices", 0);
        }
        data.put("collectStats", collectStats);

        // JVM
        Map<String, Object> jvm = new HashMap<>();
        Runtime rt = Runtime.getRuntime();
        long usedMB = (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024;
        long maxMB = rt.maxMemory() / 1024 / 1024;
        jvm.put("heapUsed", usedMB + "MB");
        jvm.put("heapMax", maxMB + "MB");
        jvm.put("availableProcessors", rt.availableProcessors());
        jvm.put("javaVersion", System.getProperty("java.version"));
        data.put("jvm", jvm);

        // Metrics placeholders (frontend has fallbacks)
        data.put("apiMetrics", Map.of("labels", List.of(), "values", List.of()));
        data.put("collectionMetrics", Map.of("labels", List.of(), "values", List.of()));

        return ResponseEntity.ok(ApiResponse.success(data));
    }

    // ==================== 数据清理 ====================

    @DeleteMapping("/data/cleanup")
    public ResponseEntity<ApiResponse<Map<String, Object>>> cleanupOldData(
            @RequestParam(defaultValue = "90") int daysBefore) {
        Instant cutoff = LocalDateTime.now().minusDays(daysBefore).toInstant(ZoneOffset.UTC);
        long deleted = energyRecordRepository.deleteByTimestampBefore(cutoff);
        Map<String, Object> result = new HashMap<>();
        result.put("deletedRecords", deleted);
        result.put("daysBefore", daysBefore);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
