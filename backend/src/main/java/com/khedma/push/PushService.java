package com.khedma.push;

import java.net.URI;
import java.time.Clock;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.khedma.common.ApiException;
import com.khedma.common.ErrorCode;
import com.khedma.push.PushGateway.Outcome;
import com.khedma.push.PushGateway.Target;
import com.khedma.user.AppUser;
import com.khedma.user.UserRepository;
import com.khedma.user.UserStatus;

/**
 * Keeps each account's subscribed devices and sends notifications to them.
 * Sending is fire-and-forget on {@link PushExecutor}: a failing push service can never fail or slow the request
 * (or roll back the points) that triggered it.
 */
@Service
public class PushService {

    private static final Logger log = LoggerFactory.getLogger(PushService.class);
    private static final int MAX_BODY = 300;

    /** What the service worker shows. {@code url} is where tapping it opens; {@code tag} replaces an earlier one. */
    public record Message(String title, String body, String url, String tag) {}

    private final PushGateway gateway;
    private final PushSubscriptionRepository subscriptions;
    private final UserRepository users;
    private final PushExecutor executor;
    private final Clock clock;
    private final ObjectMapper json;

    public PushService(PushGateway gateway, PushSubscriptionRepository subscriptions, UserRepository users,
                       PushExecutor executor, Clock clock, ObjectMapper json) {
        this.gateway = gateway;
        this.subscriptions = subscriptions;
        this.users = users;
        this.executor = executor;
        this.clock = clock;
        this.json = json;
    }

    public boolean enabled() {
        return gateway.enabled();
    }

    // ---- Subscriptions ------------------------------------------------------------------

    /**
     * Saves a device for the account. A device is identified by its endpoint, so subscribing again simply
     * moves it to whoever is signed in now: a shared phone never keeps the previous person's notifications.
     */
    @Transactional
    public void subscribe(Long userId, String endpoint, String p256dh, String auth, String userAgent) {
        requireAllowedEndpoint(endpoint);
        requireValidKeys(p256dh, auth);
        PushSubscription subscription = subscriptions.findByEndpoint(endpoint).orElseGet(PushSubscription::new);
        subscription.setUser(users.getReferenceById(userId));
        subscription.setEndpoint(endpoint);
        subscription.setP256dh(p256dh);
        subscription.setAuth(auth);
        subscription.setUserAgent(userAgent == null ? "" : userAgent.substring(0, Math.min(userAgent.length(), 300)));
        subscriptions.save(subscription);
    }

    /** Only removes the device if it belongs to this account. */
    @Transactional
    public void unsubscribe(Long userId, String endpoint) {
        subscriptions.deleteByEndpointAndUserId(endpoint, userId);
    }

    @Transactional(readOnly = true)
    public int countDevices(Collection<Long> userIds) {
        return userIds.isEmpty() ? 0 : (int) subscriptions.countByUserIdIn(userIds);
    }

    /** Ids of approved accounts that belong to an audience. */
    @Transactional(readOnly = true)
    public List<Long> accountIds(Audience audience) {
        return users.findAllByStatusAndRoleIn(UserStatus.APPROVED, audience.roles()).stream().map(AppUser::getId).toList();
    }

    // ---- Sending ----------------------------------------------------------------------

    /** Sends to every device of these accounts, in the background. */
    public void sendToUsers(Collection<Long> userIds, Message message) {
        if (userIds.isEmpty()) return;
        List<Long> ids = List.copyOf(userIds);
        executor.execute(() -> deliver(ids, message));
    }

    /** Sends to the approved account linked to this student, if there is one. */
    public void sendToStudentAccount(Long studentId, Message message) {
        executor.execute(() -> users.findByStudentId(studentId)
                .filter(AppUser::isApproved)
                .ifPresent(account -> deliver(List.of(account.getId()), message)));
    }

    private void deliver(List<Long> userIds, Message message) {
        try {
            String payload = payload(message);
            for (PushSubscription subscription : subscriptions.findAllByUserIdIn(userIds)) {
                Outcome outcome = gateway.send(
                        new Target(subscription.getEndpoint(), subscription.getP256dh(), subscription.getAuth()), payload);
                switch (outcome) {
                    case DELIVERED -> subscriptions.markDelivered(subscription.getId(), clock.instant());
                    // The browser says the device is gone (app uninstalled, permission revoked): stop trying.
                    case GONE -> subscriptions.deleteByEndpoint(subscription.getEndpoint());
                    case FAILED -> { /* already logged by the gateway; the next message tries again */ }
                }
            }
        } catch (RuntimeException e) {
            log.warn("Push delivery failed: {}", e.toString());
        }
    }

    private String payload(Message message) {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("title", message.title());
        data.put("body", message.body().length() > MAX_BODY ? message.body().substring(0, MAX_BODY) : message.body());
        if (message.url() != null) data.put("url", message.url());
        if (message.tag() != null) data.put("tag", message.tag());
        try {
            return json.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    // ---- Validation -------------------------------------------------------------------

    /**
     * The server POSTs to whatever endpoint a signed-in user hands over, so only the real push services of
     * the major browsers are accepted (otherwise this would let a user make the server call any URL).
     */
    static void requireAllowedEndpoint(String endpoint) {
        try {
            URI uri = URI.create(endpoint);
            String host = uri.getHost();
            boolean knownService = host != null && (host.equals("fcm.googleapis.com")
                    || host.endsWith(".push.services.mozilla.com")
                    || host.endsWith(".push.apple.com")
                    || host.endsWith(".notify.windows.com"));
            if ("https".equals(uri.getScheme()) && knownService && uri.getUserInfo() == null
                    && (uri.getPort() == -1 || uri.getPort() == 443)) {
                return;
            }
        } catch (IllegalArgumentException ignored) {
            // falls through to the error below
        }
        throw new ApiException(ErrorCode.PUSH_ENDPOINT_NOT_ALLOWED);
    }

    private static void requireValidKeys(String p256dh, String auth) {
        try {
            if (EcKeys.decodeBase64Url(p256dh).length != EcKeys.PUBLIC_KEY_LENGTH || EcKeys.decodeBase64Url(auth).length < 16) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED);
            }
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
    }
}
