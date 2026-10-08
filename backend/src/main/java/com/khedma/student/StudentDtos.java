package com.khedma.student;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class StudentDtos {

    private StudentDtos() {}

    public record StudentRequest(
            @NotBlank(message = "اكتب اسم المخدوم") @Size(min = 2, max = 100, message = "الاسم من 2 إلى 100 حرف") String name,
            @NotNull(message = "اختر الصف") Grade grade,
            @Size(max = 30, message = "رقم التليفون طويل جدًا") String phone,
            /** null = no group. */
            Long groupId) {}

    public record StudentResponse(
            Long id,
            String name,
            Grade grade,
            String gradeName,
            String phone,
            Long groupId,
            String groupName,
            int pointsBalance,
            Instant createdAt) {

        public static StudentResponse of(Student s) {
            return new StudentResponse(
                    s.getId(),
                    s.getName(),
                    s.getGrade(),
                    s.getGrade().arabicName(),
                    s.getPhone(),
                    s.getGroup() == null ? null : s.getGroup().getId(),
                    s.getGroup() == null ? null : s.getGroup().getName(),
                    s.getPointsBalance(),
                    s.getCreatedAt());
        }
    }
}
