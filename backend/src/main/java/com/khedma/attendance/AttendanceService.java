package com.khedma.attendance;

import java.text.Collator;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.khedma.attendance.AttendanceDtos.AttendanceRow;
import com.khedma.attendance.AttendanceDtos.MeetingAttendance;
import com.khedma.attendance.AttendanceDtos.MeetingsResponse;
import com.khedma.attendance.AttendanceDtos.SaveAttendanceResult;
import com.khedma.common.ApiException;
import com.khedma.common.ErrorCode;
import com.khedma.points.PointTransactionRepository;
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
    private final SkippedMeetingRepository skippedMeetings;
    private final StudentRepository students;
    private final UserRepository users;
    private final PointsService pointsService;
    private final PointTransactionRepository transactions;
    private final SettingsService settingsService;
    private final MeetingCalendar calendar;
    private final Clock clock;

    public AttendanceService(AttendanceRepository attendanceRepository, SkippedMeetingRepository skippedMeetings,
                             StudentRepository students, UserRepository users, PointsService pointsService,
                             PointTransactionRepository transactions, SettingsService settingsService,
                             MeetingCalendar calendar, Clock clock) {
        this.attendanceRepository = attendanceRepository;
        this.skippedMeetings = skippedMeetings;
        this.students = students;
        this.users = users;
        this.pointsService = pointsService;
        this.transactions = transactions;
        this.settingsService = settingsService;
        this.calendar = calendar;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public MeetingsResponse meetings() {
        List<LocalDate> meetings = calendar.pastMeetings(settingsService.current());
        var skipped = skippedMeetings.allDates();
        return new MeetingsResponse(meetings, calendar.today(), meetings.stream().filter(skipped::contains).toList());
    }

    @Transactional(readOnly = true)
    public MeetingAttendance forMeeting(LocalDate date) {
        AppSettings settings = settingsService.current();
        calendar.requireValidMeeting(date, settings);
        Map<Long, Attendance> byStudent = attendanceRepository.findAllByMeetingDate(date).stream()
                .collect(Collectors.toMap(a -> a.getStudent().getId(), Function.identity()));
        Map<Long, Integer> extra = transactions.sumManualPointsByStudent(date).stream()
                .collect(Collectors.toMap(r -> (Long) r[0], r -> ((Number) r[1]).intValue()));
        List<AttendanceRow> rows = students.findAllByActiveTrue().stream()
                .sorted(BY_GRADE_THEN_NAME)
                .map(student -> {
                    Attendance a = byStudent.get(student.getId());
                    return new AttendanceRow(
                            student.getId(),
                            student.getName(),
                            student.getGrade(),
                            student.getGroup() == null ? null : student.getGroup().getId(),
                            student.getGroup() == null ? null : student.getGroup().getName(),
                            a != null && a.isService(),
                            a != null && a.isMass(),
                            a != null,
                            extra.getOrDefault(student.getId(), 0));
                })
                .toList();
        List<LocalDate> meetings = calendar.pastMeetings(settings);
        int index = meetings.indexOf(date);
        Optional<SkippedMeeting> skipped = skippedMeetings.findById(date);
        return new MeetingAttendance(
                date,
                index > 0 ? meetings.get(index - 1) : null,
                index >= 0 && index < meetings.size() - 1 ? meetings.get(index + 1) : null,
                (int) rows.stream().filter(AttendanceRow::service).count(),
                (int) rows.stream().filter(AttendanceRow::mass).count(),
                skipped.isPresent(),
                skipped.map(SkippedMeeting::getReason).orElse(null),
                rows);
    }

    /**
     * Saves one student's attendance for a meeting and, in the same transaction, distributes points:
     * newly marked service/mass -> award; un-marked -> reverse the earlier award.
     */
    @Transactional
    public SaveAttendanceResult save(LocalDate date, Long studentId, boolean service, boolean mass, Long actorId) {
        AppSettings settings = settingsService.current();
        calendar.requireValidMeeting(date, settings);
        if (skippedMeetings.existsById(date)) {
            throw new ApiException(ErrorCode.MEETING_SKIPPED);
        }
        Student student = students.findByIdForUpdate(studentId)
                .filter(Student::isActive)
                .orElseThrow(() -> new ApiException(ErrorCode.STUDENT_NOT_FOUND));

        Attendance attendance = attendanceRepository.findByStudentIdAndMeetingDate(studentId, date)
                .orElseGet(() -> {
                    Attendance created = new Attendance();
                    created.setStudent(student);
                    created.setMeetingDate(date);
                    return created;
                });
        attendance.setService(service);
        attendance.setMass(mass);
        attendance.setUpdatedBy(users.getReferenceById(actorId));
        attendance.setUpdatedAt(clock.instant());
        attendance = attendanceRepository.save(attendance);

        PointsTally tally = new PointsTally();
        syncPoints(student, attendance, settings, actorId, false, tally);
        return new SaveAttendanceResult(date, 1, tally.awards, tally.reversals, tally.net);
    }

    /**
     * Marks a meeting as not held: every attendance point awarded for it is reversed, attendance
     * can't be recorded while it's skipped, and the dashboard leaves it out of rates and counts.
     * The attendance rows themselves are kept, so un-skipping restores everything.
     */
    @Transactional
    public SaveAttendanceResult skip(LocalDate date, String reason, Long actorId) {
        AppSettings settings = settingsService.current();
        calendar.requireValidMeeting(date, settings);
        SkippedMeeting skipped = skippedMeetings.findById(date).orElseGet(() -> {
            SkippedMeeting created = new SkippedMeeting();
            created.setMeetingDate(date);
            created.setCreatedBy(users.getReferenceById(actorId));
            created.setCreatedAt(clock.instant());
            return created;
        });
        skipped.setReason(reason == null ? "" : reason.trim());
        skippedMeetings.save(skipped);
        return resyncMeeting(date, settings, actorId, true);
    }

    /** Restores a skipped meeting and re-awards points for its recorded attendance (from the points start date). */
    @Transactional
    public SaveAttendanceResult unskip(LocalDate date, Long actorId) {
        AppSettings settings = settingsService.current();
        calendar.requireValidMeeting(date, settings);
        skippedMeetings.findById(date).ifPresent(skippedMeetings::delete);
        return resyncMeeting(date, settings, actorId, false);
    }

    private SaveAttendanceResult resyncMeeting(LocalDate date, AppSettings settings, Long actorId, boolean skipped) {
        List<Attendance> rows = attendanceRepository.findAllByMeetingDate(date).stream()
                .sorted(Comparator.comparing(a -> a.getStudent().getId()))
                .toList();
        PointsTally tally = new PointsTally();
        for (Attendance attendance : rows) {
            Student student = students.findByIdForUpdate(attendance.getStudent().getId()).orElseThrow();
            syncPoints(student, attendance, settings, actorId, skipped, tally);
        }
        return new SaveAttendanceResult(date, rows.size(), tally.awards, tally.reversals, tally.net);
    }

    private void syncPoints(Student student, Attendance attendance, AppSettings settings, Long actorId,
                            boolean skipped, PointsTally tally) {
        for (AttendanceKind kind : AttendanceKind.values()) {
            // Before the points start date nothing is awarded; earlier awards can still be reversed.
            int points = !settings.awardsPointsOn(attendance.getMeetingDate()) ? 0
                    : kind == AttendanceKind.MASS ? settings.getMassPoints() : settings.getServicePoints();
            int delta = pointsService.syncAttendancePoints(student, attendance, kind, points, actorId, skipped);
            if (delta > 0) tally.awards++;
            if (delta < 0) tally.reversals++;
            tally.net += delta;
        }
    }

    private static final class PointsTally {
        int awards;
        int reversals;
        int net;
    }
}
