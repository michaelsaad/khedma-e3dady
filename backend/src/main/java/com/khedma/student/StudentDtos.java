package com.khedma.student;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class StudentDtos {

    private StudentDtos() {}

    public record StudentRequest(
            @NotBlank @Size(min = 2, max = 100) String name,
            @NotNull Grade grade,
            @Size(max = 30) String phone,
            /** null = no group. */
            Long groupId) {}

    public record StudentResponse(
            Long id,
            String name,
            Grade grade,
            String phone,
            Long groupId,
            String groupName,
            /** All-time total. */
            int pointsBalance,
            /** Points in the current period. */
            int periodPoints,
            Instant createdAt) {

        public static StudentResponse of(Student s, int periodPoints) {
            return new StudentResponse(
                    s.getId(),
                    s.getName(),
                    s.getGrade(),
                    s.getPhone(),
                    s.getGroup() == null ? null : s.getGroup().getId(),
                    s.getGroup() == null ? null : s.getGroup().getName(),
                    s.getPointsBalance(),
                    periodPoints,
                    s.getCreatedAt());
        }
    }
}
