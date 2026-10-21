package com.devin.internshiptracker.service;

import com.devin.internshiptracker.entity.AppUser;
import com.devin.internshiptracker.repository.AppUserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

@Service
public class GoogleAccountService {
    private final AppUserRepository users;
    public GoogleAccountService(AppUserRepository users) { this.users = users; }

    // Only call with the identity returned by Spring's validating OIDC user service.
    public OidcUser provision(OidcUser identity) {
        if (!Boolean.TRUE.equals(identity.getEmailVerified()) || identity.getEmail() == null) {
            throw new OAuth2AuthenticationException("unverified_email");
        }
        // Returning users are identified by immutable Google subject, not email.
        if (users.findByGoogleSubject(identity.getSubject()).isPresent()) return identity;
        String email = AccountService.normalizeEmail(identity.getEmail());
        // Never link a password account without proof of ownership of that account.
        if (users.findByEmail(email).isPresent()) throw new OAuth2AuthenticationException("account_exists");
        try {
            users.saveAndFlush(new AppUser(email,
                    identity.getFullName() == null ? email : identity.getFullName(), null, identity.getSubject()));
        } catch (DataIntegrityViolationException exception) {
            throw new OAuth2AuthenticationException("account_exists");
        }
        return identity;
    }
}
