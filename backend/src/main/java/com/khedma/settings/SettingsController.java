package com.khedma.settings;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.khedma.auth.AuthUser;
import com.khedma.settings.SettingsDtos.SettingsResponse;
import com.khedma.settings.SettingsDtos.UpdateSettingsRequest;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private final SettingsService settingsService;

    public SettingsController(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping
    public SettingsResponse get() {
        return settingsService.get();
    }

    @PutMapping
    public SettingsResponse update(@Valid @RequestBody UpdateSettingsRequest request,
                                   @AuthenticationPrincipal AuthUser actor) {
        return settingsService.update(request, actor.id());
    }
}
