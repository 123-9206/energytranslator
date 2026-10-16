package com.energy.translator.service;

import com.energy.translator.entity.User;
import com.energy.translator.repository.*;
import com.energy.translator.security.JwtTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Tests the publication preparation's account and external-action defaults. */
class PublicConfigurationTest {
    @Test
    void demoLoginIsDisabledByDefaultAndRejectsProdEvenWhenEnabled() {
        UserRepository users = mock(UserRepository.class);
        Environment environment = mock(Environment.class);
        AuthService auth = new AuthService(users, mock(EnterpriseRepository.class),
                mock(JwtTokenProvider.class), mock(PasswordEncoder.class), environment);
        assertThrows(IllegalStateException.class, auth::loginDemo);
        verifyNoInteractions(users);

        ReflectionTestUtils.setField(auth, "demoLoginEnabled", true);
        when(environment.acceptsProfiles(any(Profiles.class))).thenReturn(true);
        assertThrows(IllegalStateException.class, auth::loginDemo);
        verifyNoInteractions(users);
    }

    @Test
    void disabledSeedingDoesNotCreateOrResetAccounts() {
        UserRepository users = mock(UserRepository.class);
        EnterpriseRepository enterprises = mock(EnterpriseRepository.class);
        DeviceRepository devices = mock(DeviceRepository.class);
        SeedDataRunner seed = new SeedDataRunner(users, enterprises, devices,
                mock(AlarmRuleRepository.class), mock(EnergyRecordRepository.class),
                mock(VppResourceRepository.class), mock(VppActivityRepository.class));
        seed.run();
        verifyNoInteractions(users, enterprises, devices);
    }

    @Test
    void seedingDoesNotResetExistingDemoAccount() {
        UserRepository users = mock(UserRepository.class);
        DeviceRepository devices = mock(DeviceRepository.class);
        when(users.findByUsername("demo")).thenReturn(Optional.of(new User()));
        when(devices.count()).thenReturn(5L);
        SeedDataRunner seed = new SeedDataRunner(users, mock(EnterpriseRepository.class), devices,
                mock(AlarmRuleRepository.class), mock(EnergyRecordRepository.class),
                mock(VppResourceRepository.class), mock(VppActivityRepository.class));
        ReflectionTestUtils.setField(seed, "seedEnabled", true);
        seed.run();
        verify(users, never()).save(any(User.class));
    }

    @Test
    void bootstrapPreservesExistingUserRatherThanPromotingOrResettingIt() {
        UserRepository users = mock(UserRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(users.findByUsername("local-admin")).thenReturn(Optional.of(new User()));
        BootstrapAdminRunner runner = new BootstrapAdminRunner(users, encoder);
        ReflectionTestUtils.setField(runner, "username", "local-admin");
        ReflectionTestUtils.setField(runner, "password", UUID.randomUUID().toString());
        runner.run();
        verify(users, never()).save(any(User.class));
        verifyNoInteractions(encoder);
    }

    @Test
    void bootstrapCreatesAdminWithHashOnlyWhenPasswordIsProvided() {
        UserRepository users = mock(UserRepository.class);
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        BootstrapAdminRunner runner = new BootstrapAdminRunner(users, encoder);
        runner.run();
        verifyNoInteractions(users);

        String privatePassword = UUID.randomUUID().toString();
        ReflectionTestUtils.setField(runner, "username", "local-admin");
        ReflectionTestUtils.setField(runner, "password", privatePassword);
        when(users.findByUsername("local-admin")).thenReturn(Optional.empty());
        runner.run();
        ArgumentCaptor<User> captured = ArgumentCaptor.forClass(User.class);
        verify(users).save(captured.capture());
        assertEquals("admin", captured.getValue().getRole());
        assertNotEquals(privatePassword, captured.getValue().getPassword());
        assertTrue(encoder.matches(privatePassword, captured.getValue().getPassword()));
    }

    @Test
    void disabledControlDoesNotLookUpOrContactDevices() {
        DeviceRepository devices = mock(DeviceRepository.class);
        ControlService control = new ControlService(devices, mock(ControlRuleRepository.class),
                mock(ControlLogRepository.class), new ObjectMapper());
        assertThrows(IllegalStateException.class,
                () -> control.sendControlCommand("example", java.util.Map.of(), "example", "example"));
        verifyNoInteractions(devices);
    }
}
