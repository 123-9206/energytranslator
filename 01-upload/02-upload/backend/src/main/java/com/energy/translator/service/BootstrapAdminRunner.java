package com.energy.translator.service;

import com.energy.translator.entity.User;
import com.energy.translator.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import java.util.List;

/** Create an initial administrator only when a private bootstrap password is supplied. */
@Component
@Slf4j
@RequiredArgsConstructor
public class BootstrapAdminRunner implements CommandLineRunner {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${bootstrap.admin.username:admin}")
    private String username;

    @Value("${bootstrap.admin.password:}")
    private String password;

    @Override
    public void run(String... args) {
        if (password == null || password.isBlank()) {
            return;
        }
        if (username == null || username.isBlank() || "demo".equalsIgnoreCase(username)) {
            throw new IllegalStateException("Choose a bootstrap username other than demo");
        }
        // Never promote another user or reset an existing account during startup.
        if (userRepository.findByUsername(username).isPresent()) {
            return;
        }
        if (password.length() < 16) {
            throw new IllegalStateException("BOOTSTRAP_ADMIN_PASSWORD must contain at least 16 characters");
        }
        User admin = User.builder()
                .username(username)
                .password(passwordEncoder.encode(password))
                .role("admin")
                .permissions(List.of("*"))
                .status("active")
                .loginCount(0)
                .build();
        userRepository.save(admin);
        log.info("Created bootstrap administrator; credentials are not logged");
    }
}
