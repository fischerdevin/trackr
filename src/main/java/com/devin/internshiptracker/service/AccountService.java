package com.devin.internshiptracker.service;

import com.devin.internshiptracker.entity.AppUser;
import com.devin.internshiptracker.repository.AppUserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

@Service
public class AccountService {
    private final AppUserRepository users;
    public AccountService(AppUserRepository users) { this.users = users; }

    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public AppUser currentUser() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please sign in");
        }
        var account = authentication.getPrincipal() instanceof OidcUser oidc
                ? users.findByGoogleSubject(oidc.getSubject())
                : users.findByEmail(authentication.getName());
        return account.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please sign in"));
    }
}
