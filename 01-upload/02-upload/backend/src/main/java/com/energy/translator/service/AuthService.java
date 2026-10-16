package com.energy.translator.service;

import com.energy.translator.dto.LoginResponse;
import com.energy.translator.dto.RegisterRequest;
import com.energy.translator.entity.Enterprise;
import com.energy.translator.entity.User;
import com.energy.translator.repository.EnterpriseRepository;
import com.energy.translator.repository.UserRepository;
import com.energy.translator.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collections;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final EnterpriseRepository enterpriseRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final Environment environment;

    @Value("${demo.login-enabled:false}")
    private boolean demoLoginEnabled;

    public LoginResponse login(String username, String password) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("用户名或密码错误"));

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new RuntimeException("用户名或密码错误");
        }

        if (!"active".equals(user.getStatus())) {
            if ("pending".equals(user.getStatus())) {
                throw new RuntimeException("账号待审核，请联系平台管理员审核通过");
            }
            throw new RuntimeException("账号已被禁用");
        }

        String token = jwtTokenProvider.generateToken(user.getId(), user.getUsername(), user.getRole());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        user.setLastLoginAt(Instant.now());
        user.setLoginCount(user.getLoginCount() == null ? 1 : user.getLoginCount() + 1);
        userRepository.save(user);

        Enterprise enterprise = null;
        if (user.getEnterpriseId() != null) {
            enterprise = enterpriseRepository.findById(user.getEnterpriseId()).orElse(null);
        }

        return LoginResponse.builder()
                .token(token)
                .refreshToken(refreshToken)
                .expiresIn(jwtTokenProvider.getExpiration() / 1000)
                .userInfo(LoginResponse.UserInfo.builder()
                        .userId(user.getId())
                        .username(user.getUsername())
                        .role(user.getRole())
                        .permissions(user.getPermissions() != null ? user.getPermissions() : Collections.singletonList("*"))
                        .enterpriseId(user.getEnterpriseId())
                        .enterpriseName(enterprise != null ? enterprise.getName() : null)
                        .build())
                .build();
    }

    /**
     * 免密体验登录：仅用于小程序"一键体验演示企业"入口。
     * 直接以固定的公开体验账号(demo)签发 token，不校验密码，
     * 避免小程序端写死口令导致后端改密后体验入口失效。
     * 仅显式开启 demo.login-enabled 且非 prod 环境时允许；限隔离的本地演示数据库。
     */
    public LoginResponse loginDemo() {
        if (!demoLoginEnabled || environment.acceptsProfiles(Profiles.of("prod"))) {
            throw new IllegalStateException("当前环境未启用演示登录");
        }
        User user = userRepository.findByUsername("demo")
                .orElseThrow(() -> new RuntimeException("体验账号不存在，请联系管理员"));

        if (!"active".equals(user.getStatus())) {
            if ("pending".equals(user.getStatus())) {
                throw new RuntimeException("体验账号待审核，请联系平台管理员审核通过");
            }
            throw new RuntimeException("体验账号已被禁用");
        }

        String token = jwtTokenProvider.generateToken(user.getId(), user.getUsername(), user.getRole());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        user.setLastLoginAt(Instant.now());
        user.setLoginCount(user.getLoginCount() == null ? 1 : user.getLoginCount() + 1);
        userRepository.save(user);

        Enterprise enterprise = null;
        if (user.getEnterpriseId() != null) {
            enterprise = enterpriseRepository.findById(user.getEnterpriseId()).orElse(null);
        }

        return LoginResponse.builder()
                .token(token)
                .refreshToken(refreshToken)
                .expiresIn(jwtTokenProvider.getExpiration() / 1000)
                .userInfo(LoginResponse.UserInfo.builder()
                        .userId(user.getId())
                        .username(user.getUsername())
                        .role(user.getRole())
                        .permissions(user.getPermissions() != null ? user.getPermissions() : Collections.singletonList("*"))
                        .enterpriseId(user.getEnterpriseId())
                        .enterpriseName(enterprise != null ? enterprise.getName() : null)
                        .build())
                .build();
    }

    @Transactional
    public String register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("用户名已存在");
        }

        if (userRepository.existsByPhone(request.getPhone())) {
            throw new RuntimeException("手机号已被注册");
        }

        String enterpriseId = null;
        if (request.getEnterpriseName() != null && !request.getEnterpriseName().isEmpty()) {
            Enterprise enterprise = Enterprise.builder()
                    .name(request.getEnterpriseName())
                    .type(request.getEnterpriseType() != null ? request.getEnterpriseType() : "general")
                    .creditCode(request.getCreditCode())
                    .status("pending")
                    .contactPhone(request.getPhone())
                    .contactEmail(request.getEmail())
                    .capacityInfo(Enterprise.CapacityInfo.builder()
                            .totalLoad(0.0)
                            .adjustableCapacity(0.0)
                            .contractCapacity(0.0)
                            .build())
                    .build();
            enterprise = enterpriseRepository.save(enterprise);
            enterpriseId = enterprise.getId();
        }

        // 安全修正：注册接口绝不产出平台超级管理员(admin)。
        // 仅当携带企业名(创建企业)时赋予企业管理员(enterprise_admin)，
        // 否则为普通用户(user)。平台 admin 仅允许由已存在的 admin 在后台创建。
        String role = enterpriseId != null ? "enterprise_admin" : "user";
        // 企业注册需平台审核：关联用户先置 pending，审核通过(后台)后才 active；
        // 普通用户(不带企业名)直接 active 立即可用。
        String userStatus = enterpriseId != null ? "pending" : "active";

        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .phone(request.getPhone())
                .email(request.getEmail())
                .role(role)
                .permissions(Collections.singletonList("*"))
                .enterpriseId(enterpriseId)
                .status(userStatus)
                .loginCount(0)
                .build();

        userRepository.save(user);

        return enterpriseId != null ? "注册成功，请等待企业审核" : "注册成功";
    }

    public LoginResponse.RefreshResponse refreshToken(String refreshToken) {
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new RuntimeException("无效的刷新Token");
        }

        String userId = jwtTokenProvider.getUserId(refreshToken);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        String newToken = jwtTokenProvider.generateToken(user.getId(), user.getUsername(), user.getRole());
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        return LoginResponse.RefreshResponse.builder()
                .token(newToken)
                .refreshToken(newRefreshToken)
                .expiresIn(jwtTokenProvider.getExpiration() / 1000)
                .build();
    }

    public User getUserInfo(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
    }
}
