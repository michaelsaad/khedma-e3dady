package com.khedma.dashboard;

import java.text.Collator;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.khedma.attendance.Attendance;
import com.khedma.attendance.AttendanceRepository;
import com.khedma.attendance.MeetingCalendar;
import com.khedma.attendance.SkippedMeetingRepository;
import com.khedma.common.ApiException;
import com.khedma.common.ErrorCode;
import com.khedma.common.PageResponse;
import com.khedma.dashboard.DashboardDtos.GroupStanding;
import com.khedma.dashboard.DashboardDtos.LastMeeting;
import com.khedma.dashboard.DashboardDtos.MeetingRecord;
import com.khedma.dashboard.DashboardDtos.SortBy;
import com.khedma.dashboard.DashboardDtos.StudentCard;
import com.khedma.dashboard.DashboardDtos.StudentDetail;
import com.khedma.dashboard.DashboardDtos.Summary;
import com.khedma.group.GroupRepository;
import com.khedma.group.StudentGroup;
import com.khedma.points.PointsService;
import com.khedma.settings.AppSettings;
import com.khedma.settings.SettingsService;
import com.khedma.student.Grade;
import com.khedma.student.Student;
import com.khedma.student.StudentRepository;

/**
 * Read-only statistics for the dashboard. Computed in memory from the season's attendance,
 * which is small (students x weekly meetings), so this stays simple and fast.
 */
@Service
public class DashboardService {

    private static final int TOP_STUDENTS = 5;
    private static final Collator ARABIC_NAMES = Collator.getInstance(Locale.forLanguageTag("ar"));

    private final StudentRepository students;
    private final GroupRepository groups;
    private final AttendanceRepository attendance;
    private final SettingsService settingsService;
    private final MeetingCalendar calendar;
    private final PointsService pointsService;
    private final SkippedMeetingRepository skippedMeetings;

    public DashboardService(StudentRepository students, GroupRepository groups, AttendanceRepository attendance,
                            SettingsService settingsService, MeetingCalendar calendar, PointsService pointsService,
                            SkippedMeetingRepository skippedMeetings) {
        this.students = students;
        this.groups = groups;
        this.attendance = attendance;
        this.settingsService = settingsService;
        this.calendar = calendar;
        this.pointsService = pointsService;
        this.skippedMeetings = skippedMeetings;
    }

    @Transactional(readOnly = true)
    public Summary summary() {
        Season season = loadSeason();
        List<StudentCard> cards = season.cards();

        LastMeeting last = null;
        if (!season.meetings().isEmpty()) {
            LocalDate date = season.meetings().get(season.meetings().size() - 1);
            List<Attendance> rows = season.records().stream()
                    .filter(a -> a.getMeetingDate().equals(date))
                    .filter(a -> season.activeIds().contains(a.getStudent().getId()))
                    .toList();
            last = new LastMeeting(date,
                    (int) rows.stream().filter(Attendance::isService).count(),
                    (int) rows.stream().filter(Attendance::isMass).count());
        }

        List<StudentGroup> allGroups = groups.findAllByOrderByNameAsc();
        Map<Long, Long> members = cards.stream()
                .filter(card -> card.groupId() != null)
                .collect(Collectors.groupingBy(StudentCard::groupId, Collectors.counting()));
        List<StudentGroup> byPoints = allGroups.stream()
                .sorted(Comparator.comparingInt(StudentGroup::getPointsBalance).reversed())
                .toList();
        List<GroupStanding> standings = byPoints.stream()
                .map(g -> new GroupStanding(g.getId(), g.getName(), g.getPointsBalance(),
                        rank(byPoints, g, StudentGroup::getPointsBalance), members.getOrDefault(g.getId(), 0L)))
                .toList();

        return new Summary(
                cards.size(),
                allGroups.size(),
                season.meetings().size(),
                last,
                average(cards, StudentCard::serviceRate),
                average(cards, StudentCard::massRate),
                cards.stream().sorted(Comparator.comparingInt(StudentCard::rank)).limit(TOP_STUDENTS).toList(),
                standings);
    }

    @Transactional(readOnly = true)
    public PageResponse<StudentCard> cards(String query, Grade grade, Long groupId, SortBy sortBy, int page, int size) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<StudentCard> filtered = loadSeason().cards().stream()
                .filter(card -> grade == null || card.grade() == grade)
                .filter(card -> groupId == null || groupId.equals(card.groupId()))
                .filter(card -> q.isEmpty() || card.name().toLowerCase(Locale.ROOT).contains(q))
                .sorted(comparator(sortBy))
                .toList();

        int safeSize = Math.min(Math.max(size, 1), 200);
        int safePage = Math.max(page, 0);
        int from = Math.min(safePage * safeSize, filtered.size());
        int to = Math.min(from + safeSize, filtered.size());
        int totalPages = (filtered.size() + safeSize - 1) / safeSize;
        return new PageResponse<>(filtered.subList(from, to), safePage, safeSize, filtered.size(), totalPages);
    }

    @Transactional(readOnly = true)
    public StudentDetail student(Long id) {
        Season season = loadSeason();
        StudentCard card = season.cards().stream()
                .filter(c -> c.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.STUDENT_NOT_FOUND));

        Map<LocalDate, Attendance> byDate = season.records().stream()
                .filter(a -> a.getStudent().getId().equals(id))
                .collect(Collectors.toMap(Attendance::getMeetingDate, Function.identity()));
        List<MeetingRecord> history = season.meetings().stream()
                .sorted(Comparator.reverseOrder())
                .map(date -> {
                    Attendance a = byDate.get(date);
                    return new MeetingRecord(date, a != null && a.isService(), a != null && a.isMass());
                })
                .toList();

        return new StudentDetail(card, history, pointsService.forStudent(id, 0, 20).items());
    }

    // ---- internals ------------------------------------------------------------------------

    private record Season(List<LocalDate> meetings, List<Attendance> records, Set<Long> activeIds,
                          List<StudentCard> cards) {}

    private Season loadSeason() {
        AppSettings settings = settingsService.current();
        // Skipped meetings (not held) count for nothing: not in "meetings held", rates or history.
        Set<LocalDate> skipped = skippedMeetings.allDates();
        List<LocalDate> meetings = calendar.pastMeetings(settings).stream().filter(d -> !skipped.contains(d)).toList();
        Set<LocalDate> meetingSet = new HashSet<>(meetings);

        List<Attendance> records = meetings.isEmpty() ? List.of()
                : attendance.findAllByMeetingDateBetween(meetings.get(0), meetings.get(meetings.size() - 1)).stream()
                        .filter(a -> meetingSet.contains(a.getMeetingDate()))
                        .toList();

        Map<Long, int[]> counts = new HashMap<>();
        for (Attendance a : records) {
            int[] c = counts.computeIfAbsent(a.getStudent().getId(), k -> new int[2]);
            if (a.isService()) c[0]++;
            if (a.isMass()) c[1]++;
        }

        List<Student> active = students.findAllByActiveTrue();
        List<Student> byPoints = active.stream()
                .sorted(Comparator.comparingInt(Student::getPointsBalance).reversed())
                .toList();
        int held = meetings.size();
        List<StudentCard> cards = active.stream()
                .map(s -> {
                    int[] c = counts.getOrDefault(s.getId(), new int[2]);
                    return new StudentCard(
                            s.getId(),
                            s.getName(),
                            s.getGrade(),
                            s.getPhone(),
                            s.getGroup() == null ? null : s.getGroup().getId(),
                            s.getGroup() == null ? null : s.getGroup().getName(),
                            s.getPointsBalance(),
                            rank(byPoints, s, Student::getPointsBalance),
                            c[0],
                            c[1],
                            held,
                            percent(c[0], held),
                            percent(c[1], held));
                })
                .toList();
        Set<Long> activeIds = active.stream().map(Student::getId).collect(Collectors.toSet());
        return new Season(meetings, records, activeIds, cards);
    }

    /** Competition ranking (1, 1, 3): one more than the number of items with strictly more points. */
    private static <T> int rank(List<T> sortedDesc, T item, ToIntFunction<T> points) {
        int value = points.applyAsInt(item);
        return (int) sortedDesc.stream().filter(other -> points.applyAsInt(other) > value).count() + 1;
    }

    private static int percent(int count, int total) {
        return total == 0 ? 0 : Math.round(count * 100f / total);
    }

    private static int average(List<StudentCard> cards, ToIntFunction<StudentCard> rate) {
        return cards.isEmpty() ? 0 : (int) Math.round(cards.stream().mapToInt(rate).average().orElse(0));
    }

    private static Comparator<StudentCard> comparator(SortBy sortBy) {
        Comparator<StudentCard> byName = Comparator.comparing(StudentCard::name, ARABIC_NAMES);
        return switch (sortBy == null ? SortBy.POINTS : sortBy) {
            case POINTS -> Comparator.comparingInt(StudentCard::rank).thenComparing(byName);
            case NAME -> Comparator.comparing(StudentCard::grade).thenComparing(byName);
            case ATTENDANCE -> Comparator.comparingInt((StudentCard c) -> c.serviceCount() + c.massCount())
                    .reversed().thenComparing(byName);
        };
    }
}
