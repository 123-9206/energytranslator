// pages/guide/guide.js
Page({
  data: {
    activeTab: 'modbus',
    tabs: [
      { key: 'modbus', label: 'Modbus' },
      { key: 'mqtt', label: 'MQTT' },
      { key: 'http', label: 'HTTP' },
      { key: 'powersource', label: '多电源' },
      { key: 'shop', label: '采购指南' }
    ],

    // Modbus 配置参数说明
    modbusFields: [
      { name: 'ipAddress', label: 'IP地址', required: true, desc: '设备的局域网IP，如 192.168.1.100' },
      { name: 'port', label: '端口', required: true, desc: 'Modbus TCP默认502' },
      { name: 'slaveId', label: '从站ID', required: true, desc: 'Modbus从站地址，通常1-247，默认1' },
      { name: 'energyAddress', label: '能耗寄存器地址', required: true, desc: '存储累计能耗的保持寄存器起始地址' },
      { name: 'powerAddress', label: '功率寄存器地址', required: true, desc: '存储实时功率的保持寄存器起始地址' },
      { name: 'dataType', label: '数据类型', required: false, desc: 'INT16/UINT16/INT32/UINT32/FLOAT，默认FLOAT' },
      { name: 'writeAddress', label: '控制写入地址', required: false, desc: '下发控制指令的线圈或寄存器地址' },
      { name: 'writeType', label: '写入类型', required: false, desc: '线圈(coil)或保持寄存器(register)，默认coil' },
      { name: 'intervalSeconds', label: '采集间隔(秒)', required: false, desc: '默认60秒，建议不低于10秒' }
    ],

    // MQTT 配置参数说明
    mqttFields: [
      { name: 'ipAddress', label: 'Broker地址', required: true, desc: 'MQTT Broker的IP，如 localhost 或 192.168.1.100' },
      { name: 'port', label: 'Broker端口', required: true, desc: 'MQTT默认1883，TLS端口8883' },
      { name: 'dataTopic', label: '数据上报Topic', required: true, desc: '设备上报数据的topic，如 energy/device/001/data' },
      { name: 'controlTopic', label: '控制接收Topic', required: false, desc: '设备接收控制指令的topic，如 energy/device/001/control' },
      { name: 'mqttQos', label: 'QoS等级', required: false, desc: '0/1/2，默认1（至少一次送达）' },
      { name: 'mqttRetained', label: '保留消息', required: false, desc: 'true/false，默认false' },
      { name: 'intervalSeconds', label: '心跳间隔(秒)', required: false, desc: '默认60秒，建议不超过300秒' }
    ],

    // HTTP 配置参数说明
    httpFields: [
      { name: 'ipAddress', label: '设备URL', required: true, desc: '设备的HTTP API地址，如 http://192.168.1.100' },
      { name: 'port', label: '端口', required: true, desc: 'HTTP默认80，HTTPS默认443' },
      { name: 'dataPath', label: '数据接口路径', required: true, desc: '如 /api/energy/current' },
      { name: 'httpMethod', label: '请求方法', required: false, desc: 'GET或POST，默认GET' },
      { name: 'authHeader', label: '认证头', required: false, desc: '如需要认证，填写 Authorization 的值' },
      { name: 'energyField', label: '能耗字段路径', required: true, desc: 'JSON响应中能耗值的路径，如 data.energy' },
      { name: 'powerField', label: '功率字段路径', required: true, desc: 'JSON响应中功率值的路径，如 data.power' },
      { name: 'controlPath', label: '控制接口路径', required: false, desc: '如 /api/control' },
      { name: 'intervalSeconds', label: '采集间隔(秒)', required: false, desc: '默认60秒' }
    ],

    // 采购指南
    shopCategories: [
      {
        title: 'Modbus TCP 电力仪表',
        icon: '⚡',
        items: [
          {
            brand: '威胜 (Wasion)',
            models: 'DTSD341 / DSSD331',
            price: '¥800-3,000',
            desc: '三相多功能电力仪表，支持Modbus TCP，精度0.5S级',
            channel: '威胜官网 / 1688 / 京东工业品'
          },
          {
            brand: '安科瑞 (Acrel)',
            models: 'ADL3000 / DTSD1352',
            price: '¥400-1,500',
            desc: '导轨式三相电表，Modbus RTU/TCP双模，性价比高',
            channel: '安科瑞天猫旗舰店 / 1688'
          },
          {
            brand: '华立科技 (Holley)',
            models: 'DTZY545 / DDZY285',
            price: '¥600-2,000',
            desc: '国网中标品牌，支持Modbus TCP通信，稳定可靠',
            channel: '华立官网 / 1688'
          },
          {
            brand: '林洋能源 (Linyang)',
            models: 'DTZY71 / DDZY102',
            price: '¥500-1,800',
            desc: '智能电表龙头企业，支持Modbus TCP，支持双向计量',
            channel: '林洋官网 / 京东工业品'
          }
        ]
      },
      {
        title: 'MQTT IoT 网关/DTU',
        icon: '📡',
        items: [
          {
            brand: '有人物联网 (USR)',
            models: 'USR-G780 / USR-G771',
            price: '¥200-500',
            desc: '4G DTU，串口转MQTT，支持阿里云/EMQX/OneNET',
            channel: '有人天猫旗舰店 / 京东'
          },
          {
            brand: '四信 (Four-Faith)',
            models: 'F7916 / F2X16',
            price: '¥300-800',
            desc: '工业级4G/5G DTU，支持MQTT/Modbus互转',
            channel: '四信官网 / 1688'
          },
          {
            brand: '纵横智控 (ZHC)',
            models: 'ZHC4921 / ZHC4941',
            price: '¥150-400',
            desc: '低成本4G DTU，透明传输+MQTT，适合批量部署',
            channel: '1688 / 淘宝'
          },
          {
            brand: 'ESP32开发板',
            models: 'ESP32-DevKitC / NodeMCU-32S',
            price: '¥20-50',
            desc: '开源方案，WiFi+蓝牙，可自行开发MQTT固件',
            channel: '淘宝 / 立创商城'
          }
        ]
      },
      {
        title: 'Modbus/MQTT 协议转换器',
        icon: '🔄',
        items: [
          {
            brand: '有人物联网 (USR)',
            models: 'USR-M511',
            price: '¥300-600',
            desc: 'Modbus RTU/TCP 转 MQTT，双向透传，Web配置',
            channel: '有人天猫旗舰店'
          },
          {
            brand: '四信 (Four-Faith)',
            models: 'F8L10T',
            price: '¥400-900',
            desc: 'LoRa转MQTT网关，适合远距离无线采集场景',
            channel: '四信官网 / 1688'
          },
          {
            brand: '康耐德 (KonNaD)',
            models: 'C2000-A2',
            price: '¥200-500',
            desc: '串口服务器，Modbus RTU转TCP，工业级',
            channel: '天猫 / 京东'
          }
        ]
      },
      {
        title: 'HTTP 智能传感器/控制器',
        icon: '🌐',
        items: [
          {
            brand: '涂鸦智能 (Tuya)',
            models: '智能断路器/智能插座系列',
            price: '¥50-300',
            desc: 'WiFi直连，支持HTTP API控制，适合办公/商业场景',
            channel: '涂鸦官网 / 天猫 / 1688'
          },
          {
            brand: '小米 (Xiaomi)',
            models: '米家智能插座/空调伴侣',
            price: '¥39-150',
            desc: 'WiFi联网，可通过Home Assistant桥接获取HTTP API',
            channel: '小米商城 / 京东 / 天猫'
          },
          {
            brand: '树莓派 (Raspberry Pi)',
            models: '4B / 5 + 传感器模块',
            price: '¥200-500',
            desc: '开源方案，可搭建HTTP接口上报环境/能耗数据',
            channel: '淘宝 / 京东 / 立创商城'
          }
        ]
      },
      {
        title: '☀️ 光伏逆变器',
        icon: '☀️',
        items: [
          { brand: '华为 (Huawei)', models: 'SUN2000-xxKTL-M1/M3', price: '¥3,000-15,000', desc: '支持 Modbus TCP + SunSpec，国内市占率第一，通信协议文档公开', channel: '华为数字能源官网 / 京东 / 1688' },
          { brand: '阳光电源 (Sungrow)', models: 'SGxxCX / SGxxRT', price: '¥2,000-12,000', desc: 'Modbus TCP接口，通信协议公开，性价比高', channel: '阳光电源官网 / 1688' },
          { brand: '固德威 (GoodWe)', models: 'GWxxK-ET / GWxxK-HT', price: '¥1,500-8,000', desc: '支持Modbus TCP + WiFi直连，户用工商业均适用', channel: '固德威天猫旗舰店 / 京东' },
          { brand: '锦浪 (Ginlong Solis)', models: 'Solis-xxK-5G', price: '¥1,200-6,000', desc: 'Modbus TCP，性价比极高，中小功率段强项', channel: '1688 / 淘宝' }
        ]
      },
      {
        title: '🔋 储能柜 / 电池系统',
        icon: '🔋',
        items: [
          { brand: '宁德时代 (CATL)', models: 'EnerOne / EnerC', price: '¥100,000-500,000+', desc: '集装箱式储能，Modbus TCP，200kWh-1MWh+，工业级', channel: '宁德时代储能事业部 / 授权经销商' },
          { brand: '比亚迪 (BYD)', models: 'B-Box / CHESS', price: '¥50,000-300,000', desc: '高压储能柜，支持Modbus TCP通信，户用/工商业都有', channel: '比亚迪储能官网 / 1688经销商' },
          { brand: '阳光电源 (Sungrow)', models: 'SC2500UD / STxxxCP', price: '¥80,000-400,000', desc: 'PCS+电池一体化，Modbus TCP，协议文档完备', channel: '阳光电源官网 / 系统集成商' },
          { brand: '华为 (Huawei)', models: 'LUNA2000-S0/S1', price: '¥15,000-80,000', desc: '户用/工商业储能，Modbus TCP + CAN，智能运维', channel: '华为数字能源官网 / 京东' }
        ]
      },
      {
        title: '🔧 安装辅材推荐',
        icon: '🧰',
        items: [
          {
            brand: '电流互感器 (CT)',
            models: '开口式 100A/200A/400A',
            price: '¥30-80/个',
            desc: '开口式免停电安装，搭配电表使用',
            channel: '1688 / 淘宝搜索「开口电流互感器」'
          },
          {
            brand: '工业交换机',
            models: '5口/8口百兆非网管',
            price: '¥80-300',
            desc: 'TP-LINK/海康威视，连接多个Modbus TCP设备',
            channel: '京东自营'
          },
          {
            brand: '导轨电源',
            models: '24V 2A DIN导轨安装',
            price: '¥30-80',
            desc: '明纬(MW)或国产替代，为DTU/网关供电',
            channel: '1688 / 淘宝'
          }
        ]
      }
    ],

    // ========== 光伏逆变器 ==========
    solarFields: [
      { name: 'ipAddress', label: '逆变器IP', required: true, desc: '光伏逆变器的局域网IP，如 192.168.1.50' },
      { name: 'port', label: '端口', required: true, desc: 'Modbus TCP默认502，部分品牌用其他端口' },
      { name: 'slaveId', label: '从站ID', required: true, desc: '逆变器Modbus从站地址，通常为1或2' },
      { name: 'powerAddress', label: '实时功率地址', required: true, desc: '交流输出功率(Ah)的保持寄存器地址' },
      { name: 'dailyEnergy', label: '日发电量地址', required: true, desc: '当日累计发电量的寄存器地址 (kWh)' },
      { name: 'totalEnergy', label: '总发电量地址', required: true, desc: '总累计发电量的寄存器地址，通常为双寄存器' },
      { name: 'dcVoltage', label: '直流电压地址', required: false, desc: 'PV输入直流电压寄存器地址' },
      { name: 'dcCurrent', label: '直流电流地址', required: false, desc: 'PV输入直流电流寄存器地址' },
      { name: 'acVoltage', label: '交流电压地址', required: false, desc: '逆变器AC输出电压寄存器地址' },
      { name: 'temperature', label: '逆变器温度', required: false, desc: '逆变器机内温度寄存器地址' },
      { name: 'dataType', label: '数据类型', required: false, desc: 'UINT16/UINT32/FLOAT，默认UINT32' },
      { name: 'intervalSeconds', label: '采集间隔(秒)', required: false, desc: '默认60秒' }
    ],

    solarBrands: [
      { brand: '华为 (Huawei)', models: 'SUN2000-xxKTL-M1/M3', note: '支持Modbus TCP + SunSpec，国内市占率第一' },
      { brand: '阳光电源 (Sungrow)', models: 'SGxxCX / SGxxRT', note: 'Modbus TCP接口，通信协议公开' },
      { brand: '固德威 (GoodWe)', models: 'GWxxK-ET / GWxxK-HT', note: '支持Modbus TCP + WiFi直连' },
      { brand: '锦浪 (Ginlong Solis)', models: 'Solis-xxK-5G', note: 'Modbus TCP，性价比高' },
      { brand: '古瑞瓦特 (Growatt)', models: 'MAX/MOD/MAC系列', note: 'Modbus TCP，需在设置中开启通信接口' }
    ],

    // ========== 储能柜 ==========
    storageFields: [
      { name: 'ipAddress', label: '储能柜IP', required: true, desc: '储能柜BMS/EMS的局域网IP' },
      { name: 'port', label: '端口', required: true, desc: 'Modbus TCP默认502' },
      { name: 'slaveId', label: '从站ID', required: true, desc: 'BMS Modbus从站地址，通常为1' },
      { name: 'socAddress', label: 'SOC地址', required: true, desc: '电池荷电状态寄存器的寄存器地址 (%)' },
      { name: 'sohAddress', label: 'SOH地址', required: false, desc: '电池健康状态寄存器地址 (%)' },
      { name: 'chargePower', label: '充电功率地址', required: true, desc: '当前充电/放电功率寄存器地址 (kW, 正值充电负值放电)' },
      { name: 'totalCapacity', label: '额定容量地址', required: true, desc: '系统额定容量寄存器的寄存器地址 (kWh)' },
      { name: 'cellTempMax', label: '电芯最高温度', required: false, desc: '电芯温度（最高值）寄存器地址' },
      { name: 'cellTempMin', label: '电芯最低温度', required: false, desc: '电芯温度（最低值）寄存器地址' },
      { name: 'cycleCount', label: '循环次数', required: false, desc: '充放电循环次数寄存器地址' },
      { name: 'dataType', label: '数据类型', required: false, desc: 'UINT16/INT32/FLOAT，默认UINT16' },
      { name: 'intervalSeconds', label: '采集间隔(秒)', required: false, desc: '默认30秒' }
    ],

    storageBrands: [
      { brand: '宁德时代 (CATL)', models: 'EnerOne / EnerC', note: '集装箱式储能，Modbus TCP，工业级' },
      { brand: '比亚迪 (BYD)', models: 'B-Box / CHESS系列', note: '高压储能柜，支持Modbus TCP通信' },
      { brand: '阳光电源 (Sungrow)', models: 'SC2500UD / STxxxxCP', note: 'PCS+电池一体化，Modbus TCP，协议完备' },
      { brand: '华为 (Huawei)', models: 'LUNA2000-S0/S1', note: '户用/工商业储能，Modbus TCP + CAN' },
      { brand: '科华数据 (Kehua)', models: 'BCS系列 / S³系列', note: '工商业储能柜，支持Modbus TCP + MQTT' }
    ],

    // ========== VPP 多电源渠道接入 ==========
    vppSourceTypes: [
      { 
        type: 'solar', name: '光伏发电', icon: '☀️',
        desc: '分布式光伏电站，接入VPP参与调峰和容量市场交易',
        fields: 'panelCapacity(组件容量kWp), panelEfficiency(转换效率%), peakSunHours(年日照小时数), orientation(朝向), tiltAngle(倾角度)',
        protocol: 'Modbus TCP（逆变器标准协议），推荐SunSpec寄存器映射'
      },
      {
        type: 'storage', name: '储能设备', icon: '🔋',
        desc: '电池储能系统，参与VPP削峰填谷、调频辅助服务',
        fields: 'socMin/socMax(SOC安全范围%), ratedCapacity(额定容量kWh), chargeRate/dischargeRate(充放电倍率C)',
        protocol: 'Modbus TCP（BMS/EMS标准协议）'
      },
      {
        type: 'generator', name: '柴油发电机', icon: '🛢️',
        desc: '应急/备用发电机组，参与VPP紧急响应和容量备用',
        fields: 'minPower(最低出力kW), rampRate(爬坡速率kW/min), minRuntime/maxDowntime(运行/停机限制分钟), fuelType(柴油)',
        protocol: 'Modbus TCP（发电机控制器），部分支持MQTT'
      },
      {
        type: 'wind', name: '风力发电', icon: '💨',
        desc: '分布式风力发电机组，参与VPP绿色电力交易',
        fields: 'ratedPower(额定功率kW), rampRate(爬坡速率), panelEfficiency(等效效率), peakSunHours(等效满发小时)',
        protocol: 'Modbus TCP（风机控制器），高端机型支持OPC-UA'
      },
      {
        type: 'grid', name: '电网关口', icon: '🏭',
        desc: '企业电网关口表，作为VPP的电网交互界面，用于计量上网/下网电量',
        fields: 'ratedPower(并网容量kW), gridConnectionPoint(接入点编号), minPower/maxPower(功率范围)',
        protocol: 'Modbus TCP（关口表），DL/T645也可桥接'
      },
      {
        type: 'adjustable_load', name: '可调负荷', icon: '⚙️',
        desc: '可调节的工业负荷（如空调、水泵、生产线），参与需求响应',
        fields: 'adjustableRange(min/max kW), responseTime(响应时间秒), continuousTime(持续时间分钟)',
        protocol: 'Modbus TCP（PLC/控制器）或HTTP（智能设备）'
      }
    ],

    // ========== VPP 注册流程 ==========
    vppSteps: [
      { step: 1, title: '安装物理设备', desc: '光伏、储能、柴油发电机等设备按Modbus TCP规范接入局域网，分配固定IP' },
      { step: 2, title: '创建设备记录', desc: '在能耗翻译官后台「设备管理」中，为每个物理设备创建记录。光伏选择类型 inverter，储能选择 storage_cabinet，协议选择 modbus' },
      { step: 3, title: '验证数据采集', desc: '待设备上线后，在「能耗记录」页面确认数据正常采集，检查功率、发电量/充放电量数据准确' },
      { step: 4, title: '注册VPP资源', desc: '在「控制」→「VPP资源」页面，登记为VPP资源：选择对应类型(solar/storage/generator等)，关联已创建的设备，填写额定参数' },
      { step: 5, title: '参与VPP活动', desc: '资源审核通过后，可报名参加VPP调峰/需求响应等活动。系统根据实时数据评估响应能力并生成结算报告' }
    ],

    // FAQ
    faq: [
      { q: '设备添加后显示离线？', a: '检查设备IP能否从服务器ping通，确认端口未被防火墙拦截。Modbus设备需开放502端口，MQTT需1883端口，HTTP需对应端口。连续3次采集失败会自动标记为离线。' },
      { q: 'Modbus寄存器地址如何确定？', a: '查看设备说明书中的「Modbus通信地址表」，通常以十进制或十六进制给出。注意有些设备说明书给的地址需要+1（如说明书写40001，实际地址为0）。' },
      { q: 'MQTT数据格式有要求吗？', a: '设备需发布JSON格式数据到指定topic，至少包含 value(功率) 和 energy(累计能耗) 两个字段。示例：{"value": 15.6, "energy": 230.5, "timestamp": "2026-05-26T17:00:00Z"}。' },
      { q: 'HTTP设备的JSON响应怎么配置字段路径？', a: '使用点号分隔嵌套路径。如果响应是 {"data":{"power":15.6}}，则powerField填「data.power」。如果响应是 {"power":15.6}，直接填「power」。' },
      { q: '一个系统能接多少台设备？', a: '理论上无硬性限制，但建议Modbus TCP同一网段不超过50台（避免轮询延迟），MQTT无限制，HTTP取决于API响应速度。采集线程池默认10个并发。' },
      { q: '采集间隔最短能设多少？', a: 'Modbus建议不低于5秒（TCP连接开销），HTTP建议不低于10秒（避免对方服务器限流），MQTT为被动接收模式不受此限制。' }
    ]
  },

  onLoad() {
    // 预留
  },

  onShareAppMessage() {
    return {
      title: '能耗翻译官 - 设备配置指南',
      path: '/pages/guide/guide'
    };
  },

  switchTab(e) {
    const tab = e.currentTarget.dataset.tab;
    this.setData({ activeTab: tab });
  },

  /** 复制文本到剪贴板 */
  onCopy(e) {
    const text = e.currentTarget.dataset.text;
    if (!text) return;
    wx.setClipboardData({
      data: text,
      success: () => {
        wx.showToast({ title: '已复制', icon: 'success', duration: 1500 });
      }
    });
  }
});
