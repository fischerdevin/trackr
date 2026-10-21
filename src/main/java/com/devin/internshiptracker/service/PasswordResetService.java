package com.devin.internshiptracker.service;

import com.devin.internshiptracker.repository.AppUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class PasswordResetService {
    private final AppUserRepository users;
    private final PasswordEncoder passwords;
    private final PasswordResetMailService mail;
    private final ApplicationEventPublisher events;
    private final SessionRegistry sessions;
    private final String frontendUrl;
    private final SecureRandom random = new SecureRandom();

    public PasswordResetService(AppUserRepository users, PasswordEncoder passwords, PasswordResetMailService mail,
            ApplicationEventPublisher events, SessionRegistry sessions,
            @Value("${app.frontend-url:http://localhost:5173}") String frontendUrl) {
        this.users = users;
        this.passwords = passwords;
        this.mail = mail;
        this.events = events;
        this.sessions = sessions;
        this.frontendUrl = frontendUrl.replaceAll("/+$", "");
    }

    @Transactional
    public void requestReset(String email) {
        if (!mail.isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Password reset email is not configured yet. Please try again later.");
        }
        var account = users.findForPasswordResetByEmail(AccountService.normalizeEmail(email));
        if (account.isEmpty() || account.get().getPasswordHash() == null || !account.get().isPasswordResetAllowed()) return;
        var user = account.get();
        Instant now = Instant.now();
        // Serialize requests per account and allow at most one recovery email per minute.
        if (user.getResetRequestedAt() != null && user.getResetRequestedAt().isAfter(now.minusSeconds(60))) return;
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        user.issuePasswordReset(digest(token), now.plus(30, ChronoUnit.MINUTES), now);
        // The fragment stays out of HTTP access logs and Referer headers.
        events.publishEvent(new PasswordResetMailService.ResetEmail(user.getEmail(),
                frontendUrl + "/#reset-password=" + token));
    }

    @Transactional
    public void resetPassword(String token, String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be at most 72 UTF-8 bytes");
        }
        var user = users.findForPasswordResetByTokenHash(digest(token))
                .filter(account -> account.isPasswordResetAllowed() && account.getPasswordHash() != null && account.getResetTokenExpiresAt() != null
                        && account.getResetTokenExpiresAt().isAfter(Instant.now()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "This reset link is invalid or has expired. Request a new one."));
        // A row lock prevents concurrent requests from using the same token twice.
        user.resetPassword(passwords.encode(password));
        for (Object principal : sessions.getAllPrincipals()) {
            if (principal instanceof UserDetails details && details.getUsername().equals(user.getEmail())) {
                sessions.getAllSessions(principal, false).forEach(session -> session.expireNow());
            }
        }
        events.publishEvent(new PasswordResetMailService.PasswordChangedEmail(user.getEmail()));
    }

    private static String digest(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
