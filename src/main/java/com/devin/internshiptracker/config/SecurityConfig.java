package com.devin.internshiptracker.config;

import com.devin.internshiptracker.repository.AppUserRepository;
import com.devin.internshiptracker.service.AccountService;
import com.devin.internshiptracker.service.GoogleAccountService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.beans.factory.ObjectProvider;

@Configuration
public class SecurityConfig {
    @Bean
    org.springframework.security.core.session.SessionRegistry sessionRegistry() {
        return new org.springframework.security.core.session.SessionRegistryImpl();
    }

    @Bean
    org.springframework.security.web.session.HttpSessionEventPublisher sessionEventPublisher() {
        return new org.springframework.security.web.session.HttpSessionEventPublisher();
    }
    @Bean
    PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }

    @Bean
    UserDetailsService userDetailsService(AppUserRepository users) {
        return email -> {
            var account = users.findByEmail(AccountService.normalizeEmail(email))
                    .filter(user -> user.getPasswordHash() != null)
                    .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
            return User.withUsername(account.getEmail()).password(account.getPasswordHash()).roles("USER").build();
        };
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, GoogleAccountService googleAccounts,
            org.springframework.security.core.session.SessionRegistry sessions,
            ObjectProvider<ClientRegistrationRepository> registrations,
            @Value("${app.frontend-url:http://localhost:5173}") String frontendUrl) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/csrf", "/api/auth/register", "/api/auth/login", "/api/auth/config", "/api/auth/forgot-password", "/api/auth/reset-password", "/oauth2/**", "/login/oauth2/**", "/error").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/actuator/health").permitAll()
                .anyRequest().authenticated())
            .sessionManagement(session -> session.maximumSessions(-1).sessionRegistry(sessions)
                .expiredSessionStrategy(event -> {
                    event.getResponse().setStatus(401);
                    event.getResponse().setContentType("application/json");
                    event.getResponse().getWriter().write("{\"message\":\"Your session has expired. Please sign in again.\"}");
                }))
            .requestCache(cache -> cache.disable())
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((request, response, exception) -> {
                    response.setStatus(401);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"message\":\"Please sign in\"}");
                })
                .accessDeniedHandler((request, response, exception) -> {
                    response.setStatus(403);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"message\":\"Request rejected. Refresh and try again.\"}");
                }))
            .formLogin(form -> form.loginProcessingUrl("/api/auth/login")
                .successHandler((request, response, auth) -> response.setStatus(204))
                .failureHandler((request, response, exception) -> {
                    response.setStatus(401);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"message\":\"Invalid email or password\"}");
                }))
            .logout(logout -> logout.logoutUrl("/api/auth/logout")
                .deleteCookies("JSESSIONID")
                .logoutSuccessHandler((request, response, auth) -> response.setStatus(204)));

        if (registrations.getIfAvailable() != null) {
            http.oauth2Login(oauth -> oauth
                .userInfoEndpoint(info -> info.oidcUserService(request -> {
                    return googleAccounts.provision(new OidcUserService().loadUser(request));
                }))
                .successHandler((request, response, authentication) -> response.sendRedirect(frontendUrl))
                .failureHandler((request, response, exception) -> response.sendRedirect(frontendUrl + "/?authError=google")));
        }
        return http.build();
    }
}
