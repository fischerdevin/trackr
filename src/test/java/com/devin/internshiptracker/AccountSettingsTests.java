package com.devin.internshiptracker;

import com.devin.internshiptracker.entity.AppUser;
import com.devin.internshiptracker.entity.JobApplication;
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
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "app.mail.from=trackr@example.com")
@AutoConfigureMockMvc
class AccountSettingsTests {
    @Autowired MockMvc mvc;
    @Autowired AppUserRepository users;
    @Autowired JobApplicationRepository applications;
    @Autowired PasswordEncoder passwords;
    @MockitoBean JavaMailSender sender;
    final BlockingQueue<SimpleMailMessage> messages = new LinkedBlockingQueue<>();
    static final String PASSWORD = "old-long-test-password";
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

    AppUser account() { return users.save(new AppUser("alice@example.com", "Alice", passwords.encode(PASSWORD), null)); }
    MockHttpSession login(String email, String password) throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/auth/login").with(csrf())
                .param("username", email).param("password", password))
                .andExpect(status().isNoContent()).andReturn().getRequest().getSession(false);
    }
    ResultActions profile(MockHttpSession session, String body) throws Exception {
        return mvc.perform(patch("/api/auth/me").session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }
    ResultActions password(MockHttpSession session, String current, String next) throws Exception {
        return mvc.perform(put("/api/auth/password").session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"" + current + "\",\"newPassword\":\"" + next + "\"}"));
    }

    @Test void profileUpdatesNameWithoutSigningOutOrExposingCredentials() throws Exception {
        var alice = account();
        var session = login(alice.getEmail(), PASSWORD);
        profile(session, "{\"name\":\"  Alice Example  \",\"email\":\"ALICE@example.com\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.requiresLogin").value(false))
                .andExpect(jsonPath("$.user.name").value("Alice Example"))
                .andExpect(jsonPath("$.user.credentialsEditable").value(true))
                .andExpect(jsonPath("$.user.provider").value("PASSWORD"))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.user.resetTokenHash").doesNotExist());
        assertThat(users.findById(alice.getId()).orElseThrow().getName()).isEqualTo("Alice Example");
        assertThat(session.isInvalid()).isFalse();
        mvc.perform(get("/api/auth/me").session(session)).andExpect(jsonPath("$.name").value("Alice Example"));
    }

    @Test void emailChangeRequiresCurrentPasswordAndLeavesDataUntouchedOnFailure() throws Exception {
        var alice = account();
        var session = login(alice.getEmail(), PASSWORD);
        profile(session, "{\"name\":\"Changed\",\"email\":\"new@example.com\"}").andExpect(status().isBadRequest());
        profile(session, "{\"name\":\"Changed\",\"email\":\"new@example.com\",\"currentPassword\":\"wrong-password\"}")
                .andExpect(status().isBadRequest());
        var unchanged = users.findById(alice.getId()).orElseThrow();
        assertThat(unchanged.getEmail()).isEqualTo(alice.getEmail());
        assertThat(unchanged.getName()).isEqualTo("Alice");
        assertThat(session.isInvalid()).isFalse();
    }

    @Test void emailChangeKeepsApplicationsExpiresAllSessionsAndClearsResetTokens() throws Exception {
        var alice = account();
        alice.issuePasswordReset("a".repeat(64), Instant.now().plusSeconds(1800), Instant.now());
        users.save(alice);
        var application = new JobApplication();
        application.setOwner(alice);
        application.setCompanyName("Example Company");
        application.setJobTitle("Intern");
        applications.save(application);
        var first = login(alice.getEmail(), PASSWORD);
        var second = login(alice.getEmail(), PASSWORD);
        profile(first, "{\"name\":\"Alice\",\"email\":\"NEW@example.com\",\"currentPassword\":\"" + PASSWORD + "\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.requiresLogin").value(true))
                .andExpect(jsonPath("$.user.email").value("new@example.com"));
        assertThat(first.isInvalid()).isTrue();
        mvc.perform(get("/api/auth/me").session(second)).andExpect(status().isUnauthorized());
        var updated = users.findById(alice.getId()).orElseThrow();
        assertThat(updated.getResetTokenHash()).isNull();
        assertThat(updated.getResetTokenExpiresAt()).isNull();
        mvc.perform(post("/api/auth/login").with(csrf()).param("username", alice.getEmail()).param("password", PASSWORD))
                .andExpect(status().isUnauthorized());
        var next = login("new@example.com", PASSWORD);
        mvc.perform(get("/api/applications").session(next)).andExpect(jsonPath("$.length()").value(1));
        var oldNotice = messages.poll(5, TimeUnit.SECONDS);
        var newNotice = messages.poll(5, TimeUnit.SECONDS);
        assertThat(oldNotice).isNotNull();
        assertThat(newNotice).isNotNull();
        assertThat(oldNotice.getTo()).containsExactly("alice@example.com");
        assertThat(newNotice.getTo()).containsExactly("new@example.com");
        assertThat(oldNotice.getText()).doesNotContain(PASSWORD);
    }

    @Test void duplicateAndReservedEmailsAreRejectedWithoutPartialChanges() throws Exception {
        var alice = account();
        users.save(new AppUser("bob@example.com", "Bob", passwords.encode(PASSWORD), null));
        var session = login(alice.getEmail(), PASSWORD);
        for (String email : new String[]{"BOB@example.com", "demo@trackr.test"}) {
            profile(session, "{\"name\":\"Changed\",\"email\":\"" + email + "\",\"currentPassword\":\"" + PASSWORD + "\"}")
                    .andExpect(status().isConflict());
        }
        assertThat(users.findById(alice.getId()).orElseThrow().getName()).isEqualTo("Alice");
    }

    @Test void passwordChangeHashesPasswordClearsResetTokensAndSignsOutAllSessions() throws Exception {
        var alice = account();
        alice.issuePasswordReset("b".repeat(64), Instant.now().plusSeconds(1800), Instant.now());
        users.save(alice);
        var first = login(alice.getEmail(), PASSWORD);
        var second = login(alice.getEmail(), PASSWORD);
        password(first, PASSWORD, NEW_PASSWORD).andExpect(status().isOk());
        var updated = users.findById(alice.getId()).orElseThrow();
        assertThat(updated.getPasswordHash()).isNotEqualTo(NEW_PASSWORD);
        assertThat(passwords.matches(NEW_PASSWORD, updated.getPasswordHash())).isTrue();
        assertThat(updated.getResetTokenHash()).isNull();
        assertThat(first.isInvalid()).isTrue();
        mvc.perform(get("/api/auth/me").session(second)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").with(csrf()).param("username", alice.getEmail()).param("password", PASSWORD))
                .andExpect(status().isUnauthorized());
        login(alice.getEmail(), NEW_PASSWORD);
        var notice = messages.poll(5, TimeUnit.SECONDS);
        assertThat(notice).isNotNull();
        assertThat(notice.getSubject()).isEqualTo("Your Trackr password was changed");
        assertThat(notice.getText()).doesNotContain(PASSWORD, NEW_PASSWORD);
    }

    @Test void passwordChangesRejectWrongCurrentShortOversizedAndUnchangedPasswords() throws Exception {
        var alice = account();
        var session = login(alice.getEmail(), PASSWORD);
        password(session, "wrong-password", NEW_PASSWORD).andExpect(status().isBadRequest());
        password(session, PASSWORD, "short").andExpect(status().isBadRequest());
        password(session, PASSWORD, "é".repeat(40)).andExpect(status().isBadRequest());
        password(session, PASSWORD, PASSWORD).andExpect(status().isBadRequest());
        assertThat(passwords.matches(PASSWORD, users.findById(alice.getId()).orElseThrow().getPasswordHash())).isTrue();
        assertThat(session.isInvalid()).isFalse();
    }

    @Test void demoCredentialsCannotBeChangedEvenByCallingTheApiDirectly() throws Exception {
        var demo = new AppUser("demo@trackr.test", "Demo User", passwords.encode(PASSWORD), null);
        demo.disablePasswordReset();
        users.save(demo);
        var session = login(demo.getEmail(), PASSWORD);
        mvc.perform(get("/api/auth/me").session(session)).andExpect(jsonPath("$.credentialsEditable").value(false));
        profile(session, "{\"name\":\"Demo\",\"email\":\"changed@example.com\",\"currentPassword\":\"" + PASSWORD + "\"}")
                .andExpect(status().isForbidden());
        password(session, PASSWORD, NEW_PASSWORD).andExpect(status().isForbidden());
        profile(session, "{\"name\":\"Demo Visitor\",\"email\":\"demo@trackr.test\"}").andExpect(status().isOk());
        assertThat(users.findById(demo.getId()).orElseThrow().getEmail()).isEqualTo(demo.getEmail());
        assertThat(passwords.matches(PASSWORD, users.findById(demo.getId()).orElseThrow().getPasswordHash())).isTrue();
    }

    @Test void googleUsersCanUpdateTheirTrackrNameButNotManagedCredentials() throws Exception {
        var google = users.save(new AppUser("google@example.com", "Google User", null, "google-settings"));
        var identity = oidcLogin().idToken(token -> token.subject("google-settings").claim("email", google.getEmail()));
        mvc.perform(get("/api/auth/me").with(identity)).andExpect(jsonPath("$.provider").value("GOOGLE"))
                .andExpect(jsonPath("$.credentialsEditable").value(false));
        mvc.perform(patch("/api/auth/me").with(identity).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"New Name\",\"email\":\"google@example.com\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.name").value("New Name"));
        mvc.perform(patch("/api/auth/me").with(identity).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"New Name\",\"email\":\"changed@example.com\",\"currentPassword\":\"anything\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/auth/password").with(identity).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"anything\",\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test void updatesOnlyTheSignedInUserAndCannotChangeAccountPolicyViaExtraFields() throws Exception {
        var alice = account();
        var bob = users.save(new AppUser("bob@example.com", "Bob", passwords.encode(PASSWORD), null));
        var session = login(alice.getEmail(), PASSWORD);
        profile(session, "{\"id\":" + bob.getId() + ",\"name\":\"Alice Updated\",\"email\":\"alice@example.com\","
                + "\"passwordResetAllowed\":false,\"googleSubject\":\"attacker\",\"passwordHash\":\"plaintext\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.id").value(alice.getId()));
        assertThat(users.findById(bob.getId()).orElseThrow().getName()).isEqualTo("Bob");
        var updated = users.findById(alice.getId()).orElseThrow();
        assertThat(updated.isPasswordResetAllowed()).isTrue();
        assertThat(updated.getGoogleSubject()).isNull();
        assertThat(passwords.matches(PASSWORD, updated.getPasswordHash())).isTrue();
    }

    @Test void validatesProfileFieldsAndRequiresAuthenticationAndCsrf() throws Exception {
        var alice = account();
        var session = login(alice.getEmail(), PASSWORD);
        for (String body : new String[]{"{\"name\":\" \",\"email\":\"alice@example.com\"}",
                "{\"name\":\"Alice\",\"email\":\"not-an-email\"}",
                "{\"name\":\"" + "a".repeat(101) + "\",\"email\":\"alice@example.com\"}"}) {
            profile(session, body).andExpect(status().isBadRequest());
        }
        mvc.perform(patch("/api/auth/me").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(put("/api/auth/password").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/auth/me").session(session).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/auth/password").session(session).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }
}
