package com.khedma.attendance;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.khedma.attendance.AttendanceDtos.AttendanceEntry;
import com.khedma.attendance.AttendanceDtos.MeetingAttendance;
import com.khedma.attendance.AttendanceDtos.MeetingsResponse;
import com.khedma.attendance.AttendanceDtos.SaveAttendanceRequest;
import com.khedma.attendance.AttendanceDtos.SaveAttendanceResult;
import com.khedma.attendance.AttendanceDtos.SkipMeetingRequest;
import com.khedma.attendance.AttendanceDtos.UpdateStudentAttendanceRequest;
import com.khedma.auth.AuthUser;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/attendance")
public class AttendanceController {

    private final AttendanceService attendanceService;

    public AttendanceController(AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    /** Past meeting dates in the current season, for the date picker. */
    @GetMapping("/meetings")
    public MeetingsResponse meetings() {
        return attendanceService.meetings();
    }

    @GetMapping("/{date}")
    public MeetingAttendance forMeeting(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return attendanceService.forMeeting(date);
    }

    /** Batch save; also awards / reverses attendance points automatically. */
    @PutMapping("/{date}")
    public SaveAttendanceResult save(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                     @Valid @RequestBody SaveAttendanceRequest request,
                                     @AuthenticationPrincipal AuthUser actor) {
        return attendanceService.save(date, request.entries(), actor.id());
    }

    /** Saves one student immediately (same point rules as the batch save). */
    @PutMapping("/{date}/students/{studentId}")
    public SaveAttendanceResult saveOne(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                        @PathVariable Long studentId,
                                        @Valid @RequestBody UpdateStudentAttendanceRequest request,
                                        @AuthenticationPrincipal AuthUser actor) {
        return attendanceService.save(date,
                List.of(new AttendanceEntry(studentId, request.service(), request.mass())), actor.id());
    }

    /** Marks the meeting as not held: reverses its attendance points and leaves it out of statistics. */
    @PutMapping("/{date}/skip")
    public SaveAttendanceResult skip(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                     @Valid @RequestBody SkipMeetingRequest request,
                                     @AuthenticationPrincipal AuthUser actor) {
        return attendanceService.skip(date, request.reason(), actor.id());
    }

    /** Restores a skipped meeting and re-awards its attendance points. */
    @DeleteMapping("/{date}/skip")
    public SaveAttendanceResult unskip(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                       @AuthenticationPrincipal AuthUser actor) {
        return attendanceService.unskip(date, actor.id());
    }
}
