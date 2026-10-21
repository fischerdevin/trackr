package com.devin.internshiptracker;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.security.oauth2.client.registration.google.client-id=test-client",
        "spring.security.oauth2.client.registration.google.client-secret=test-secret",
        "spring.security.oauth2.client.registration.google.scope=openid,profile,email",
        "spring.security.oauth2.client.registration.google.redirect-uri=http://localhost:5173/login/oauth2/code/google"
})
@AutoConfigureMockMvc
class GoogleOAuthConfigurationTests {
    @Autowired MockMvc mvc;

    @Test void googleAuthorizationRedirectIncludesStateAndExpectedCallback() throws Exception {
        mvc.perform(get("/api/auth/config")).andExpect(jsonPath("$.googleEnabled").value(true));
        var result = mvc.perform(get("/oauth2/authorization/google"))
                .andExpect(status().is3xxRedirection()).andReturn();
        String location = result.getResponse().getRedirectedUrl();
        assertThat(location).startsWith("https://accounts.google.com/")
                .contains("state=", "client_id=test-client", "scope=openid", "redirect_uri=http://localhost:5173/login/oauth2/code/google");
        assertThat(result.getRequest().getSession(false)).isNotNull();
    }

    @Test void invalidCallbackCannotCreateAnAuthenticatedSession() throws Exception {
        mvc.perform(get("/login/oauth2/code/google").param("code", "fake").param("state", "fake"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost:5173/?authError=google"));
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }
}
