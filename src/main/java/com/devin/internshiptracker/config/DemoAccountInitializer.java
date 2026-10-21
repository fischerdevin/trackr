package com.devin.internshiptracker.config;

import com.devin.internshiptracker.entity.AppUser;
import com.devin.internshiptracker.repository.AppUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

@Component
@ConditionalOnProperty(name = "app.demo.enabled", havingValue = "true")
public class DemoAccountInitializer implements ApplicationRunner {
    public static final String EMAIL = "demo@trackr.test";
    private final AppUserRepository users;
    private final PasswordEncoder passwords;
    private final String initialPassword;

    public DemoAccountInitializer(AppUserRepository users, PasswordEncoder passwords,
            @Value("${app.demo.password:}") String initialPassword) {
        this.users = users;
        this.passwords = passwords;
        this.initialPassword = initialPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        var existing = users.findByEmail(EMAIL);
        if (existing.isPresent()) {
            // Restarting must never overwrite an account's existing password or settings.
            if (existing.get().isPasswordResetAllowed()) {
                throw new IllegalStateException("The reserved demo email is already in use by a regular account.");
            }
            return;
        }
        if (initialPassword.length() < 12 || initialPassword.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalStateException("Demo provisioning requires app.demo.password with at least 12 characters and at most 72 UTF-8 bytes.");
        }
        var demo = new AppUser(EMAIL, "Demo User", passwords.encode(initialPassword), null);
        demo.disablePasswordReset();
        users.saveAndFlush(demo);
    }
}
