package com.khedma.attendance;

import java.time.LocalDate;
import java.util.List;

import com.khedma.student.Grade;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class AttendanceDtos {

    private AttendanceDtos() {}

    public record AttendanceEntry(
            @NotNull Long studentId,
            @NotNull Boolean service,
            @NotNull Boolean mass) {}

    public record SaveAttendanceRequest(
            @NotEmpty @Size(max = 500)
            List<@Valid @NotNull AttendanceEntry> entries) {}

    /** One row per active student for the requested meeting (absent if never recorded). */
    public record AttendanceRow(
            Long studentId,
            String studentName,
            Grade grade,
            Long groupId,
            boolean service,
            boolean mass,
            boolean recorded) {}

    /** previous/next are the neighbouring valid meetings (null at the edges) for arrow navigation. */
    public record MeetingAttendance(
            LocalDate date,
            LocalDate previousMeeting,
            LocalDate nextMeeting,
            int serviceCount,
            int massCount,
            List<AttendanceRow> rows) {}

    /** Instant single-student update (e.g. tapping a toggle). */
    public record UpdateStudentAttendanceRequest(
            @NotNull Boolean service,
            @NotNull Boolean mass) {}

    public record SaveAttendanceResult(
            LocalDate date,
            int saved,
            int awardsCreated,
            int reversalsCreated,
            int netPoints) {}

    public record MeetingsResponse(List<LocalDate> meetings, LocalDate today) {}
}
