package com.khedma.group;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class GroupDtos {

    private GroupDtos() {}

    public record GroupRequest(
            @NotBlank @Size(min = 2, max = 60) String name) {}

    /** {@code pointsBalance} is the all-time total; {@code periodPoints} the current period's. */
    public record GroupResponse(Long id, String name, int pointsBalance, int periodPoints, long memberCount) {}
}
