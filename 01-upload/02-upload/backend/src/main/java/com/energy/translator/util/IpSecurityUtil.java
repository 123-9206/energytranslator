package com.energy.translator.util;

import com.energy.translator.exception.ForbiddenException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * IP 安全校验工具类 — SSRF 防护
 *
 * 禁止向内网/保留地址发起外发 HTTP 请求。
 * 用于 ControlService 和 DeviceCollectService 等所有对外 HTTP 调用场景。
 */
public final class IpSecurityUtil {

    private static final Logger log = LoggerFactory.getLogger(IpSecurityUtil.class);

    private IpSecurityUtil() {
        // 工具类禁止实例化
    }

    /**
     * 校验目标 IP 是否允许作为外发请求的目标。
     * 阻断以下地址类型：
     * <ul>
     *   <li>Loopback — 127.x.x.x, ::1</li>
     *   <li>Link-local — 169.254.x.x</li>
     *   <li>Site-local — 10.x, 172.16-31.x, 192.168.x</li>
     *   <li>Any-local — 0.0.0.0</li>
     *   <li>Multicast — 224.x.x.x+</li>
     * </ul>
     *
     * @param ip 待校验的 IP 地址字符串
     * @return true=允许, false=阻断（内网/保留地址/非法）
     */
    public static boolean isAllowedTargetIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }

        // 去掉 IPv6 scope 后缀（如 %eth0）
        String cleanIp = ip.contains("%") ? ip.substring(0, ip.indexOf('%')) : ip;

        try {
            InetAddress addr = InetAddress.getByName(cleanIp);
            boolean blocked = addr.isLoopbackAddress()
                    || addr.isLinkLocalAddress()
                    || addr.isSiteLocalAddress()
                    || addr.isAnyLocalAddress()
                    || addr.isMulticastAddress();

            if (blocked) {
                log.warn("IP安全校验阻断: ip={}, type={}", cleanIp,
                        addr.isLoopbackAddress() ? "loopback" :
                                addr.isLinkLocalAddress() ? "link-local" :
                                        addr.isSiteLocalAddress() ? "site-local" :
                                                addr.isAnyLocalAddress() ? "any-local" : "multicast");
            }
            return !blocked;
        } catch (UnknownHostException e) {
            log.error("IP地址解析失败，阻断请求: ip={}", ip, e);
            return false;
        }
    }

    /**
     * 校验并抛出异常（适合需要直接中断的场景）
     *
     * @param ip      待校验 IP
     * @param context 上下文描述（用于错误日志），如 "HTTP控制" / "HTTP采集"
     * @throws ForbiddenException 当 IP 为内网/保留地址时抛出
     */
    public static void requireAllowedTargetIp(String ip, String context) {
        if (!isAllowedTargetIp(ip)) {
            throw new ForbiddenException(
                    String.format("[%s] 禁止向内网/保留地址发送请求: %s", context, ip));
        }
    }
}
