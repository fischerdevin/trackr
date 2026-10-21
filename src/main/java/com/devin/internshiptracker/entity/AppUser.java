package com.devin.internshiptracker.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "app_users")
public class AppUser {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true)
    private String email;
    @Column(nullable = false)
    private String name;
    private String passwordHash;
    @Column(unique = true)
    private String googleSubject;
    @Column(nullable = false, columnDefinition = "boolean default true")
    private boolean passwordResetAllowed = true;
    @Column(unique = true, length = 64)
    private String resetTokenHash;
    private java.time.Instant resetTokenExpiresAt;
    private java.time.Instant resetRequestedAt;

    protected AppUser() {}
    public AppUser(String email, String name, String passwordHash, String googleSubject) {
        this.email = email;
        this.name = name;
        this.passwordHash = passwordHash;
        this.googleSubject = googleSubject;
    }
    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getName() { return name; }
    public void updateName(String name) { this.name = name; }
    public void updateEmail(String email) {
        this.email = email;
        resetTokenHash = null;
        resetTokenExpiresAt = null;
        resetRequestedAt = null;
    }
    public String getPasswordHash() { return passwordHash; }
    public String getGoogleSubject() { return googleSubject; }
    public boolean isPasswordResetAllowed() { return passwordResetAllowed; }
    public void disablePasswordReset() {
        passwordResetAllowed = false;
        resetTokenHash = null;
        resetTokenExpiresAt = null;
    }
    public String getResetTokenHash() { return resetTokenHash; }
    public java.time.Instant getResetTokenExpiresAt() { return resetTokenExpiresAt; }
    public java.time.Instant getResetRequestedAt() { return resetRequestedAt; }
    public void issuePasswordReset(String tokenHash, java.time.Instant expiresAt, java.time.Instant requestedAt) {
        resetTokenHash = tokenHash;
        resetTokenExpiresAt = expiresAt;
        resetRequestedAt = requestedAt;
    }
    public void resetPassword(String encodedPassword) {
        passwordHash = encodedPassword;
        resetTokenHash = null;
        resetTokenExpiresAt = null;
    }
}
