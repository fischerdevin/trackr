package com.devin.internshiptracker;

import com.devin.internshiptracker.entity.AppUser;
import com.devin.internshiptracker.repository.AppUserRepository;
import com.devin.internshiptracker.service.GoogleAccountService;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class GoogleAccountServiceTests {
    private final AppUserRepository users = mock(AppUserRepository.class);
    private final GoogleAccountService service = new GoogleAccountService(users);

    private OidcUser identity(boolean verified) {
        var identity = mock(OidcUser.class);
        when(identity.getEmailVerified()).thenReturn(verified);
        when(identity.getEmail()).thenReturn("Alice@example.com");
        when(identity.getSubject()).thenReturn("google-123");
        return identity;
    }

    @Test void rejectsUnverifiedGoogleEmails() {
        assertThatThrownBy(() -> service.provision(identity(false))).isInstanceOf(OAuth2AuthenticationException.class);
        verifyNoInteractions(users);
    }

    @Test void neverAutomaticallyLinksAnExistingPasswordAccount() {
        when(users.findByEmail("alice@example.com")).thenReturn(Optional.of(new AppUser("alice@example.com", "Alice", "hash", null)));
        assertThatThrownBy(() -> service.provision(identity(true))).isInstanceOf(OAuth2AuthenticationException.class);
        verify(users, never()).saveAndFlush(any());
    }

    @Test void createsGoogleAccountsWithoutLocalPasswords() {
        service.provision(identity(true));
        verify(users).saveAndFlush(argThat(account -> account.getEmail().equals("alice@example.com")
                && account.getGoogleSubject().equals("google-123") && account.getPasswordHash() == null));
    }

    @Test void returningGoogleUsersAreMatchedBySubject() {
        when(users.findByGoogleSubject("google-123")).thenReturn(Optional.of(new AppUser("old@example.com", "Alice", null, "google-123")));
        service.provision(identity(true));
        verify(users, never()).findByEmail(any());
        verify(users, never()).saveAndFlush(any());
    }
}
