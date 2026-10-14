package com.khedma.push;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.khedma.attendance.Attendance;
import com.khedma.attendance.AttendanceRepository;
import com.khedma.attendance.MeetingCalendar;
import com.khedma.attendance.SkippedMeetingRepository;
import com.khedma.config.AppProperties;
import com.khedma.push.PushService.Message;
import com.khedma.settings.AppSettings;
import com.khedma.settings.SettingsService;
import com.khedma.student.Student;
import com.khedma.student.StudentRepository;

/**
 * The two timed notifications. Each runs once a day; a marker row per day makes it safe against restarts and
 * a second instance. The public methods take "today" so tests can run them for any date.
 */
@Component
public class PushScheduler {

    private static final int MAX_NAMES = 4;

    private final PushService pushService;
    private final PushRunRepository runs;
    private final SettingsService settingsService;
    private final MeetingCalendar calendar;
    private final SkippedMeetingRepository skippedMeetings;
    private final AttendanceRepository attendance;
    private final StudentRepository students;
    private final AppProperties.Push config;
    private final Clock clock;

    public PushScheduler(PushService pushService, PushRunRepository runs, SettingsService settingsService,
                         MeetingCalendar calendar, SkippedMeetingRepository skippedMeetings,
                         AttendanceRepository attendance, StudentRepository students, AppProperties properties, Clock clock) {
        this.pushService = pushService;
        this.runs = runs;
        this.settingsService = settingsService;
        this.calendar = calendar;
        this.skippedMeetings = skippedMeetings;
        this.attendance = attendance;
        this.students = students;
        this.config = properties.push();
        this.clock = clock;
    }

    @Scheduled(cron = "${app.push.friday-reminder-cron}", zone = "${app.timezone}")
    void scheduledReminder() {
        if (config.schedulerEnabled()) meetingReminder(LocalDate.now(clock));
    }

    @Scheduled(cron = "${app.push.absence-alert-cron}", zone = "${app.timezone}")
    void scheduledAbsenceAlert() {
        if (config.schedulerEnabled()) absenceAlert(LocalDate.now(clock));
    }

    /** The evening before a meeting: remind the admins to take attendance. Nothing for a skipped meeting. */
    public void meetingReminder(LocalDate today) {
        if (!pushService.enabled()) return;
        AppSettings settings = settingsService.current();
        LocalDate meeting = today.plusDays(1);
        boolean isMeeting = meeting.getDayOfWeek() == settings.getMeetingDay()
                && !meeting.isBefore(settings.getSeasonStart())
                && !meeting.isAfter(settings.getSeasonEnd())
                && !skippedMeetings.existsById(meeting);
        if (!isMeeting || !runs.claim("meeting-reminder:" + meeting)) return;

        pushService.sendToUsers(pushService.accountIds(Audience.ADMINS),
                new Message("تذكير الحضور", "بكرة اجتماع الخدمة، متنساش تسجّل الحضور.", "/attendance", "meeting-reminder"));
    }

    /**
     * The day after a meeting: tell the admins who missed both service and mass in each of the last few held
     * meetings. Skipped meetings don't count, and nothing is sent if attendance was never recorded for one of them
     * (otherwise a forgotten Friday would flag everyone).
     */
    public void absenceAlert(LocalDate today) {
        if (!pushService.enabled()) return;
        AppSettings settings = settingsService.current();
        LocalDate lastMeeting = today.minusDays(1);
        if (lastMeeting.getDayOfWeek() != settings.getMeetingDay()) return;

        Set<LocalDate> skipped = skippedMeetings.allDates();
        List<LocalDate> held = calendar.pastMeetings(settings).stream().filter(date -> !skipped.contains(date)).toList();
        int needed = Math.max(config.absenceMissedMeetings(), 1);
        if (held.size() < needed || !held.get(held.size() - 1).equals(lastMeeting)) return;

        Set<Long> attended = new HashSet<>();
        for (LocalDate date : held.subList(held.size() - needed, held.size())) {
            List<Attendance> records = attendance.findAllByMeetingDate(date);
            if (records.isEmpty()) return;
            records.stream().filter(a -> a.isService() || a.isMass()).forEach(a -> attended.add(a.getStudent().getId()));
        }

        List<String> absent = students.findAllByActiveTrue().stream()
                .filter(student -> !attended.contains(student.getId()))
                .sorted(Comparator.comparing(Student::getName))
                .map(student -> firstName(student.getName()))
                .toList();
        if (absent.isEmpty() || !runs.claim("absence-alert:" + lastMeeting)) return;

        pushService.sendToUsers(pushService.accountIds(Audience.ADMINS),
                new Message("غياب متتالي", absenceBody(absent, needed), "/", "absence-alert"));
    }

    static String absenceBody(List<String> names, int meetings) {
        String period = meetings == 2 ? "اجتماعين" : ArabicDigits.of(meetings) + " اجتماعات";
        String shown = String.join("، ", names.subList(0, Math.min(names.size(), MAX_NAMES)));
        int more = names.size() - MAX_NAMES;
        return "غابوا آخر " + period + ": " + shown + (more > 0 ? " و" + ArabicDigits.of(more) + " كمان" : "");
    }

    private static String firstName(String fullName) {
        return fullName.trim().split("\\s+")[0];
    }
}
