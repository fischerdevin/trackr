package com.devin.internshiptracker;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
class ProductionSecretsTests {
    private static final Path SECRETS = createSecrets();
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withPropertyValues(
                    "spring.config.import=configtree:" + SECRETS.toAbsolutePath() + "/",
                    "spring.profiles.active=production,mail,google",
                    "FRONTEND_URL=https://trackr.example.test",
                    "MAIL_HOST=localhost", "MAIL_PORT=2525", "MAIL_USERNAME=test-smtp-login",
                    "MAIL_FROM=trackr@example.test", "GOOGLE_CLIENT_ID=test-client");

    private static Path createSecrets() {
        try {
            Path path = Files.createTempDirectory("trackr-config-tree-");
            Files.writeString(path.resolve("db_password"), "test-db-password");
            Files.writeString(path.resolve("mail_password"), "test-mail-password");
            Files.writeString(path.resolve("google_client_secret"), "test-google-secret");
            return path;
        } catch (IOException exception) { throw new IllegalStateException(exception); }
    }

    @AfterAll static void clean() throws IOException {
        for (String name : new String[]{"db_password", "mail_password", "google_client_secret"}) {
            Files.deleteIfExists(SECRETS.resolve(name));
        }
        Files.deleteIfExists(SECRETS);
    }

    @Test void fileSecretsResolveWithoutPuttingValuesInProductionProperties() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            var environment = context.getEnvironment();
            assertThat(environment.getProperty("db_password")).isEqualTo("test-db-password");
            assertThat(environment.getProperty("spring.mail.password")).isEqualTo("test-mail-password");
            assertThat(environment.getProperty("spring.security.oauth2.client.registration.google.client-secret"))
                    .isEqualTo("test-google-secret");
        });
    }

    @Test void googleUsesThePublicHttpsCallbackBehindTheProxy() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getEnvironment().getProperty(
                    "spring.security.oauth2.client.registration.google.redirect-uri"))
                    .isEqualTo("https://trackr.example.test/login/oauth2/code/google");
        });
    }
}
