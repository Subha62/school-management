package com.schoolmanagement.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/** Sends transactional email. In development, messages are captured by Mailpit. */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final String from;

    public EmailService(JavaMailSender mailSender, @Value("${app.mail.from}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    /**
     * Runs asynchronously so response time does not depend on mail delivery. Failures are logged without
     * the recipient or the link, because the link contains a secret token.
     */
    @Async
    public void sendPasswordReset(String to, String firstName, String resetLink, long validForMinutes) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject("Reset your School Management password");
        message.setText("Hello " + firstName + ",\n\n"
                + "We received a request to reset your password. Use the link below within "
                + validForMinutes + " minutes:\n\n" + resetLink + "\n\n"
                + "If you did not request this, you can ignore this email; your password will not change.\n");
        try {
            mailSender.send(message);
        } catch (MailException e) {
            log.warn("Password reset email could not be delivered ({})", e.getClass().getSimpleName());
        }
    }
}
