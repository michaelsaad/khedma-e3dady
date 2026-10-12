package com.khedma.auth.otp;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.khedma.common.ApiException;
import com.khedma.common.ErrorCode;
import com.khedma.config.AppProperties;

/**
 * Sends verification codes. In order of preference:
 * <ol>
 *   <li>Brevo's HTTPS API when {@code app.mail.brevo-api-key} is set (works on hosts that block SMTP ports),</li>
 *   <li>SMTP when {@code spring.mail.host} is configured,</li>
 *   <li>otherwise (local development) the code is written to the log.</li>
 * </ol>
 */
@Component
public class EmailSender {

    private static final Logger log = LoggerFactory.getLogger(EmailSender.class);
    private static final String SENDER_NAME = "Khedma E3dady";

    private final ObjectProvider<JavaMailSender> mailSender;
    private final AppProperties properties;
    private final RestClient brevo;

    @Autowired
    public EmailSender(ObjectProvider<JavaMailSender> mailSender, AppProperties properties) {
        this(mailSender, properties, RestClient.builder().requestFactory(timeouts()));
    }

    EmailSender(ObjectProvider<JavaMailSender> mailSender, AppProperties properties, RestClient.Builder http) {
        this.mailSender = mailSender;
        this.properties = properties;
        this.brevo = http.baseUrl(properties.mail().brevoBaseUrl()).build();
    }

    private static SimpleClientHttpRequestFactory timeouts() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(10));
        return factory;
    }

    public void sendCode(String to, String name, String code, Duration validFor, CodePurpose purpose) {
        String subject = purpose.subject() + ": " + code;
        String text = """
                Hi %s,

                %s: %s

                It expires in %d minutes. If you did not request this, you can ignore this email.
                """.formatted(name, purpose.intro(), code, validFor.toMinutes());

        if (properties.mail().brevoEnabled()) {
            sendWithBrevo(to, name, subject, text);
            return;
        }
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            log.warn("Email is not configured. {} code for {}: {}", purpose, to, code);
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.mail().from());
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        try {
            sender.send(message);
        } catch (MailException ex) {
            log.error("Sending code email to {} failed: {}", to, ex.getMessage());
            throw new ApiException(ErrorCode.EMAIL_SEND_FAILED);
        }
    }

    private void sendWithBrevo(String to, String name, String subject, String text) {
        Map<String, Object> body = Map.of(
                "sender", Map.of("email", properties.mail().from(), "name", SENDER_NAME),
                "to", List.of(Map.of("email", to, "name", name)),
                "subject", subject,
                "textContent", text);
        try {
            brevo.post()
                    .uri("/v3/smtp/email")
                    .header("api-key", properties.mail().brevoApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException ex) {
            log.error("Sending code email to {} via Brevo failed: {}", to, ex.getMessage());
            throw new ApiException(ErrorCode.EMAIL_SEND_FAILED);
        }
    }
}
