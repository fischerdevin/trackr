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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AuthenticationTests {
    @Autowired MockMvc mvc;
    @Autowired AppUserRepository users;
    @Autowired JobApplicationRepository applications;
    @Autowired PasswordEncoder passwords;
    private static final String PASSWORD = "a-long-test-password";

    @BeforeEach
    void clean() {
        applications.deleteAll();
        users.deleteAll();
    }

    @Test
    void registrationHashesPasswordsAndRejectsInvalidOrDuplicateAccounts() throws Exception {
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Alice\",\"email\":\"ALICE@example.com\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.passwordHash").doesNotExist());
        var account = users.findByEmail("alice@example.com").orElseThrow();
        assertThat(account.getPasswordHash()).isNotEqualTo(PASSWORD);
        assertThat(passwords.matches(PASSWORD, account.getPasswordHash())).isTrue();
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Alice\",\"email\":\"alice@example.com\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Bob\",\"email\":\"bob@example.com\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Bob\",\"email\":\"bob@example.com\",\"password\":\"" + "é".repeat(40) + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void loginPersistsSessionAndLogoutInvalidatesIt() throws Exception {
        users.save(new AppUser("alice@example.com", "Alice", passwords.encode(PASSWORD), null));
        mvc.perform(post("/api/auth/login").with(csrf()).param("username", "alice@example.com").param("password", "incorrect"))
                .andExpect(status().isUnauthorized());
        var result = mvc.perform(post("/api/auth/login").with(csrf()).param("username", "ALICE@example.com").param("password", PASSWORD))
                .andExpect(status().isNoContent()).andReturn();
        var session = (MockHttpSession) result.getRequest().getSession(false);
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("alice@example.com"));
        mvc.perform(post("/api/auth/logout").session(session).with(csrf())).andExpect(status().isNoContent());
        assertThat(session.isInvalid()).isTrue();
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void browserCanFetchCsrfThenLoginAndMutateUsingTheRotatedSession() throws Exception {
        users.save(new AppUser("alice@example.com", "Alice", passwords.encode(PASSWORD), null));
        var bootstrap = mvc.perform(get("/api/auth/csrf")).andReturn();
        String token = com.jayway.jsonpath.JsonPath.read(bootstrap.getResponse().getContentAsString(), "$.token");
        var session = (MockHttpSession) bootstrap.getRequest().getSession(false);
        mvc.perform(post("/api/auth/login").session(session).header("X-CSRF-TOKEN", token)
                .param("username", "alice@example.com").param("password", PASSWORD)).andExpect(status().isNoContent());
        var refresh = mvc.perform(get("/api/auth/csrf").session(session)).andReturn();
        String refreshedToken = com.jayway.jsonpath.JsonPath.read(refresh.getResponse().getContentAsString(), "$.token");
        mvc.perform(post("/api/applications").session(session).header("X-CSRF-TOKEN", refreshedToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"companyName\":\"Acme\",\"jobTitle\":\"Intern\"}"))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/applications").session(session)).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void missingMailConfigurationReturnsAnExplicitError() throws Exception {
        mvc.perform(post("/api/auth/forgot-password").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"alice@example.com\"}"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void anonymousRequestsAndMissingCsrfAreRejected() throws Exception {
        mvc.perform(get("/api/applications")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/login").param("username", "x").param("password", "x"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/logout")).andExpect(status().isForbidden());
        mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void anotherUserCannotReadModifyDeleteOrOverwriteAnApplication() throws Exception {
        var alice = users.save(new AppUser("alice@example.com", "Alice", passwords.encode(PASSWORD), null));
        users.save(new AppUser("bob@example.com", "Bob", passwords.encode(PASSWORD), null));
        var application = new JobApplication();
        application.setCompanyName("Acme");
        application.setJobTitle("Intern");
        application.setOwner(alice);
        Long id = applications.save(application).getId();
        for (String filter : new String[]{"", "?status=SAVED", "?company=Acme", "?search=Intern"}) {
            mvc.perform(get("/api/applications" + filter).with(user("bob@example.com")))
                    .andExpect(status().isOk()).andExpect(content().json("[]"));
        }
        mvc.perform(get("/api/applications/" + id).with(user("bob@example.com"))).andExpect(status().isNotFound());
        String body = "{\"id\":" + id + ",\"companyName\":\"Changed\",\"jobTitle\":\"Intern\"}";
        mvc.perform(put("/api/applications/" + id).with(user("bob@example.com")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/applications/" + id).with(user("bob@example.com")).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/applications").with(user("bob@example.com")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.owner").doesNotExist());
        assertThat(applications.findById(id).orElseThrow().getCompanyName()).isEqualTo("Acme");
        mvc.perform(get("/api/applications").with(user("alice@example.com")))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void googleSessionsUseSubjectAndCannotLogInWithAPassword() throws Exception {
        users.save(new AppUser("google@example.com", "Google User", null, "google-123"));
        mvc.perform(get("/api/auth/me").with(oidcLogin().idToken(token -> token.subject("google-123")
                .claim("email", "changed@example.com"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value("google@example.com"));
        mvc.perform(post("/api/auth/login").with(csrf()).param("username", "google@example.com").param("password", PASSWORD))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unownedLegacyApplicationsAreNotExposed() throws Exception {
        users.save(new AppUser("alice@example.com", "Alice", passwords.encode(PASSWORD), null));
        var application = new JobApplication();
        application.setCompanyName("Legacy");
        application.setJobTitle("Intern");
        Long id = applications.save(application).getId();
        mvc.perform(get("/api/applications").with(user("alice@example.com")))
                .andExpect(content().json("[]"));
        mvc.perform(get("/api/applications/" + id).with(user("alice@example.com")))
                .andExpect(status().isNotFound());
    }
}
