package com.khedma.group;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class GroupDtos {

    private GroupDtos() {}

    public record GroupRequest(
            @NotBlank(message = "اكتب اسم الجروب") @Size(min = 2, max = 60, message = "الاسم من 2 إلى 60 حرف") String name) {}

    public record GroupResponse(Long id, String name, int pointsBalance, long memberCount) {}
}
