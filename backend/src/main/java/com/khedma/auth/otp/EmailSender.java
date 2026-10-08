package com.khedma.auth.otp;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import com.khedma.config.AppProperties;

/**
 * Sends verification codes over SMTP when {@code spring.mail.host} is configured.
 * Without SMTP (local development) the code is written to the log instead.
 */
@Component
public class EmailSender {

    private static final Logger log = LoggerFactory.getLogger(EmailSender.class);

    private final ObjectProvider<JavaMailSender> mailSender;
    private final AppProperties properties;

    public EmailSender(ObjectProvider<JavaMailSender> mailSender, AppProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    public void sendVerificationCode(String to, String name, String code, Duration validFor) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            log.warn("SMTP is not configured. Verification code for {}: {}", to, code);
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.mail().from());
        message.setTo(to);
        message.setSubject("Your Khedma verification code: " + code);
        message.setText("""
                Hi %s,

                Your verification code is: %s

                It expires in %d minutes. If you did not create an account, you can ignore this email.
                """.formatted(name, code, validFor.toMinutes()));
        sender.send(message);
    }
}
