package com.khedma.points;

import java.time.Instant;

import com.khedma.attendance.AttendanceKind;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class PointsDtos {

    private PointsDtos() {}

    public enum ManualKind { ADD, DEDUCT, RESET }

    /** {@code amount} is required for ADD/DEDUCT and ignored for RESET. */
    public record ManualPointsRequest(
            @NotNull(message = "اختر نوع العملية") ManualKind kind,
            @Min(value = 1, message = "النقاط لا تقل عن 1") @Max(value = 10000, message = "النقاط لا تزيد عن 10000") Integer amount,
            @NotBlank(message = "اكتب السبب") @Size(max = 200, message = "السبب لا يزيد عن 200 حرف") String notes) {}

    public record TransactionResponse(
            Long id,
            Long studentId,
            String studentName,
            Long groupId,
            String groupName,
            TransactionType type,
            int amount,
            int balanceAfter,
            String notes,
            Long attendanceId,
            AttendanceKind attendanceKind,
            Long reversesId,
            String createdBy,
            Instant createdAt) {

        public static TransactionResponse of(PointTransaction t) {
            return new TransactionResponse(
                    t.getId(),
                    t.getStudent() == null ? null : t.getStudent().getId(),
                    t.getStudent() == null ? null : t.getStudent().getName(),
                    t.getGroup() == null ? null : t.getGroup().getId(),
                    t.getGroup() == null ? null : t.getGroup().getName(),
                    t.getType(),
                    t.getAmount(),
                    t.getBalanceAfter(),
                    t.getNotes(),
                    t.getAttendance() == null ? null : t.getAttendance().getId(),
                    t.getAttendanceKind(),
                    t.getReverses() == null ? null : t.getReverses().getId(),
                    t.getCreatedBy() == null ? null : t.getCreatedBy().getDisplayName(),
                    t.getCreatedAt());
        }
    }
}
