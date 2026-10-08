package com.khedma.settings;

import java.time.Clock;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.khedma.common.ApiException;
import com.khedma.settings.SettingsDtos.SettingsResponse;
import com.khedma.settings.SettingsDtos.UpdateSettingsRequest;
import com.khedma.user.UserRepository;

@Service
public class SettingsService {

    private final AppSettingsRepository repository;
    private final UserRepository users;
    private final Clock clock;

    public SettingsService(AppSettingsRepository repository, UserRepository users, Clock clock) {
        this.repository = repository;
        this.users = users;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AppSettings current() {
        return repository.findById(AppSettings.SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException("app_settings row is missing"));
    }

    @Transactional(readOnly = true)
    public SettingsResponse get() {
        return SettingsResponse.of(current());
    }

    /**
     * Point values only apply to attendance marked from now on. Existing awards keep their
     * original amount, and un-marking reverses exactly what was awarded.
     */
    @Transactional
    public SettingsResponse update(UpdateSettingsRequest request, Long actorId) {
        if (!request.seasonEnd().isAfter(request.seasonStart())) {
            throw ApiException.badRequest("نهاية السنة لازم تكون بعد بدايتها");
        }
        AppSettings settings = current();
        settings.setMassPoints(request.massPoints());
        settings.setServicePoints(request.servicePoints());
        settings.setSeasonStart(request.seasonStart());
        settings.setSeasonEnd(request.seasonEnd());
        settings.setMeetingDay(request.meetingDay());
        settings.setUpdatedBy(users.getReferenceById(actorId));
        settings.setUpdatedAt(clock.instant());
        return SettingsResponse.of(settings);
    }
}
