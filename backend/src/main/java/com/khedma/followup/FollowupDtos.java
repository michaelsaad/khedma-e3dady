package com.khedma.followup;

import java.time.Instant;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class FollowupDtos {

    private FollowupDtos() {}

    public record FollowupRequest(
            @NotNull Long studentId,
            @NotNull Long typeId,
            @Size(max = 500) String note) {}

    /** {@code note} is optional context for whoever handles it. */
    public record ResolveRequest(@Size(max = 500) String note) {}

    public record FollowupResponse(
            Long id,
            Long studentId,
            String studentName,
            Long typeId,
            String typeLabel,
            String note,
            FollowupStatus status,
            Long authorId,
            String authorName,
            Instant createdAt,
            String resolvedByName,
            String resolvedNote,
            Instant resolvedAt) {

        static FollowupResponse of(Followup f) {
            return new FollowupResponse(
                    f.getId(),
                    f.getStudent().getId(),
                    f.getStudent().getName(),
                    f.getType() == null ? null : f.getType().getId(),
                    f.getTypeLabel(),
                    f.getNote(),
                    f.getStatus(),
                    f.getAuthor() == null ? null : f.getAuthor().getId(),
                    f.getAuthor() == null ? null : f.getAuthor().getDisplayName(),
                    f.getCreatedAt(),
                    f.getResolvedBy() == null ? null : f.getResolvedBy().getDisplayName(),
                    f.getResolvedNote(),
                    f.getResolvedAt());
        }
    }

    public record TypeRequest(
            @NotBlank @Size(max = 100) String label,
            @Min(0) @Max(1000) Integer sortOrder,
            Boolean active) {}

    public record TypeResponse(Long id, String label, int sortOrder, boolean active) {
        static TypeResponse of(FollowupType t) {
            return new TypeResponse(t.getId(), t.getLabel(), t.getSortOrder(), t.isActive());
        }
    }
}
