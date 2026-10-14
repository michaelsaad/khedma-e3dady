package com.khedma.push;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Real sender: encrypts per RFC 8291, signs with VAPID and POSTs to the device's push service. */
public class WebPushGateway implements PushGateway {

    private static final Logger log = LoggerFactory.getLogger(WebPushGateway.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final VapidSigner signer;
    private final Clock clock;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            // Never follow redirects: a push service answering 3xx must not steer us to another host.
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public WebPushGateway(String publicKey, String privateKey, String subject, Clock clock) throws java.security.GeneralSecurityException {
        this.signer = new VapidSigner(publicKey, privateKey, subject);
        this.clock = clock;
    }

    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    public Outcome send(Target target, String payload) {
        try {
            byte[] body = WebPushCrypto.encrypt(payload.getBytes(StandardCharsets.UTF_8),
                    EcKeys.decodeBase64Url(target.p256dh()), EcKeys.decodeBase64Url(target.auth()));
            HttpRequest request = HttpRequest.newBuilder(URI.create(target.endpoint()))
                    .timeout(TIMEOUT)
                    .header("Authorization", signer.authorization(target.endpoint(), clock.instant()))
                    .header("Content-Encoding", "aes128gcm")
                    .header("Content-Type", "application/octet-stream")
                    // Keep it for a day if the device is offline, then drop it: stale reminders are noise.
                    .header("TTL", "86400")
                    .header("Urgency", "normal")
                    .POST(BodyPublishers.ofByteArray(body))
                    .build();
            HttpResponse<String> response = http.send(request, BodyHandlers.ofString());
            int status = response.statusCode();
            if (status >= 200 && status < 300) return Outcome.DELIVERED;
            if (status == 404 || status == 410) return Outcome.GONE;
            log.warn("Push service answered {} for {}: {}", status, host(target.endpoint()), response.body());
            return Outcome.FAILED;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Outcome.FAILED;
        } catch (IOException | java.security.GeneralSecurityException | RuntimeException e) {
            log.warn("Push to {} failed: {}", host(target.endpoint()), e.toString());
            return Outcome.FAILED;
        }
    }

    private static String host(String endpoint) {
        try {
            return URI.create(endpoint).getHost();
        } catch (RuntimeException e) {
            return "?";
        }
    }
}
