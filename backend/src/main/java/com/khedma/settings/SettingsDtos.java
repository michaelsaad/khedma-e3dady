package com.khedma.settings;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public final class SettingsDtos {

    private SettingsDtos() {}

    public record SettingsResponse(
            int massPoints,
            int servicePoints,
            LocalDate seasonStart,
            LocalDate seasonEnd,
            DayOfWeek meetingDay,
            LocalDate pointsStartDate,
            String updatedBy,
            Instant updatedAt) {

        static SettingsResponse of(AppSettings settings) {
            return new SettingsResponse(
                    settings.getMassPoints(),
                    settings.getServicePoints(),
                    settings.getSeasonStart(),
                    settings.getSeasonEnd(),
                    settings.getMeetingDay(),
                    settings.getPointsStartDate(),
                    settings.getUpdatedBy() == null ? null : settings.getUpdatedBy().getDisplayName(),
                    settings.getUpdatedAt());
        }
    }

    public record UpdateSettingsRequest(
            @NotNull @Min(value = 0) @Max(value = 1000) Integer massPoints,
            @NotNull @Min(value = 0) @Max(value = 1000) Integer servicePoints,
            @NotNull LocalDate seasonStart,
            @NotNull LocalDate seasonEnd,
            @NotNull DayOfWeek meetingDay,
            @NotNull LocalDate pointsStartDate) {}
}
