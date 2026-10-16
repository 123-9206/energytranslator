package com.energy.translator.service;

import com.energy.translator.entity.*;
import com.energy.translator.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.*;
import java.util.*;

/**
 * 种子数据初始化器
 * 在应用启动时自动填充合成演示数据，让小程序和管理后台立即可用。
 * 
 * 仅在 seed.enabled=true 且不是 prod/test profile 时执行；所有记录均为合成示例。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Profile("!prod & !test")
public class SeedDataRunner implements CommandLineRunner {

    private final UserRepository userRepository;
    private final EnterpriseRepository enterpriseRepository;
    private final DeviceRepository deviceRepository;
    private final AlarmRuleRepository alarmRuleRepository;
    private final EnergyRecordRepository energyRecordRepository;
    private final VppResourceRepository vppResourceRepository;
    private final VppActivityRepository vppActivityRepository;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Value("${seed.enabled:false}")
    private boolean seedEnabled;

    @Override
    public void run(String... args) {
        if (!seedEnabled) {
            log.info("种子数据已禁用 (seed.enabled=false)");
            return;
        }

        ensureDemoAccount();

        // 检查是否已有数据
        long deviceCount = deviceRepository.count();
        if (deviceCount > 0 && deviceCount >= 3) {
            log.info("数据库已有{}台设备数据，跳过种子数据初始化", deviceCount);
            return;
        }

        log.info("开始初始化演示数据...");

        try {
            String enterpriseId = seedEnterprise();
            List<Device> devices = seedDevices(enterpriseId);
            seedEnergyRecords(enterpriseId, devices);
            seedAlarmRules(enterpriseId, devices);
            seedVppResources(enterpriseId);
            seedVppActivities();

            log.info("演示数据初始化完成！共创建: 企业1家, 设备{}台, 能耗记录30天(含电流/电压/温度/功率因数), 告警规则{}条(含电路安全预警), VPP资源3个, VPP活动2个",
                    devices.size(), 11);
        } catch (Exception e) {
            log.error("种子数据初始化失败: {}", e.getMessage(), e);
        }
    }

    /** Create a synthetic demo account once; preserve existing users and passwords. */
    private void ensureDemoAccount() {
        if (userRepository.findByUsername("demo").isPresent()) {
            return;
        }
        String enterpriseId = seedEnterprise();
        User demoUser = new User();
        demoUser.setUsername("demo");
        // No published password. The optional demo-login endpoint is configured separately.
        demoUser.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        demoUser.setEnterpriseId(enterpriseId);
        demoUser.setRole("enterprise_admin");
        demoUser.setPermissions(List.of("read", "write"));
        demoUser.setStatus("active");
        userRepository.save(demoUser);
        log.info("Created synthetic demo account (no credentials logged)");
    }

    /**
     * 创建演示企业
     */
    private String seedEnterprise() {
        // 检查是否已存在
        List<Enterprise> existing = enterpriseRepository.findByName("示例制造工厂（合成数据）");
        if (!existing.isEmpty()) {
            return existing.get(0).getId();
        }

        Enterprise enterprise = new Enterprise();
        enterprise.setName("示例制造工厂（合成数据）");
        enterprise.setContactName("演示联系人");
        enterprise.setContactPhone("");
        enterprise.setAddress("演示园区（虚构地址）");
        enterprise.setIndustry("制造业");
        enterprise.setStatus("verified");

        Enterprise.CapacityInfo capacity = new Enterprise.CapacityInfo();
        capacity.setTotalLoad(1250.0);
        capacity.setAdjustableCapacity(700.0);
        enterprise.setCapacityInfo(capacity);

        enterprise = enterpriseRepository.save(enterprise);

        return enterprise.getId();
    }

    /**
     * 创建5台示例设备（地址使用文档保留网段）
     */
    private List<Device> seedDevices(String enterpriseId) {
        List<Device> existing = deviceRepository.findByEnterpriseId(enterpriseId);
        if (existing.size() >= 5) {
            return existing.subList(0, 5);
        }

        List<Device> devices = new ArrayList<>();

        // 1. 空压机 (Modbus TCP)
        devices.add(createDevice(enterpriseId, "1号空压机-A", "空压机", "AS-550PM", "CompAir",
                "modbus", "192.0.2.101", 502, "1号厂房",
                Map.of("slaveId", 1, "intervalSeconds", 60,
                        "energyAddress", 0, "powerAddress", 2,
                        "voltageAddress", 8, "currentAddress", 12)));

        // 2. 注塑机 (MQTT)
        devices.add(createDevice(enterpriseId, "注塑机-海天MA3200", "注塑机", "MA3200", "海天",
                "mqtt", "192.0.2.102", 1883, "2号厂房",
                Map.of("intervalSeconds", 60, "controlTopic", "energy/device/102/control")));

        // 3. 制冷机组 (Modbus TCP)
        devices.add(createDevice(enterpriseId, "冷水机组-Carrier", "制冷机组", "30XW-V", "Carrier",
                "modbus", "192.0.2.103", 502, "制冷站",
                Map.of("slaveId", 2, "intervalSeconds", 60,
                        "energyAddress", 0, "powerAddress", 2,
                        "voltageAddress", 8, "currentAddress", 12)));

        // 4. 水泵组 (HTTP)
        devices.add(createDevice(enterpriseId, "循环水泵组", "水泵", "CDLF85", "格兰富",
                "http", "192.0.2.104", 80, "循环水站",
                Map.of("intervalSeconds", 60, "dataPath", "/api/energy/current", "energyField", "energy")));

        // 5. 照明系统 (MQTT)
        devices.add(createDevice(enterpriseId, "车间LED照明系统", "照明系统", "LED-J100W", "飞利浦",
                "mqtt", "192.0.2.105", 1883, "全厂",
                Map.of("intervalSeconds", 120, "controlTopic", "energy/device/105/control")));

        return deviceRepository.saveAll(devices);
    }

    private Device createDevice(String enterpriseId, String name, String type, String model,
                                  String manufacturer, String protocol, String ip, int port,
                                  String siteId, Map<String, Object> config) {
        return Device.builder()
                .enterpriseId(enterpriseId)
                .name(name)
                .type(type)
                .model(model)
                .manufacturer(manufacturer)
                .status("online")
                .protocol(protocol)
                .ipAddress(ip)
                .port(port)
                .siteId(siteId)
                .config(config)
                .statistics(Device.DeviceStatistics.builder()
                        .totalDataPoints(720L)
                        .totalEnergy(0.0)
                        .lastDataTime(Instant.now())
                        .avgResponseTime(150.0)
                        .build())
                .build();
    }

    /**
     * 生成30天历史能耗数据
     */
    private void seedEnergyRecords(String enterpriseId, List<Device> devices) {
        long existingCount = energyRecordRepository.count();
        if (existingCount > 100) {
            log.info("已有{}条能耗记录，跳过种子数据生成", existingCount);
            return;
        }

        Random random = new Random(42); // 固定种子保证可重复
        LocalDate today = LocalDate.now();
        ZoneId zone = ZoneId.systemDefault();
        List<EnergyRecord> records = new ArrayList<>();

        // 每台设备的基础日能耗 (kWh)
        Map<String, Double> baseEnergy = Map.of(
                "空压机", 850.0,
                "注塑机", 420.0,
                "制冷机组", 580.0,
                "水泵", 320.0,
                "照明系统", 120.0
        );

        // 每台设备的基础电流(A)、电压(V)、运行温度(°C)
        Map<String, double[]> baseElectrical = Map.of(
                "空压机",   new double[]{78, 380, 55},   // I(A), V(V), T(°C)
                "注塑机",   new double[]{42, 380, 48},
                "制冷机组", new double[]{55, 380, 8},    // 冷水机组温度低
                "水泵",     new double[]{28, 380, 35},
                "照明系统", new double[]{12, 220, 30}
        );

        // 电价峰谷波动系数 (0-8谷0.7, 8-12峰1.2, 12-17平1.0, 17-21峰1.1, 21-24平0.9)
        double[] hourFactors = {
            0.7, 0.7, 0.7, 0.7, 0.7, 0.7, 0.7, 0.7,  // 0-7 谷
            1.2, 1.2, 1.2, 1.2,  // 8-11 峰
            1.0, 1.0, 1.0, 1.0, 1.0,  // 12-16 平
            1.1, 1.1, 1.1, 1.1,  // 17-20 峰
            0.9, 0.9, 0.9   // 21-23 平
        };

        for (Device device : devices) {
            Double base = baseEnergy.entrySet().stream()
                    .filter(e -> device.getName().contains(e.getKey()))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElse(200.0);

            double[] baseElec = baseElectrical.entrySet().stream()
                    .filter(e -> device.getName().contains(e.getKey()))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElse(new double[]{20, 220, 25});

            double baseCurrent = baseElec[0];   // A
            double baseVoltage = baseElec[1];   // V
            double baseTemp = baseElec[2];      // °C

            double hourlyBase = base / 24.0;

            for (int day = 29; day >= 0; day--) {
                Instant dayInstant = today.minusDays(day).atStartOfDay(zone).toInstant();

                for (int hour = 0; hour < 24; hour++) {
                    // 生产设备夜间停机（22-6点负荷低）
                    double workFactor = 1.0;
                    if (device.getName().contains("空压机") || device.getName().contains("注塑机")) {
                        workFactor = (hour >= 22 || hour < 6) ? 0.15 : 1.0;
                        // 周日休息
                        DayOfWeek dow = dayInstant.atZone(zone).getDayOfWeek();
                        if (dow == DayOfWeek.SUNDAY) workFactor *= 0.3;
                    } else if (device.getName().contains("制冷机组")) {
                        workFactor = (hour >= 23 || hour < 5) ? 0.4 : 1.0;
                    } else if (device.getName().contains("照明")) {
                        workFactor = (hour >= 19 || hour < 6) ? 0.1 : 1.0;
                    }

                    // 添加随机波动 (±15%)
                    double noise = 0.85 + random.nextDouble() * 0.3;
                    double hourFactor = hourFactors[hour];
                    double value = hourlyBase * hourFactor * workFactor * noise;
                    double power = value / hourFactor; // 功率 = 能耗 / 小时系数

                    // 电流 = 功率 / (电压 × 功率因数), 功率因数 0.75-0.95
                    double pf = 0.75 + random.nextDouble() * 0.2;
                    double current = (power * 1000) / (baseVoltage * pf * Math.sqrt(baseVoltage > 300 ? 3 : 1));
                    // 电压波动 ±5%
                    double voltage = baseVoltage * (0.95 + random.nextDouble() * 0.1);
                    // 温度随工作时间上升
                    double temp = baseTemp + workFactor * (5 + random.nextDouble() * 15);

                    records.add(EnergyRecord.builder()
                            .enterpriseId(enterpriseId)
                            .resourceId(device.getId())
                            .siteId(device.getSiteId())
                            .timestamp(dayInstant.plusSeconds(hour * 3600L + random.nextInt(900)))
                            .value(Math.round(value * 100.0) / 100.0)
                            .power(Math.round(power * 100.0) / 100.0)
                            .current(Math.round(current * 100.0) / 100.0)
                            .voltage(Math.round(voltage * 10.0) / 10.0)
                            .temperature(Math.round(temp * 10.0) / 10.0)
                            .powerFactor(Math.round(pf * 100.0) / 100.0)
                            .carbonEmission(Math.round(value * 0.785 * 100.0) / 100.0)
                            .unit("kWh")
                            .source("device")
                            .createdAt(Instant.now())
                            .build());
                }
            }
        }

        // 分批保存, 避免内存过大
        int batchSize = 500;
        for (int i = 0; i < records.size(); i += batchSize) {
            int end = Math.min(i + batchSize, records.size());
            energyRecordRepository.saveAll(records.subList(i, end));
        }

        log.info("生成30天历史能耗数据: {}条记录", records.size());
    }

    /**
     * 创建5条告警规则
     */
    private void seedAlarmRules(String enterpriseId, List<Device> devices) {
        if (alarmRuleRepository.count() > 0) return;

        List<AlarmRule> rules = new ArrayList<>();

        // 空压机过功率告警
        Device airComp = devices.stream().filter(d -> d.getName().contains("空压机")).findFirst().orElse(null);
        if (airComp != null) {
            rules.add(AlarmRule.builder()
                    .enterpriseId(enterpriseId).name("空压机过功率保护")
                    .deviceId(airComp.getId()).deviceName(airComp.getName())
                    .metric("power").condition("gt").threshold(550.0)
                    .durationSeconds(60).severity("critical").enabled(true)
                    .notifyMethod("app").cooldownSeconds(300)
                    .description("空压机功率超过550kW持续1分钟，请检查设备状态")
                    .build());
        }

        // 制冷机组高能耗
        Device chiller = devices.stream().filter(d -> d.getName().contains("冷水机")).findFirst().orElse(null);
        if (chiller != null) {
            rules.add(AlarmRule.builder()
                    .enterpriseId(enterpriseId).name("制冷机组高能耗告警")
                    .deviceId(chiller.getId()).deviceName(chiller.getName())
                    .metric("energy").condition("gt").threshold(40.0)
                    .durationSeconds(300).severity("warning").enabled(true)
                    .notifyMethod("app").cooldownSeconds(600)
                    .description("制冷机组小时能耗超过40kWh，可能制冷效率下降")
                    .build());
        }

        // 注塑机功率异常
        Device injection = devices.stream().filter(d -> d.getName().contains("注塑")).findFirst().orElse(null);
        if (injection != null) {
            rules.add(AlarmRule.builder()
                    .enterpriseId(enterpriseId).name("注塑机功率过流")
                    .deviceId(injection.getId()).deviceName(injection.getName())
                    .metric("power").condition("gt").threshold(300.0)
                    .durationSeconds(30).severity("critical").enabled(true)
                    .notifyMethod("app").cooldownSeconds(180)
                    .description("注塑机功率超过300kW，可能堵转或过载")
                    .build());
        }

        // 水泵低负载告警（可能漏水或空转）
        Device pump = devices.stream().filter(d -> d.getName().contains("水泵")).findFirst().orElse(null);
        if (pump != null) {
            rules.add(AlarmRule.builder()
                    .enterpriseId(enterpriseId).name("水泵低负载异常")
                    .deviceId(pump.getId()).deviceName(pump.getName())
                    .metric("energy").condition("lt").threshold(5.0)
                    .durationSeconds(600).severity("warning").enabled(true)
                    .notifyMethod("app").cooldownSeconds(900)
                    .description("水泵连续10分钟能耗低于5kWh，可能空转或漏水")
                    .build());
        }

        // 全局日能耗超限
        rules.add(AlarmRule.builder()
                .enterpriseId(enterpriseId).name("全厂日能耗超限")
                .metric("energy").condition("gt").threshold(2500.0)
                .durationSeconds(0).severity("info").enabled(true)
                .notifyMethod("app").cooldownSeconds(86400) // 24h冷却
                .description("全厂日总能耗超过2500kWh，已达设定上限")
                .build());

        // === 电路安全预警规则 ===

        // 空压机过电流保护
        if (airComp != null) {
            rules.add(AlarmRule.builder()
                    .enterpriseId(enterpriseId).name("空压机过电流保护")
                    .deviceId(airComp.getId()).deviceName(airComp.getName())
                    .metric("current").condition("gt").threshold(95.0)
                    .durationSeconds(30).severity("critical").enabled(true)
                    .notifyMethod("app,sms").cooldownSeconds(180)
                    .description("空压机电流超过95A，可能堵转、绕组短路或缺相运行，请立即停机检查")
                    .build());
        }

        // 注塑机温度过高
        if (injection != null) {
            rules.add(AlarmRule.builder()
                    .enterpriseId(enterpriseId).name("注塑机电机过热")
                    .deviceId(injection.getId()).deviceName(injection.getName())
                    .metric("temperature").condition("gt").threshold(70.0)
                    .durationSeconds(120).severity("warning").enabled(true)
                    .notifyMethod("app").cooldownSeconds(600)
                    .description("注塑机电机温度超过70°C持续2分钟，检查冷却系统、润滑和负载情况")
                    .build());
        }

        // 制冷机组低电流异常（可能制冷剂泄漏）
        if (chiller != null) {
            rules.add(AlarmRule.builder()
                    .enterpriseId(enterpriseId).name("制冷机组低电流异常")
                    .deviceId(chiller.getId()).deviceName(chiller.getName())
                    .metric("current").condition("lt").threshold(20.0)
                    .durationSeconds(300).severity("warning").enabled(true)
                    .notifyMethod("app").cooldownSeconds(900)
                    .description("制冷机组电流低于20A持续5分钟，可能制冷剂泄漏或压缩机故障")
                    .build());
        }

        // 母线电压异常（全厂级别，不绑定单台设备）
        rules.add(AlarmRule.builder()
                .enterpriseId(enterpriseId).name("380V母线电压异常")
                .metric("voltage").condition("gt").threshold(420.0)
                .durationSeconds(10).severity("critical").enabled(true)
                .notifyMethod("app,sms").cooldownSeconds(300)
                .description("380V母线电压超过420V，可能变压器分接头异常或无功补偿过补偿，存在设备绝缘击穿风险")
                .build());

        rules.add(AlarmRule.builder()
                .enterpriseId(enterpriseId).name("380V母线欠压告警")
                .metric("voltage").condition("lt").threshold(340.0)
                .durationSeconds(10).severity("critical").enabled(true)
                .notifyMethod("app,sms").cooldownSeconds(300)
                .description("380V母线电压低于340V，可能电网波动或变压器过载，电机可能堵转烧毁")
                .build());

        // 功率因数过低（无功罚款）
        rules.add(AlarmRule.builder()
                .enterpriseId(enterpriseId).name("功率因数过低告警")
                .metric("power_factor").condition("lt").threshold(0.85)
                .durationSeconds(600).severity("info").enabled(true)
                .notifyMethod("app").cooldownSeconds(3600)
                .description("全厂功率因数低于0.85持续10分钟，将产生无功罚款，建议检查电容补偿柜")
                .build());

        alarmRuleRepository.saveAll(rules);
        log.info("创建{}条告警规则（含电路安全预警）", rules.size());
    }

    /**
     * 创建3个VPP资源
     */
    private void seedVppResources(String enterpriseId) {
        if (vppResourceRepository.count() > 0) return;

        List<VppResource> resources = new ArrayList<>();

        // 储能设备
        resources.add(VppResource.builder()
                .enterpriseId(enterpriseId).name("1号厂房储能柜-500kW/1000kWh")
                .type("storage").location("1号厂房")
                .ratedPower(500.0)
                .adjustableRange(VppResource.AdjustableRange.builder().min(100.0).max(500.0).build())
                .responseTime(5).continuousTime(120)
                .status("online")
                .deviceInfo(VppResource.DeviceInfo.builder()
                        .manufacturer("宁德时代").model("CTP-500").protocol("modbus").build())
                .statistics(VppResource.ResourceStatistics.builder()
                        .totalResponseCount(12).completedResponseCount(10)
                        .avgResponseRate(0.95).totalAdjustment(4500.0).build())
                .build());

        // 光伏发电
        resources.add(VppResource.builder()
                .enterpriseId(enterpriseId).name("屋顶光伏阵列-200kWp")
                .type("generator").location("屋顶")
                .ratedPower(200.0)
                .adjustableRange(VppResource.AdjustableRange.builder().min(0.0).max(200.0).build())
                .responseTime(30).continuousTime(480)
                .status("online")
                .deviceInfo(VppResource.DeviceInfo.builder()
                        .manufacturer("隆基").model("Hi-MO7").protocol("http").build())
                .statistics(VppResource.ResourceStatistics.builder()
                        .totalResponseCount(8).completedResponseCount(8)
                        .avgResponseRate(1.0).totalAdjustment(1400.0).build())
                .build());

        // 可调负荷
        resources.add(VppResource.builder()
                .enterpriseId(enterpriseId).name("空压机可调负荷-300kW")
                .type("adjustable_load").location("1号厂房")
                .ratedPower(300.0)
                .adjustableRange(VppResource.AdjustableRange.builder().min(50.0).max(300.0).build())
                .responseTime(60).continuousTime(240)
                .status("online")
                .deviceInfo(VppResource.DeviceInfo.builder()
                        .manufacturer("CompAir").model("AS-550PM").protocol("modbus").build())
                .statistics(VppResource.ResourceStatistics.builder()
                        .totalResponseCount(15).completedResponseCount(13)
                        .avgResponseRate(0.87).totalAdjustment(3200.0).build())
                .build());

        vppResourceRepository.saveAll(resources);
        log.info("创建{}个VPP资源", resources.size());
    }

    /**
     * 创建2个VPP活动
     */
    private void seedVppActivities() {
        if (vppActivityRepository.count() > 0) return;

        // 削峰响应活动
        VppActivity peakShaving = new VppActivity();
        peakShaving.setTitle("2026年8月削峰响应-苏州工业园区");
        peakShaving.setType("peak_shaving");
        peakShaving.setDescription("响应苏州电网夏季负荷高峰削减需求，参与工业削峰响应。\n\n活动说明：\n- 响应时段: 8月15日 14:00-16:00\n- 容量需求: 合计800kW\n- 补偿标准: 容量补贴50元/kW + 电量补贴2元/kWh");
        peakShaving.setStatus("registration");

        VppActivity.ActivityTime time = new VppActivity.ActivityTime();
        time.setPublishAt(Instant.now());
        time.setRegistrationDeadline(Instant.now().plusSeconds(7 * 86400));
        time.setStartAt(Instant.now().plusSeconds(8 * 86400));
        time.setEndAt(Instant.now().plusSeconds(8 * 86400 + 7200));
        peakShaving.setTime(time);

        VppActivity.ActivityRequirements reqs = new VppActivity.ActivityRequirements();
        reqs.setTotalCapacity(800.0);
        reqs.setMinDuration(60);
        reqs.setMaxResponseTime(10);
        peakShaving.setRequirements(reqs);

        VppActivity.ActivityCompensation comp = new VppActivity.ActivityCompensation();
        comp.setCapacityPrice(50.0);
        comp.setEnergyPrice(2.0);
        comp.setMinimumCapacity(100.0);
        peakShaving.setCompensation(comp);

        peakShaving.setEnrolledResources(new ArrayList<>());
        peakShaving.setEnrolledCount(0);
        peakShaving.setCommittedCapacity(0.0);

        vppActivityRepository.save(peakShaving);

        // 调频辅助服务
        VppActivity ancillary = new VppActivity();
        ancillary.setTitle("2026年8月AGC调频辅助服务-紧急需求");
        ancillary.setType("ancillary");
        ancillary.setDescription("电网频率波动，需要快速调频资源响应。\n\n要求：\n- 响应时间 < 3秒\n- 持续时长 > 30分钟\n- 补偿: 容量补贴80元/kW");
        ancillary.setStatus("registration");

        VppActivity.ActivityTime time2 = new VppActivity.ActivityTime();
        time2.setPublishAt(Instant.now());
        time2.setRegistrationDeadline(Instant.now().plusSeconds(3 * 86400));
        time2.setStartAt(Instant.now().plusSeconds(5 * 86400));
        time2.setEndAt(Instant.now().plusSeconds(5 * 86400 + 3600));
        ancillary.setTime(time2);

        VppActivity.ActivityRequirements reqs2 = new VppActivity.ActivityRequirements();
        reqs2.setTotalCapacity(500.0);
        reqs2.setMinDuration(30);
        reqs2.setMaxResponseTime(3);
        ancillary.setRequirements(reqs2);

        VppActivity.ActivityCompensation comp2 = new VppActivity.ActivityCompensation();
        comp2.setCapacityPrice(80.0);
        comp2.setEnergyPrice(3.0);
        comp2.setMinimumCapacity(50.0);
        ancillary.setCompensation(comp2);

        ancillary.setEnrolledResources(new ArrayList<>());
        ancillary.setEnrolledCount(0);
        ancillary.setCommittedCapacity(0.0);

        vppActivityRepository.save(ancillary);
        log.info("创建2个VPP活动");
    }
}
