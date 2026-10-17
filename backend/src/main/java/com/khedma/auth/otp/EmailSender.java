package com.khedma.auth.otp;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.khedma.common.ApiException;
import com.khedma.common.ErrorCode;
import com.khedma.config.AppProperties;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

/**
 * Sends one-time codes as a branded HTML email (with a plain-text part). In order of preference:
 * <ol>
 *   <li>Brevo's HTTPS API when {@code app.mail.brevo-api-key} is set (works on hosts that block SMTP ports),</li>
 *   <li>SMTP when {@code spring.mail.host} is configured,</li>
 *   <li>otherwise (local development) the code is written to the log.</li>
 * </ol>
 */
@Component
public class EmailSender {

    private static final Logger log = LoggerFactory.getLogger(EmailSender.class);
    private static final String SENDER_NAME = "خدمة إعدادي";

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
        // The code stays inside the message only: not in the subject or the inbox preview line.
        String subject = purpose.title() + " | خدمة إعدادي";
        String link = link(to, code, purpose);
        String text = text(name, code, validFor, purpose, link);
        String html = html(name, code, validFor, purpose, link);

        if (properties.mail().brevoEnabled()) {
            sendWithBrevo(to, name, subject, text, html);
            return;
        }
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            log.warn("Email is not configured. {} code for {}: {} ({})", purpose, to, code, link);
            return;
        }
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(properties.mail().from(), SENDER_NAME);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(text, html);
            sender.send(message);
        } catch (MailException | MessagingException | UnsupportedEncodingException ex) {
            log.error("Sending code email to {} failed: {}", to, ex.getMessage());
            throw new ApiException(ErrorCode.EMAIL_SEND_FAILED);
        }
    }

    /**
     * Opens the app with the code already filled in. Everything is in the #fragment, which browsers never
     * send to a server, so the code doesn't end up in access logs or Referer headers.
     */
    String link(String to, String code, CodePurpose purpose) {
        String flow = switch (purpose) {
            case VERIFY_EMAIL -> "verify";
            case RESET_PASSWORD -> "reset";
            case LOGIN -> "login";
        };
        return properties.appUrl() + "/login#otp=" + code + "&flow=" + flow
                + "&email=" + URLEncoder.encode(to, StandardCharsets.UTF_8);
    }

    static String text(String name, String code, Duration validFor, CodePurpose purpose, String link) {
        return """
                أهلاً %s،

                %s

                الكود: %s

                أو افتح اللينك ده والكود هيتكتب لوحده:
                %s

                الكود صالح لمدة %d دقايق.

                خدمة إعدادي
                """.formatted(name, purpose.intro(), code, link, validFor.toMinutes());
    }

    String html(String name, String code, Duration validFor, CodePurpose purpose, String link) {
        return TEMPLATE
                .replace("{{appUrl}}", escape(properties.appUrl()))
                .replace("{{title}}", escape(purpose.title()))
                .replace("{{name}}", escape(name))
                .replace("{{intro}}", escape(purpose.intro()))
                .replace("{{code}}", escape(code))
                .replace("{{action}}", escape(purpose.action()))
                .replace("{{link}}", escape(link))
                .replace("{{minutes}}", String.valueOf(validFor.toMinutes()));
    }

    /** Display names are typed by users, so everything placed in the HTML is escaped. */
    static String escape(String value) {
        if (value == null) return "";
        StringBuilder out = new StringBuilder(value.length());
        for (char c : value.toCharArray()) {
            switch (c) {
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '&' -> out.append("&amp;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    private void sendWithBrevo(String to, String name, String subject, String text, String html) {
        Map<String, Object> body = Map.of(
                "sender", Map.of("email", properties.mail().from(), "name", SENDER_NAME),
                "to", List.of(Map.of("email", to, "name", name)),
                "subject", subject,
                "textContent", text,
                "htmlContent", html);
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

    // Table layout + inline styles: what Gmail, Outlook and Apple Mail all render the same way.
    private static final String TEMPLATE = """
            <!doctype html>
            <html lang="ar" dir="rtl">
            <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <meta name="color-scheme" content="light only">
            <title>{{title}}</title>
            </head>
            <body style="margin:0;padding:0;background:#f6f1e4;">
            <div style="display:none;max-height:0;overflow:hidden;opacity:0;">{{intro}}</div>
            <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background:#f6f1e4;">
            <tr><td align="center" style="padding:32px 14px;">
            <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="max-width:480px;background:#ffffff;border-radius:20px;overflow:hidden;font-family:'IBM Plex Sans Arabic',Tahoma,Arial,sans-serif;direction:rtl;text-align:right;">
            <tr><td style="background:#8c650f;padding:28px 24px;text-align:center;">
            <img src="{{appUrl}}/logo-128.png" width="68" height="68" alt="" style="display:block;margin:0 auto 10px;border:0;">
            <div style="color:#ffffff;font-size:21px;font-weight:700;">خدمة إعدادي</div>
            <div style="color:#f3e6c4;font-size:13px;margin-top:4px;">فصل القديس الأنبا موسى القوي</div>
            </td></tr>
            <tr><td style="padding:28px 28px 6px;color:#292524;font-size:16px;line-height:1.8;">
            <p style="margin:0 0 4px;font-size:18px;font-weight:700;color:#6b4c0a;">{{title}}</p>
            <p style="margin:0 0 8px;font-weight:700;">أهلاً {{name}}،</p>
            <p style="margin:0;color:#57534e;font-size:15px;">{{intro}}</p>
            </td></tr>
            <tr><td align="center" style="padding:20px 20px 8px;">
            <div dir="ltr" style="display:inline-block;background:#fbf6e9;border:2px dashed #d9bf7f;border-radius:16px;padding:14px 22px 14px 32px;font-family:'SFMono-Regular',Consolas,'Courier New',monospace;font-size:36px;font-weight:700;letter-spacing:10px;color:#6b4c0a;">{{code}}</div>
            <div style="color:#78716c;font-size:13px;margin-top:10px;">الكود صالح لمدة {{minutes}} دقايق</div>
            </td></tr>
            <tr><td align="center" style="padding:14px 28px 28px;">
            <a href="{{link}}" style="display:inline-block;background:#8c650f;color:#ffffff;text-decoration:none;font-size:16px;font-weight:700;padding:14px 34px;border-radius:12px;">{{action}}</a>
            <div style="color:#a8a29e;font-size:12px;margin-top:10px;">الزرار بيفتح الموقع والكود مكتوب لوحده</div>
            </td></tr>
            </table>
            </td></tr>
            </table>
            </body>
            </html>
            """;
}
