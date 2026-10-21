package com.devin.internshiptracker;

import com.devin.internshiptracker.config.DemoAccountInitializer;
import com.devin.internshiptracker.entity.AppUser;
import com.devin.internshiptracker.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"app.demo.enabled=true", "app.demo.password=test-demo-password"})
@AutoConfigureMockMvc
class DemoAccountTests {
    @Autowired AppUserRepository users;
    @Autowired PasswordEncoder passwords;
    @Autowired DemoAccountInitializer initializer;
    @Autowired MockMvc mvc;

    @Test void startupCreatesDemoWithHashedPasswordAndIdempotentProvisioning() throws Exception {
        var demo = users.findByEmail(DemoAccountInitializer.EMAIL).orElseThrow();
        assertThat(demo.isPasswordResetAllowed()).isFalse();
        assertThat(passwords.matches("test-demo-password", demo.getPasswordHash())).isTrue();
        assertThat(demo.getResetTokenHash()).isNull();
        initializer.run(null);
        var sameAccount = users.findByEmail(DemoAccountInitializer.EMAIL).orElseThrow();
        assertThat(sameAccount.getId()).isEqualTo(demo.getId());
        assertThat(sameAccount.getPasswordHash()).isEqualTo(demo.getPasswordHash());
        mvc.perform(post("/api/auth/login").with(csrf()).param("username", DemoAccountInitializer.EMAIL)
                .param("password", "test-demo-password")).andExpect(status().isNoContent());
    }

    @Test void regularAccountsRemainResettableAndRegistrationCannotChangePolicy() throws Exception {
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Owner\",\"email\":\"owner@example.com\",\"password\":\"test-owner-password\",\"passwordResetAllowed\":false}"))
                .andExpect(status().isCreated());
        assertThat(users.findByEmail("owner@example.com").orElseThrow().isPasswordResetAllowed()).isTrue();
        assertThat(new AppUser("new@example.com", "New user", "hash", null).isPasswordResetAllowed()).isTrue();
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Demo\",\"email\":\"DEMO@trackr.test\",\"password\":\"test-other-password\"}"))
                .andExpect(status().isConflict());
    }
}
