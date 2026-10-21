package com.devin.internshiptracker.repository;

import com.devin.internshiptracker.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByEmail(String email);
    Optional<AppUser> findByGoogleSubject(String subject);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select u from AppUser u where u.id = :id")
    Optional<AppUser> findForSettingsById(Long id);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select u from AppUser u where u.email = :email")
    Optional<AppUser> findForPasswordResetByEmail(String email);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select u from AppUser u where u.resetTokenHash = :hash")
    Optional<AppUser> findForPasswordResetByTokenHash(String hash);
}
