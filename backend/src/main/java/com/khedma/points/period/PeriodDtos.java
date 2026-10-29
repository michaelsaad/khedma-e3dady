package com.khedma.points.period;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class PeriodDtos {

    private PeriodDtos() {}

    public record CreatePeriodRequest(@NotBlank @Size(max = 100) String name, @NotNull LocalDate startsOn) {}

    public record RenamePeriodRequest(@NotBlank @Size(max = 100) String name) {}

    /**
     * {@code endsOn} is the last day (inclusive), null while the period is still open.
     * {@code upcoming} marks a period whose start date hasn't come yet.
     */
    public record PeriodResponse(Long id, String name, LocalDate startsOn, LocalDate endsOn, boolean current,
                                 boolean upcoming) {}
}
