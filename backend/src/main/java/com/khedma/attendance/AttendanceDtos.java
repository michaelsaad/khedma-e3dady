package com.khedma.attendance;

import java.time.LocalDate;
import java.util.List;

import com.khedma.student.Grade;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class AttendanceDtos {

    private AttendanceDtos() {}

    public record AttendanceEntry(
            @NotNull Long studentId,
            @NotNull Boolean service,
            @NotNull Boolean mass) {}

    /** One row per active student for the requested meeting (absent if never recorded). */
    public record AttendanceRow(
            Long studentId,
            String studentName,
            Grade grade,
            Long groupId,
            boolean service,
            boolean mass,
            boolean recorded,
            /* Net manual points given on this meeting from the attendance page. */
            int extraPoints) {}

    /** previous/next are the neighbouring valid meetings (null at the edges) for arrow navigation. */
    public record MeetingAttendance(
            LocalDate date,
            LocalDate previousMeeting,
            LocalDate nextMeeting,
            int serviceCount,
            int massCount,
            boolean skipped,
            String skipReason,
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

    /** {@code skipped} lists the meetings marked as not held. */
    public record MeetingsResponse(List<LocalDate> meetings, LocalDate today, List<LocalDate> skipped) {}

    public record SkipMeetingRequest(@Size(max = 200) String reason) {}
}
