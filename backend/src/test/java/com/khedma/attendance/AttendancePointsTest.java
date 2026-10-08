package com.khedma.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import com.khedma.points.PointTransaction;
import com.khedma.points.PointTransactionRepository;
import com.khedma.points.PointsDtos.ManualKind;
import com.khedma.points.PointsDtos.ManualPointsRequest;
import com.khedma.points.PointsService;
import com.khedma.points.TransactionType;
import com.khedma.settings.SettingsDtos.UpdateSettingsRequest;
import com.khedma.settings.SettingsService;
import com.khedma.student.Grade;
import com.khedma.student.Student;
import com.khedma.student.StudentRepository;
import com.khedma.user.AppUser;
import com.khedma.user.Role;
import com.khedma.user.UserRepository;
import com.khedma.user.UserStatus;

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
                .containsExactlyInAnyOrder("حضور خدمة يوم الجمعة 9 أكتوبر 2026", "حضور قداس يوم الجمعة 9 أكتوبر 2026");
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
        assertThat(reversal.getNotes()).isEqualTo("إلغاء حضور قداس يوم الجمعة 9 أكتوبر 2026");
        assertThat(reversal.getReverses().getType()).isEqualTo(TransactionType.ATTENDANCE_AWARD);

        // Marking again awards again.
        save(true, true);
        assertThat(balance()).isEqualTo(7);
    }

    @Test
    void reversalUsesOriginalAmountEvenAfterSettingsChange() {
        save(false, true); // +5
        settingsService.update(new UpdateSettingsRequest(10, 2, LocalDate.of(2026, 9, 18),
                LocalDate.of(2027, 9, 30), DayOfWeek.FRIDAY), adminId);

        save(false, false); // must reverse 5, not 10
        assertThat(balance()).isZero();

        save(false, true); // new award uses new setting
        assertThat(balance()).isEqualTo(10);
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
                .hasMessage("الرصيد لا يكفي للخصم");

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
