package com.devin.internshiptracker;

import com.devin.internshiptracker.entity.AppUser;
import com.devin.internshiptracker.repository.AppUserRepository;
import com.devin.internshiptracker.repository.JobApplicationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "app.mail.from=trackr@example.com")
@AutoConfigureMockMvc
class PasswordResetTests {
    @Autowired MockMvc mvc;
    @Autowired AppUserRepository users;
    @Autowired JobApplicationRepository applications;
    @Autowired PasswordEncoder passwords;
    @MockitoBean JavaMailSender sender;
    final BlockingQueue<SimpleMailMessage> messages = new LinkedBlockingQueue<>();
    static final String OLD_PASSWORD = "old-long-test-password";
    static final String NEW_PASSWORD = "new-long-test-password";

    @BeforeEach void setup() {
        applications.deleteAll();
        users.deleteAll();
        messages.clear();
        doAnswer(invocation -> {
            messages.add(new SimpleMailMessage(invocation.getArgument(0, SimpleMailMessage.class)));
            return null;
        }).when(sender).send(any(SimpleMailMessage.class));
    }

    AppUser account() { return users.save(new AppUser("alice@example.com", "Alice", passwords.encode(OLD_PASSWORD), null)); }
    String requestReset(String email) throws Exception {
        return mvc.perform(post("/api/auth/forgot-password").with(csrf()).header("Host", "untrusted.example")
                .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }
    String emailedToken() throws Exception {
        var message = messages.poll(5, TimeUnit.SECONDS);
        assertThat(message).isNotNull();
        assertThat(message.getTo()).containsExactly("alice@example.com");
        assertThat(message.getText()).contains("http://localhost:5173/#reset-password=");
        return message.getText().split("#reset-password=")[1].split("\\s")[0];
    }
    org.springframework.test.web.servlet.ResultActions reset(String token, String password) throws Exception {
        return mvc.perform(post("/api/auth/reset-password").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\",\"password\":\"" + password + "\"}"));
    }

    @Test void requestsDoNotDiscloseAccountsAndOnlyStoreHashedTokens() throws Exception {
        account();
        users.save(new AppUser("google@example.com", "Google", null, "subject"));
        String response = requestReset("ALICE@example.com");
        String token = emailedToken();
        var user = users.findByEmail("alice@example.com").orElseThrow();
        assertThat(token).hasSize(43);
        assertThat(response).doesNotContain(token);
        assertThat(user.getResetTokenHash()).hasSize(64).isNotEqualTo(token);
        assertThat(user.getResetTokenExpiresAt()).isAfter(Instant.now().plusSeconds(1700));
        assertThat(passwords.matches(OLD_PASSWORD, user.getPasswordHash())).isTrue();
        assertThat(requestReset("missing@example.com")).isEqualTo(response);
        assertThat(requestReset("google@example.com")).isEqualTo(response);
        assertThat(messages.poll(150, TimeUnit.MILLISECONDS)).isNull();
    }

    @Test void resetsPasswordOnceAndRejectsOldPasswordAndExistingSessions() throws Exception {
        account();
        var login = mvc.perform(post("/api/auth/login").with(csrf()).param("username", "alice@example.com")
                .param("password", OLD_PASSWORD)).andExpect(status().isNoContent()).andReturn();
        var session = (MockHttpSession) login.getRequest().getSession(false);
        requestReset("alice@example.com");
        String token = emailedToken();
        reset(token, NEW_PASSWORD).andExpect(status().isOk());
        var changed = users.findByEmail("alice@example.com").orElseThrow();
        assertThat(passwords.matches(NEW_PASSWORD, changed.getPasswordHash())).isTrue();
        assertThat(changed.getResetTokenHash()).isNull();
        assertThat(changed.getResetTokenExpiresAt()).isNull();
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());
        reset(token, "another-long-password").andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/login").with(csrf()).param("username", "alice@example.com").param("password", OLD_PASSWORD))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").with(csrf()).param("username", "alice@example.com").param("password", NEW_PASSWORD))
                .andExpect(status().isNoContent());
        var confirmation = messages.poll(5, TimeUnit.SECONDS);
        assertThat(confirmation).isNotNull();
        assertThat(confirmation.getSubject()).isEqualTo("Your Trackr password was changed");
        assertThat(confirmation.getText()).doesNotContain(NEW_PASSWORD, token);
    }

    @Test void expiresLinksAndRejectsUnknownTokens() throws Exception {
        var account = account();
        String token = "a".repeat(43);
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        account.issuePasswordReset(hash, Instant.now().minusSeconds(1), Instant.now().minusSeconds(1801));
        users.save(account);
        reset(token, NEW_PASSWORD).andExpect(status().isBadRequest());
        reset("b".repeat(43), NEW_PASSWORD).andExpect(status().isBadRequest());
        assertThat(passwords.matches(OLD_PASSWORD, users.findById(account.getId()).orElseThrow().getPasswordHash())).isTrue();
    }

    @Test void resetDisabledAccountsCannotRequestLinksOrRedeemExistingTokens() throws Exception {
        var account = account();
        account.disablePasswordReset();
        String token = "c".repeat(43);
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        // Simulate an outstanding link created before the account was protected.
        account.issuePasswordReset(hash, Instant.now().plusSeconds(1800), Instant.now().minusSeconds(61));
        users.save(account);
        assertThat(requestReset("alice@example.com")).isEqualTo(requestReset("missing@example.com"));
        assertThat(messages.poll(150, TimeUnit.MILLISECONDS)).isNull();
        reset(token, NEW_PASSWORD).andExpect(status().isBadRequest());
        var unchanged = users.findById(account.getId()).orElseThrow();
        assertThat(passwords.matches(OLD_PASSWORD, unchanged.getPasswordHash())).isTrue();
        assertThat(unchanged.isPasswordResetAllowed()).isFalse();
        mvc.perform(post("/api/auth/login").with(csrf()).param("username", "alice@example.com").param("password", OLD_PASSWORD))
                .andExpect(status().isNoContent());
    }

    @Test void repeatedRequestsAreThrottledAndNewLinksReplaceOldOnes() throws Exception {
        account();
        requestReset("alice@example.com");
        String oldToken = emailedToken();
        requestReset("alice@example.com");
        assertThat(messages.poll(150, TimeUnit.MILLISECONDS)).isNull();
        var account = users.findByEmail("alice@example.com").orElseThrow();
        account.issuePasswordReset(account.getResetTokenHash(), account.getResetTokenExpiresAt(), Instant.now().minusSeconds(61));
        users.save(account);
        requestReset("alice@example.com");
        String newToken = emailedToken();
        assertThat(newToken).isNotEqualTo(oldToken);
        reset(oldToken, NEW_PASSWORD).andExpect(status().isBadRequest());
        reset(newToken, NEW_PASSWORD).andExpect(status().isOk());
        assertThat(messages.poll(5, TimeUnit.SECONDS)).isNotNull();
    }

    @Test void validatesPasswordsAndRequiresCsrfOnBothEndpoints() throws Exception {
        account();
        requestReset("alice@example.com");
        String token = emailedToken();
        reset(token, "short").andExpect(status().isBadRequest());
        reset(token, "é".repeat(40)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"alice@example.com\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isForbidden());
        assertThat(passwords.matches(OLD_PASSWORD, users.findByEmail("alice@example.com").orElseThrow().getPasswordHash())).isTrue();
    }

    @Test void concurrentRequestsCannotReuseTheSameLink() throws Exception {
        account();
        requestReset("alice@example.com");
        String token = emailedToken();
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> reset(token, NEW_PASSWORD).andReturn().getResponse().getStatus());
            var second = executor.submit(() -> reset(token, "another-long-password").andReturn().getResponse().getStatus());
            assertThat(java.util.List.of(first.get(), second.get())).containsExactlyInAnyOrder(200, 400);
        }
        assertThat(messages.poll(5, TimeUnit.SECONDS)).isNotNull();
    }
}
