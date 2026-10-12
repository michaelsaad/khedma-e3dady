package com.khedma.auth.otp;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.hamcrest.Matchers.containsString;

import java.time.Duration;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.khedma.common.ApiException;
import com.khedma.common.ErrorCode;
import com.khedma.config.AppProperties;

class EmailSenderTest {

    private final RestClient.Builder http = RestClient.builder();
    private final MockRestServiceServer brevo = MockRestServiceServer.bindTo(http).build();

    private EmailSender sender(String apiKey) {
        AppProperties properties = new AppProperties(
                ZoneId.of("Africa/Cairo"), null, null, null,
                new AppProperties.Mail("sender@test.local", apiKey, "https://brevo.test"),
                List.of(), null);
        return new EmailSender(new StaticListableBeanFactory().getBeanProvider(JavaMailSender.class), properties, http);
    }

    @Test
    void sendsThroughBrevoApiWhenKeyIsSet() {
        brevo.expect(requestTo("https://brevo.test/v3/smtp/email"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("api-key", "test-key"))
                .andExpect(jsonPath("$.sender.email").value("sender@test.local"))
                .andExpect(jsonPath("$.to[0].email").value("user@test.local"))
                .andExpect(jsonPath("$.textContent").value(containsString("123456")))
                .andRespond(withStatus(HttpStatus.CREATED));

        sender("test-key").sendCode("user@test.local", "User", "123456", Duration.ofMinutes(10), CodePurpose.VERIFY_EMAIL);
        brevo.verify();
    }

    @Test
    void brevoFailureBecomesEmailSendFailed() {
        brevo.expect(requestTo("https://brevo.test/v3/smtp/email")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> sender("bad-key").sendCode("user@test.local", "User", "1", Duration.ofMinutes(10), CodePurpose.VERIFY_EMAIL))
                .isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo(ErrorCode.EMAIL_SEND_FAILED);
    }

    @Test
    void withoutBrevoOrSmtpTheCodeIsOnlyLogged() {
        sender("").sendCode("user@test.local", "User", "123456", Duration.ofMinutes(10), CodePurpose.VERIFY_EMAIL);
        brevo.verify(); // no HTTP call expected
    }
}
