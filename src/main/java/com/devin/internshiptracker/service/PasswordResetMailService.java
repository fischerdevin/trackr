package com.devin.internshiptracker.service;

import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
@EnableAsync
public class PasswordResetMailService {
    public record ResetEmail(String to, String link) {}
    public record PasswordChangedEmail(String to) {}
    public record EmailChangedEmail(String previousEmail, String newEmail) {}
    private final ObjectProvider<JavaMailSender> senders;
    private final String from;

    public PasswordResetMailService(ObjectProvider<JavaMailSender> senders,
            @Value("${app.mail.from:}") String from) {
        this.senders = senders;
        this.from = from;
    }

    public boolean isConfigured() { return senders.getIfAvailable() != null && !from.isBlank(); }

    @Async
    @TransactionalEventListener
    public void sendReset(ResetEmail event) {
        send(event.to(), "Reset your Trackr password", "Use this link to choose a new Trackr password:\n\n"
                + event.link() + "\n\nThis link expires in 30 minutes and can only be used once.\n"
                + "If you did not request this reset, you can ignore this email. Your password has not changed.");
    }

    @Async
    @TransactionalEventListener
    public void sendConfirmation(PasswordChangedEmail event) {
        send(event.to(), "Your Trackr password was changed",
                "Your Trackr password has been changed and existing sessions have been signed out.\n"
                + "If you did not make this change, request a new password reset immediately.");
    }

    @Async
    @TransactionalEventListener
    public void sendEmailChangeConfirmation(EmailChangedEmail event) {
        String message = "Your Trackr sign-in email has been changed and existing sessions have been signed out.\n"
                + "If you did not make this change, contact the app administrator immediately.";
        send(event.previousEmail(), "Your Trackr email was changed", message);
        send(event.newEmail(), "Your Trackr sign-in email", "This is now your Trackr sign-in email.\n" + message);
    }

    private void send(String to, String subject, String body) {
        if (!isConfigured()) return;
        var message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        try {
            senders.getObject().send(message);
        } catch (org.springframework.mail.MailException exception) {
            // Do not log recipients, reset links, or provider errors containing message content.
            LoggerFactory.getLogger(getClass()).warn("Password recovery email could not be sent. Check SMTP configuration.");
        }
    }
}
