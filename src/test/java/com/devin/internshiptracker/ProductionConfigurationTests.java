package com.devin.internshiptracker;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.env.Environment;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.profiles.active=production",
        "spring.config.import=",
        "spring.datasource.url=jdbc:h2:mem:production-tests;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.frontend-url=https://trackr.example.test"
})
@AutoConfigureMockMvc
class ProductionConfigurationTests {
    @Autowired MockMvc mvc;
    @Autowired Environment environment;

    @Test void healthIsAvailableWithoutExposingDatabaseOrConfigurationDetails() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(jsonPath("$.details").doesNotExist());
        mvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/applications")).andExpect(status().isUnauthorized());
    }

    @Test void productionUsesSecureCookiesTrustedProxyHandlingAndNoDemoProvisioning() throws Exception {
        assertThat(environment.getProperty("server.servlet.session.cookie.secure", Boolean.class)).isTrue();
        assertThat(environment.getProperty("server.forward-headers-strategy")).isEqualTo("framework");
        assertThat(environment.getProperty("app.demo.enabled", Boolean.class)).isFalse();
        assertThat(environment.getProperty("spring.jpa.open-in-view", Boolean.class)).isFalse();
        assertThat(environment.getProperty("management.endpoints.web.exposure.include")).isEqualTo("health");
        mvc.perform(get("/api/auth/config")).andExpect(jsonPath("$.googleEnabled").value(false));
    }
}
