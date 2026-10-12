package com.khedma.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import com.khedma.TestcontainersConfig;
import com.khedma.attendance.AttendanceDtos.AttendanceEntry;
import com.khedma.attendance.AttendanceDtos.SaveAttendanceResult;
import com.khedma.common.ApiException;
import com.khedma.common.ErrorCode;
import com.khedma.dashboard.DashboardService;
import com.khedma.points.PointTransaction;
import com.khedma.points.PointTransactionRepository;
import com.khedma.points.PointsDtos.ManualKind;
import com.khedma.points.PointsDtos.ManualPointsRequest;
import com.khedma.points.PointsService;
import com.khedma.points.TransactionType;
import com.khedma.points.reason.PointReasonService;
import com.khedma.points.reason.PointReasonService.ReasonRequest;
import com.khedma.points.reason.PointReasonService.ReasonResponse;
import com.khedma.settings.SettingsDtos.UpdateSettingsRequest;
import com.khedma.settings.SettingsService;
import com.khedma.student.Grade;
import com.khedma.student.Student;
import com.khedma.student.StudentRepository;
import com.khedma.user.AppUser;
import com.khedma.user.Role;
import com.khedma.user.UserRepository;
import com.khedma.user.UserStatus;

import jakarta.persistence.EntityManager;

@SpringBootTest
@Import(TestcontainersConfig.class)
@Transactional
class AttendancePointsTest {

    private static final LocalDate OCT_9 = LocalDate.of(2026, 10, 9);

    @Autowired AttendanceService attendanceService;
    @Autowired PointsService pointsService;
    @Autowired SettingsService settingsService;
    @Autowired StudentRepository students;
    @Autowired UserRepository users;
    @Autowired PointTransactionRepository transactions;
    @Autowired DashboardService dashboardService;
    @Autowired PointReasonService reasonService;
    @Autowired EntityManager entityManager;

    Long adminId;
    Student student;

    @BeforeEach
    void setUp() {
        AppUser admin = new AppUser();
        admin.setEmail("admin@test.local");
        admin.setDisplayName("Admin");
        admin.setRole(Role.ADMIN);
        admin.setStatus(UserStatus.APPROVED);
        adminId = users.save(admin).getId();

        Student s = new Student();
        s.setName("مينا");
        s.setGrade(Grade.FIRST);
        student = students.save(s);
    }

    @Test
    void markingAttendanceAwardsPointsFromSettings() {
        SaveAttendanceResult result = save(true, true);

        assertThat(result.netPoints()).isEqualTo(7); // service 2 + mass 5
        assertThat(result.awardsCreated()).isEqualTo(2);
        assertThat(balance()).isEqualTo(7);
        assertThat(studentTransactions())
                .extracting(PointTransaction::getNotes)
                .containsExactlyInAnyOrder("Service attendance on 2026-10-09", "Mass attendance on 2026-10-09");
    }

    @Test
    void savingSameAttendanceTwiceDoesNotDoubleAward() {
        save(true, true);
        SaveAttendanceResult second = save(true, true);

        assertThat(second.netPoints()).isZero();
        assertThat(balance()).isEqualTo(7);
        assertThat(studentTransactions()).hasSize(2);
    }

    @Test
    void unmarkingReversesTheAward() {
        save(true, true);
        SaveAttendanceResult result = save(true, false);

        assertThat(result.netPoints()).isEqualTo(-5);
        assertThat(result.reversalsCreated()).isEqualTo(1);
        assertThat(balance()).isEqualTo(2);
        PointTransaction reversal = studentTransactions().stream()
                .filter(t -> t.getType() == TransactionType.ATTENDANCE_REVERSAL)
                .findFirst().orElseThrow();
        assertThat(reversal.getAmount()).isEqualTo(-5);
        assertThat(reversal.getNotes()).isEqualTo("Reversal: Mass attendance on 2026-10-09");
        assertThat(reversal.getReverses().getType()).isEqualTo(TransactionType.ATTENDANCE_AWARD);

        // Marking again awards again.
        save(true, true);
        assertThat(balance()).isEqualTo(7);
    }

    @Test
    void reversalUsesOriginalAmountEvenAfterSettingsChange() {
        save(false, true); // +5
        settingsService.update(new UpdateSettingsRequest(10, 2, LocalDate.of(2026, 9, 18),
                LocalDate.of(2027, 9, 30), DayOfWeek.FRIDAY, LocalDate.of(2026, 9, 18)), adminId);

        save(false, false); // must reverse 5, not 10
        assertThat(balance()).isZero();

        save(false, true); // new award uses new setting
        assertThat(balance()).isEqualTo(10);
    }

    @Test
    void meetingsBeforePointsStartDateAwardNothingButOldAwardsCanStillBeReversed() {
        save(true, true); // +7 while distribution is on
        settingsService.update(new UpdateSettingsRequest(5, 2, LocalDate.of(2026, 9, 18),
                LocalDate.of(2027, 9, 30), DayOfWeek.FRIDAY, LocalDate.of(2026, 10, 16)), adminId);

        // Imported-style history: marking an old meeting gives no points.
        LocalDate oct2 = LocalDate.of(2026, 10, 2);
        SaveAttendanceResult imported = attendanceService.save(oct2,
                List.of(new AttendanceEntry(student.getId(), true, true)), adminId);
        assertThat(imported.netPoints()).isZero();
        assertThat(balance()).isEqualTo(7);

        // Un-marking an old meeting that had no award doesn't take points away...
        attendanceService.save(oct2, List.of(new AttendanceEntry(student.getId(), false, false)), adminId);
        assertThat(balance()).isEqualTo(7);
        // ...but an award that really happened is still reversed.
        save(false, false);
        assertThat(balance()).isZero();
    }

    @Test
    void rejectsNonMeetingAndFutureDates() {
        assertThatThrownBy(() -> attendanceService.save(LocalDate.of(2026, 10, 8),
                List.of(new AttendanceEntry(student.getId(), true, true)), adminId))
                .isInstanceOf(ApiException.class); // Thursday
        assertThatThrownBy(() -> attendanceService.save(LocalDate.of(2026, 10, 16),
                List.of(new AttendanceEntry(student.getId(), true, true)), adminId))
                .isInstanceOf(ApiException.class); // future Friday
    }

    @Test
    void manualTransactionsWorkAlongsideAutomaticOnes() {
        save(true, true); // 7
        pointsService.manualForStudent(student.getId(), new ManualPointsRequest(ManualKind.ADD, 3, "مسابقة"), adminId);
        assertThat(balance()).isEqualTo(10);

        assertThatThrownBy(() -> pointsService.manualForStudent(student.getId(),
                new ManualPointsRequest(ManualKind.DEDUCT, 11, "هدية"), adminId))
                .isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo(ErrorCode.INSUFFICIENT_BALANCE);

        pointsService.manualForStudent(student.getId(), new ManualPointsRequest(ManualKind.RESET, null, "بداية ترم"), adminId);
        assertThat(balance()).isZero();
    }

    @Test
    void meetingViewHasNeighbourNavigation() {
        var view = attendanceService.forMeeting(LocalDate.of(2026, 10, 2));
        assertThat(view.previousMeeting()).isEqualTo(LocalDate.of(2026, 9, 25));
        assertThat(view.nextMeeting()).isEqualTo(OCT_9);
        assertThat(attendanceService.forMeeting(OCT_9).nextMeeting()).isNull();
    }

    @Test
    void savedReasonsAreRankedByUsage() {
        reasonService.create(new ReasonRequest("Quiz", 5));
        reasonService.create(new ReasonRequest("Bible verse", null));
        pointsService.manualForStudent(student.getId(), new ManualPointsRequest(ManualKind.ADD, 5, "Bible verse"), adminId);
        pointsService.manualForStudent(student.getId(), new ManualPointsRequest(ManualKind.ADD, 5, "Something else"), adminId);
        entityManager.flush();
        entityManager.clear(); // the usage counter is a bulk update; read fresh rows like a new request would

        assertThat(reasonService.list())
                .extracting(ReasonResponse::label, ReasonResponse::usageCount)
                .containsExactly(tuple("Bible verse", 1), tuple("Quiz", 0));
        assertThatThrownBy(() -> reasonService.create(new ReasonRequest("Quiz", null)))
                .extracting("code").isEqualTo(ErrorCode.REASON_ALREADY_EXISTS);
    }

    @Test
    void skippingAMeetingReversesItsPointsAndRemovesItFromStatistics() {
        save(true, true); // +7 on 9 Oct
        assertThat(dashboardService.student(student.getId()).card().meetingsHeld()).isEqualTo(4);

        SaveAttendanceResult skipped = attendanceService.skip(OCT_9, "Retreat", adminId);
        assertThat(skipped.netPoints()).isEqualTo(-7);
        assertThat(balance()).isZero();
        assertThat(studentTransactions()).filteredOn(t -> t.getType() == TransactionType.ATTENDANCE_REVERSAL).hasSize(2);

        var card = dashboardService.student(student.getId()).card();
        assertThat(card.meetingsHeld()).isEqualTo(3); // 9 Oct no longer counts
        assertThat(card.serviceCount()).isZero();
        assertThat(attendanceService.forMeeting(OCT_9).skipped()).isTrue();
        assertThat(attendanceService.meetings().skipped()).containsExactly(OCT_9);

        // No attendance while skipped; skipping twice changes nothing.
        assertThatThrownBy(() -> save(true, false))
                .isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo(ErrorCode.MEETING_SKIPPED);
        assertThat(attendanceService.skip(OCT_9, "Retreat", adminId).netPoints()).isZero();

        // Restoring re-awards the recorded attendance.
        SaveAttendanceResult restored = attendanceService.unskip(OCT_9, adminId);
        assertThat(restored.netPoints()).isEqualTo(7);
        assertThat(balance()).isEqualTo(7);
        assertThat(dashboardService.student(student.getId()).card().meetingsHeld()).isEqualTo(4);
    }

    @Test
    void dashboardCardReflectsAttendanceAndPoints() {
        save(true, true);
        var card = dashboardService.student(student.getId()).card();

        assertThat(card.points()).isEqualTo(7);
        assertThat(card.rank()).isEqualTo(1);
        assertThat(card.meetingsHeld()).isEqualTo(4); // 18 Sep .. 9 Oct
        assertThat(card.serviceCount()).isEqualTo(1);
        assertThat(card.massRate()).isEqualTo(25);
        assertThat(dashboardService.student(student.getId()).attendance()).hasSize(4);
    }

    private SaveAttendanceResult save(boolean service, boolean mass) {
        return attendanceService.save(OCT_9, List.of(new AttendanceEntry(student.getId(), service, mass)), adminId);
    }

    private int balance() {
        return students.findById(student.getId()).orElseThrow().getPointsBalance();
    }

    private List<PointTransaction> studentTransactions() {
        return transactions.findAll().stream()
                .filter(t -> t.getStudent() != null && t.getStudent().getId().equals(student.getId()))
                .toList();
    }
}
