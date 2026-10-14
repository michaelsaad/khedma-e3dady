package com.khedma.push;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.khedma.common.ApiException;
import com.khedma.common.ErrorCode;
import com.khedma.push.PushService.Message;
import com.khedma.user.UserRepository;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** A super admin writes a message and sends it to everyone, or to the admins only. */
@Service
public class BroadcastService {

    public record BroadcastRequest(
            @NotBlank @Size(max = 100) String title,
            @NotBlank @Size(max = 300) String body,
            @NotNull Audience audience) {}

    /** How many accounts are in an audience and how many devices of theirs will actually get the message. */
    public record RecipientsResponse(int accounts, int devices) {}

    public record BroadcastResponse(Long id, String title, String body, Audience audience, String sentBy, int devices,
                                    Instant createdAt) {
        static BroadcastResponse of(Broadcast b) {
            return new BroadcastResponse(b.getId(), b.getTitle(), b.getBody(), b.getAudience(),
                    b.getSentBy() == null ? null : b.getSentBy().getDisplayName(), b.getDevices(), b.getCreatedAt());
        }
    }

    private final PushService pushService;
    private final BroadcastRepository broadcasts;
    private final UserRepository users;
    private final Clock clock;

    public BroadcastService(PushService pushService, BroadcastRepository broadcasts, UserRepository users, Clock clock) {
        this.pushService = pushService;
        this.broadcasts = broadcasts;
        this.users = users;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public RecipientsResponse recipients(Audience audience) {
        List<Long> ids = pushService.accountIds(audience);
        return new RecipientsResponse(ids.size(), pushService.countDevices(ids));
    }

    @Transactional
    public BroadcastResponse send(BroadcastRequest request, Long actorId) {
        if (!pushService.enabled()) throw new ApiException(ErrorCode.PUSH_NOT_CONFIGURED);
        List<Long> ids = pushService.accountIds(request.audience());

        Broadcast broadcast = new Broadcast();
        broadcast.setTitle(request.title().trim());
        broadcast.setBody(request.body().trim());
        broadcast.setAudience(request.audience());
        broadcast.setSentBy(users.getReferenceById(actorId));
        broadcast.setDevices(pushService.countDevices(ids));
        broadcast.setCreatedAt(clock.instant());
        broadcasts.save(broadcast);

        pushService.sendToUsers(ids, new Message(broadcast.getTitle(), broadcast.getBody(), "/", "broadcast-" + broadcast.getId()));
        return BroadcastResponse.of(broadcast);
    }

    @Transactional(readOnly = true)
    public List<BroadcastResponse> recent() {
        return broadcasts.findTop20ByOrderByIdDesc().stream().map(BroadcastResponse::of).toList();
    }
}
