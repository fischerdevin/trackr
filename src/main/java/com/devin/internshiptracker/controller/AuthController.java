package com.devin.internshiptracker.controller;

import com.devin.internshiptracker.entity.AppUser;
import com.devin.internshiptracker.config.DemoAccountInitializer;
import com.devin.internshiptracker.repository.AppUserRepository;
import com.devin.internshiptracker.service.AccountService;
import com.devin.internshiptracker.service.AccountSettingsService;
import com.devin.internshiptracker.service.PasswordResetService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AppUserRepository users;
    private final PasswordEncoder passwords;
    private final AccountService accounts;
    private final ObjectProvider<ClientRegistrationRepository> registrations;
    private final PasswordResetService passwordResets;
    private final AccountSettingsService settings;

    public AuthController(AppUserRepository users, PasswordEncoder passwords, AccountService accounts,
            ObjectProvider<ClientRegistrationRepository> registrations, PasswordResetService passwordResets,
            AccountSettingsService settings) {
        this.users = users;
        this.passwords = passwords;
        this.accounts = accounts;
        this.registrations = registrations;
        this.passwordResets = passwordResets;
        this.settings = settings;
    }

    public record Registration(@NotBlank @Size(max = 100) String name,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 12, max = 72) String password) {}
    public record ForgotPassword(@NotBlank @Email @Size(max = 254) String email) {}
    public record ResetPassword(@NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}") String token,
            @NotBlank @Size(min = 12, max = 72) String password) {}
    public record ProfileUpdate(@NotBlank @Size(max = 100) String name,
            @NotBlank @Email @Size(max = 254) String email, @Size(max = 72) String currentPassword) {}
    public record PasswordChange(@NotBlank @Size(max = 72) String currentPassword,
            @NotBlank @Size(min = 12, max = 72) String newPassword) {}
    public record ProfileUpdateResponse(Profile user, boolean requiresLogin) {}

    @PostMapping("/forgot-password")
    public Map<String, String> forgotPassword(@Valid @RequestBody ForgotPassword input) {
        passwordResets.requestReset(input.email());
        return Map.of("message", "If this email belongs to a password account, you will receive a reset link shortly.");
    }

    @PostMapping("/reset-password")
    public Map<String, String> resetPassword(@Valid @RequestBody ResetPassword input,
            jakarta.servlet.http.HttpServletRequest request) {
        passwordResets.resetPassword(input.token(), input.password());
        var session = request.getSession(false);
        if (session != null) session.invalidate();
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        return Map.of("message", "Your password has been reset. Sign in with your new password.");
    }
    public record Profile(Long id, String name, String email, String provider, boolean credentialsEditable) {
        static Profile from(AppUser user) {
            return new Profile(user.getId(), user.getName(), user.getEmail(),
                    user.getGoogleSubject() == null ? "PASSWORD" : "GOOGLE",
                    AccountSettingsService.credentialsEditable(user));
        }
    }

    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("token", token.getToken(), "headerName", token.getHeaderName());
    }

    @GetMapping("/config")
    public Map<String, Boolean> config() {
        return Map.of("googleEnabled", registrations.getIfAvailable() != null);
    }

    @GetMapping("/me")
    public Profile me() { return Profile.from(accounts.currentUser()); }

    @PatchMapping("/me")
    public ProfileUpdateResponse updateProfile(@Valid @RequestBody ProfileUpdate input,
            jakarta.servlet.http.HttpServletRequest request) {
        var result = settings.updateProfile(input.name(), input.email(), input.currentPassword());
        if (result.requiresLogin()) signOutCurrentSession(request);
        return new ProfileUpdateResponse(Profile.from(result.user()), result.requiresLogin());
    }

    @PutMapping("/password")
    public Map<String, String> changePassword(@Valid @RequestBody PasswordChange input,
            jakarta.servlet.http.HttpServletRequest request) {
        settings.changePassword(input.currentPassword(), input.newPassword());
        signOutCurrentSession(request);
        return Map.of("message", "Your password has been changed. Sign in with your new password.");
    }

    private void signOutCurrentSession(jakarta.servlet.http.HttpServletRequest request) {
        var session = request.getSession(false);
        if (session != null) session.invalidate();
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Profile register(@Valid @RequestBody Registration input) {
        if (input.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be at most 72 UTF-8 bytes");
        }
        String email = AccountService.normalizeEmail(input.email());
        if (DemoAccountInitializer.EMAIL.equals(email) || users.findByEmail(email).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Unable to register with this email. Try signing in.");
        }
        try {
            return Profile.from(users.saveAndFlush(new AppUser(email, input.name().trim(), passwords.encode(input.password()), null)));
        } catch (org.springframework.dao.DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Unable to register with this email. Try signing in.");
        }
    }
}
