package com.khedma.push;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.khedma.auth.AuthUser;
import com.khedma.config.AppProperties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** A device turning its notifications on or off. */
@RestController
@RequestMapping("/api/push")
public class PushController {

    /** {@code publicKey} is what the browser needs as applicationServerKey; null while push is off. */
    public record ConfigResponse(boolean enabled, String publicKey) {}

    /** The shape of {@code PushSubscription.toJSON()} in the browser. */
    public record SubscribeRequest(@NotBlank @Size(max = 1000) String endpoint, @NotNull @Valid Keys keys) {}

    public record Keys(@NotBlank @Size(max = 200) String p256dh, @NotBlank @Size(max = 100) String auth) {}

    private final PushService pushService;
    private final AppProperties properties;

    public PushController(PushService pushService, AppProperties properties) {
        this.pushService = pushService;
        this.properties = properties;
    }

    /** Public: the page needs it to decide whether to offer the toggle at all. */
    @GetMapping("/config")
    public ConfigResponse config() {
        boolean enabled = pushService.enabled();
        return new ConfigResponse(enabled, enabled ? properties.push().publicKey().trim() : null);
    }

    @PostMapping("/subscribe")
    @ResponseStatus(HttpStatus.CREATED)
    public void subscribe(@Valid @RequestBody SubscribeRequest request, @AuthenticationPrincipal AuthUser user,
                          @RequestHeader(value = "User-Agent", required = false) String userAgent) {
        pushService.subscribe(user.id(), request.endpoint(), request.keys().p256dh(), request.keys().auth(), userAgent);
    }

    @DeleteMapping("/subscribe")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsubscribe(@RequestParam String endpoint, @AuthenticationPrincipal AuthUser user) {
        pushService.unsubscribe(user.id(), endpoint);
    }
}
