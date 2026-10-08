package com.khedma.attendance;

import java.text.Collator;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.khedma.attendance.AttendanceDtos.AttendanceEntry;
import com.khedma.attendance.AttendanceDtos.AttendanceRow;
import com.khedma.attendance.AttendanceDtos.MeetingAttendance;
import com.khedma.attendance.AttendanceDtos.MeetingsResponse;
import com.khedma.attendance.AttendanceDtos.SaveAttendanceResult;
import com.khedma.common.ApiException;
import com.khedma.common.ErrorCode;
import com.khedma.points.PointsService;
import com.khedma.settings.AppSettings;
import com.khedma.settings.SettingsService;
import com.khedma.student.Student;
import com.khedma.student.StudentRepository;
import com.khedma.user.UserRepository;

@Service
public class AttendanceService {

    static final Comparator<Student> BY_GRADE_THEN_NAME = Comparator
            .comparing(Student::getGrade)
            .thenComparing(Student::getName, Collator.getInstance(Locale.forLanguageTag("ar")));

    private final AttendanceRepository attendanceRepository;
    private final StudentRepository students;
    private final UserRepository users;
    private final PointsService pointsService;
    private final SettingsService settingsService;
    private final MeetingCalendar calendar;
    private final Clock clock;

    public AttendanceService(AttendanceRepository attendanceRepository, StudentRepository students,
                             UserRepository users, PointsService pointsService, SettingsService settingsService,
                             MeetingCalendar calendar, Clock clock) {
        this.attendanceRepository = attendanceRepository;
        this.students = students;
        this.users = users;
        this.pointsService = pointsService;
        this.settingsService = settingsService;
        this.calendar = calendar;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public MeetingsResponse meetings() {
        return new MeetingsResponse(calendar.pastMeetings(settingsService.current()), calendar.today());
    }

    @Transactional(readOnly = true)
    public MeetingAttendance forMeeting(LocalDate date) {
        AppSettings settings = settingsService.current();
        calendar.requireValidMeeting(date, settings);
        Map<Long, Attendance> byStudent = attendanceRepository.findAllByMeetingDate(date).stream()
                .collect(Collectors.toMap(a -> a.getStudent().getId(), Function.identity()));
        List<AttendanceRow> rows = students.findAllByActiveTrue().stream()
                .sorted(BY_GRADE_THEN_NAME)
                .map(student -> {
                    Attendance a = byStudent.get(student.getId());
                    return new AttendanceRow(
                            student.getId(),
                            student.getName(),
                            student.getGrade(),
                            student.getGroup() == null ? null : student.getGroup().getId(),
                            a != null && a.isService(),
                            a != null && a.isMass(),
                            a != null);
                })
                .toList();
        List<LocalDate> meetings = calendar.pastMeetings(settings);
        int index = meetings.indexOf(date);
        return new MeetingAttendance(
                date,
                index > 0 ? meetings.get(index - 1) : null,
                index >= 0 && index < meetings.size() - 1 ? meetings.get(index + 1) : null,
                (int) rows.stream().filter(AttendanceRow::service).count(),
                (int) rows.stream().filter(AttendanceRow::mass).count(),
                rows);
    }

    /**
     * Saves attendance for one meeting and, in the same transaction, distributes points:
     * newly marked service/mass -> award; un-marked -> reverse the earlier award.
     */
    @Transactional
    public SaveAttendanceResult save(LocalDate date, List<AttendanceEntry> entries, Long actorId) {
        AppSettings settings = settingsService.current();
        calendar.requireValidMeeting(date, settings);
        if (new HashSet<>(entries.stream().map(AttendanceEntry::studentId).toList()).size() != entries.size()) {
            throw new ApiException(ErrorCode.DUPLICATE_ATTENDANCE_ENTRY);
        }

        // Lock students in id order so concurrent saves can't deadlock.
        List<AttendanceEntry> ordered = entries.stream()
                .sorted(Comparator.comparing(AttendanceEntry::studentId))
                .toList();

        int awards = 0;
        int reversals = 0;
        int net = 0;
        for (AttendanceEntry entry : ordered) {
            Student student = students.findByIdForUpdate(entry.studentId())
                    .filter(Student::isActive)
                    .orElseThrow(() -> new ApiException(ErrorCode.STUDENT_NOT_FOUND));

            Attendance attendance = attendanceRepository.findByStudentIdAndMeetingDate(student.getId(), date)
                    .orElseGet(() -> {
                        Attendance created = new Attendance();
                        created.setStudent(student);
                        created.setMeetingDate(date);
                        return created;
                    });
            attendance.setService(entry.service());
            attendance.setMass(entry.mass());
            attendance.setUpdatedBy(users.getReferenceById(actorId));
            attendance.setUpdatedAt(clock.instant());
            attendance = attendanceRepository.save(attendance);

            for (AttendanceKind kind : AttendanceKind.values()) {
                int points = kind == AttendanceKind.MASS ? settings.getMassPoints() : settings.getServicePoints();
                int delta = pointsService.syncAttendancePoints(student, attendance, kind, points, actorId);
                if (delta > 0) awards++;
                if (delta < 0) reversals++;
                net += delta;
            }
        }
        return new SaveAttendanceResult(date, ordered.size(), awards, reversals, net);
    }
}
