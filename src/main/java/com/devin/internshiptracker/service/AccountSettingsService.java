package com.devin.internshiptracker.service;

import com.devin.internshiptracker.config.DemoAccountInitializer;
import com.devin.internshiptracker.entity.AppUser;
import com.devin.internshiptracker.repository.AppUserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;

@Service
public class AccountSettingsService {
    public record ProfileChange(AppUser user, boolean requiresLogin) {}
    public record CredentialsChanged(String previousEmail) {}

    private final AppUserRepository users;
    private final AccountService accounts;
    private final PasswordEncoder passwords;
    private final ApplicationEventPublisher events;
    private final SessionRegistry sessions;

    public AccountSettingsService(AppUserRepository users, AccountService accounts, PasswordEncoder passwords,
            ApplicationEventPublisher events, SessionRegistry sessions) {
        this.users = users;
        this.accounts = accounts;
        this.passwords = passwords;
        this.events = events;
        this.sessions = sessions;
    }

    public static boolean credentialsEditable(AppUser user) {
        return user.getPasswordHash() != null && user.getGoogleSubject() == null
                && user.isPasswordResetAllowed() && !DemoAccountInitializer.EMAIL.equals(user.getEmail());
    }

    @Transactional
    public ProfileChange updateProfile(String name, String email, String currentPassword) {
        var user = lockedCurrentUser();
        String normalizedEmail = AccountService.normalizeEmail(email);
        boolean emailChanged = !user.getEmail().equals(normalizedEmail);
        if (emailChanged) {
            requireEditableCredentials(user);
            verifyPassword(user, currentPassword);
            if (DemoAccountInitializer.EMAIL.equals(normalizedEmail) || users.findByEmail(normalizedEmail).isPresent()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "This email is unavailable. Choose another email.");
            }
        }
        String previousEmail = user.getEmail();
        user.updateName(name.trim());
        if (emailChanged) user.updateEmail(normalizedEmail);
        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This email is unavailable. Choose another email.");
        }
        if (emailChanged) {
            events.publishEvent(new CredentialsChanged(previousEmail));
            events.publishEvent(new PasswordResetMailService.EmailChangedEmail(previousEmail, normalizedEmail));
        }
        return new ProfileChange(user, emailChanged);
    }

    @Transactional
    public void changePassword(String currentPassword, String newPassword) {
        var user = lockedCurrentUser();
        requireEditableCredentials(user);
        verifyPassword(user, currentPassword);
        if (newPassword.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be at most 72 UTF-8 bytes");
        }
        if (passwords.matches(newPassword, user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a password different from your current password.");
        }
        user.resetPassword(passwords.encode(newPassword));
        users.flush();
        events.publishEvent(new CredentialsChanged(user.getEmail()));
        events.publishEvent(new PasswordResetMailService.PasswordChangedEmail(user.getEmail()));
    }

    private AppUser lockedCurrentUser() {
        var current = accounts.currentUser();
        String authenticatedEmail = current.getEmail();
        var user = users.findForSettingsById(current.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please sign in again"));
        if (!user.getEmail().equals(authenticatedEmail)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please sign in again");
        }
        return user;
    }

    private void requireEditableCredentials(AppUser user) {
        if (!credentialsEditable(user)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, user.getGoogleSubject() != null
                    ? "Manage your email and password through your Google account."
                    : "Email and password changes are disabled for this demo account.");
        }
    }

    private void verifyPassword(AppUser user, String password) {
        if (password == null || password.getBytes(StandardCharsets.UTF_8).length > 72
                || !passwords.matches(password, user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Your current password is incorrect.");
        }
    }

    @TransactionalEventListener
    public void expireSessions(CredentialsChanged event) {
        for (Object principal : sessions.getAllPrincipals()) {
            if (principal instanceof UserDetails details && details.getUsername().equals(event.previousEmail())) {
                sessions.getAllSessions(principal, false).forEach(session -> session.expireNow());
            }
        }
    }
}
