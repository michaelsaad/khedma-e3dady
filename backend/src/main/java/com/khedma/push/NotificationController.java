package com.khedma.push;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.khedma.auth.AuthUser;
import com.khedma.push.BroadcastService.BroadcastRequest;
import com.khedma.push.BroadcastService.BroadcastResponse;
import com.khedma.push.BroadcastService.RecipientsResponse;

import jakarta.validation.Valid;

/** Super admin only (see SecurityConfig). */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final BroadcastService broadcastService;

    public NotificationController(BroadcastService broadcastService) {
        this.broadcastService = broadcastService;
    }

    @GetMapping("/recipients")
    public RecipientsResponse recipients(@RequestParam Audience audience) {
        return broadcastService.recipients(audience);
    }

    @PostMapping("/broadcast")
    @ResponseStatus(HttpStatus.CREATED)
    public BroadcastResponse broadcast(@Valid @RequestBody BroadcastRequest request, @AuthenticationPrincipal AuthUser actor) {
        return broadcastService.send(request, actor.id());
    }

    @GetMapping("/broadcasts")
    public List<BroadcastResponse> broadcasts() {
        return broadcastService.recent();
    }
}
