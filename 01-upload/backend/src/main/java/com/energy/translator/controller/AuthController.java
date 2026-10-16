package com.energy.translator.controller;

import com.energy.translator.dto.ApiResponse;
import com.energy.translator.dto.LoginRequest;
import com.energy.translator.dto.LoginResponse;
import com.energy.translator.dto.RegisterRequest;
import com.energy.translator.entity.User;
import com.energy.translator.security.UserPrincipal;
import com.energy.translator.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        try {
            LoginResponse response = authService.login(request.getUsername(), request.getPassword());
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/demo-login")
    public ResponseEntity<ApiResponse<LoginResponse>> demoLogin(HttpServletRequest request) {
        String ip = clientIp(request);
        if (!demoLoginAllowed(ip)) {
            return ResponseEntity.status(429).body(ApiResponse.error("体验登录过于频繁，请稍后再试"));
        }
        try {
            LoginResponse response = authService.loginDemo();
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    // 体验登录每 IP 频控：60 秒内最多 30 次，防免密端点被刷 token
    private static final int DEMO_LOGIN_MAX = 30;
    private static final long DEMO_LOGIN_WINDOW_MS = 60_000;
    private final Map<String, Deque<Long>> demoLoginWindow = new ConcurrentHashMap<>();

    private boolean demoLoginAllowed(String ip) {
        long now = System.currentTimeMillis();
        Deque<Long> q = demoLoginWindow.computeIfAbsent(ip, k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && now - q.peekFirst() > DEMO_LOGIN_WINDOW_MS) {
                q.pollFirst();
            }
            if (q.size() >= DEMO_LOGIN_MAX) {
                return false;
            }
            q.offerLast(now);
            return true;
        }
    }

    private String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<Map<String, String>>> register(@Valid @RequestBody RegisterRequest request) {
        try {
            String message = authService.register(request);
            return ResponseEntity.ok(ApiResponse.success(Map.of("message", message)));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<LoginResponse.RefreshResponse>> refresh(@RequestBody Map<String, String> request) {
        try {
            String refreshToken = request.get("refreshToken");
            LoginResponse.RefreshResponse response = authService.refreshToken(refreshToken);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/userinfo")
    public ResponseEntity<ApiResponse<User>> getUserInfo(@AuthenticationPrincipal UserPrincipal principal) {
        try {
            User user = authService.getUserInfo(principal.getUserId());
            user.setPassword(null); // 脱敏
            return ResponseEntity.ok(ApiResponse.success(user));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }
}
