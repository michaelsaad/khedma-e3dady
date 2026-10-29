package com.khedma.points.period;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import com.khedma.TestcontainersConfig;
import com.khedma.attendance.AttendanceService;
import com.khedma.common.ErrorCode;
import com.khedma.dashboard.DashboardDtos.StudentCard;
import com.khedma.dashboard.DashboardService;
import com.khedma.group.GroupRepository;
import com.khedma.group.StudentGroup;
import com.khedma.points.PointTransactionRepository;
import com.khedma.points.PointsDtos.ManualKind;
import com.khedma.points.PointsDtos.ManualPointsRequest;
import com.khedma.points.PointsDtos.TransactionResponse;
import com.khedma.points.PointsService;
import com.khedma.student.Grade;
import com.khedma.student.Student;
import com.khedma.student.StudentRepository;
import com.khedma.user.AppUser;
import com.khedma.user.Role;
import com.khedma.user.UserRepository;
import com.khedma.user.UserStatus;

import jakarta.persistence.EntityManager;

/**
 * "Today" is Saturday 10 Oct 2026 (see TestcontainersConfig). The scenario: the party was on Friday 9 Oct,
 * the new period starts on the 10th.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@Transactional
class PointPeriodsTest {

    private static final LocalDate PARTY_FRIDAY = LocalDate.of(2026, 10, 9);
    private static final LocalDate AFTER_PARTY = LocalDate.of(2026, 10, 10);

    @Autowired PointPeriodService periods;
    @Autowired PointsService pointsService;
    @Autowired AttendanceService attendanceService;
    @Autowired DashboardService dashboard;
    @Autowired StudentRepository students;
    @Autowired GroupRepository groups;
    @Autowired UserRepository users;
    @Autowired PointTransactionRepository transactions;
    @Autowired EntityManager entityManager;

    Long adminId;
    Student mina;
    Long firstPeriodId;

    @BeforeEach
    void setUp() {
        AppUser admin = new AppUser();
        admin.setEmail("periods@test.local");
        admin.setDisplayName("Admin");
        admin.setRole(Role.ADMIN);
        admin.setStatus(UserStatus.APPROVED);
        adminId = users.save(admin).getId();

        Student s = new Student();
        s.setName("مينا");
        s.setGrade(Grade.FIRST);
        mina = students.save(s);
        firstPeriodId = periods.current().id();
    }

    @Test
    void aNewPeriodStartsEveryoneAtZeroWhileTheTotalStays() {
        attendanceService.save(PARTY_FRIDAY, mina.getId(), true, true, adminId); // +7 on the party Friday
        assertThat(card(null).points()).isEqualTo(7);

        periods.create("بعد الحفلة", AFTER_PARTY, adminId);

        StudentCard now = card(null);
        assertThat(now.points()).isZero();
        assertThat(now.totalPoints()).isEqualTo(7);

        add(4, "سلوك");
        now = card(null);
        assertThat(now.points()).isEqualTo(4);
        assertThat(now.totalPoints()).isEqualTo(11);

        // The previous period still shows exactly what it had.
        assertThat(card(firstPeriodId).points()).isEqualTo(7);
        assertThat(dashboard.summary(firstPeriodId).period().name()).isEqualTo("من البداية");
        assertThat(dashboard.summary(null).period().current()).isTrue();
    }

    @Test
    void lateAttendanceForAnOldFridayCountsInThatFridaysPeriod() {
        periods.create("بعد الحفلة", AFTER_PARTY, adminId);

        // Recorded after the new period started, but for the 2nd of October.
        attendanceService.save(LocalDate.of(2026, 10, 2), mina.getId(), true, false, adminId);

        assertThat(card(null).points()).isZero();
        assertThat(card(firstPeriodId).points()).isPositive();
    }

    @Test
    void undoingAnOldBonusCorrectsItsOwnPeriod() {
        TransactionResponse bonus = pointsService.manualForStudent(mina.getId(),
                new ManualPointsRequest(ManualKind.ADD, 3, "مزمور", PARTY_FRIDAY), adminId);
        backdate(bonus.id(), PARTY_FRIDAY); // as if it was given on the party day
        periods.create("بعد الحفلة", AFTER_PARTY, adminId);

        pointsService.undoMeetingBonus(mina.getId(), bonus.id(), adminId);
        entityManager.flush();

        assertThat(card(null).points()).isZero();
        assertThat(card(firstPeriodId).points()).isZero();
    }

    @Test
    void deductAndResetOnlyTouchTheCurrentPeriod() {
        attendanceService.save(PARTY_FRIDAY, mina.getId(), true, true, adminId); // 7 in the old period
        periods.create("بعد الحفلة", AFTER_PARTY, adminId);
        add(3, "سلوك");

        assertThatThrownBy(() -> pointsService.manualForStudent(mina.getId(),
                new ManualPointsRequest(ManualKind.DEDUCT, 5, "خصم", null), adminId))
                .extracting("code").isEqualTo(ErrorCode.INSUFFICIENT_BALANCE);

        pointsService.manualForStudent(mina.getId(), new ManualPointsRequest(ManualKind.DEDUCT, 2, "خصم", null), adminId);
        assertThat(card(null).points()).isEqualTo(1);

        pointsService.manualForStudent(mina.getId(), new ManualPointsRequest(ManualKind.RESET, null, "تصفير", null), adminId);
        StudentCard now = card(null);
        assertThat(now.points()).isZero();
        assertThat(now.totalPoints()).isEqualTo(7); // the old period's points were not reset
        assertThat(card(firstPeriodId).points()).isEqualTo(7);

        assertThatThrownBy(() -> pointsService.manualForStudent(mina.getId(),
                new ManualPointsRequest(ManualKind.RESET, null, "تصفير", null), adminId))
                .extracting("code").isEqualTo(ErrorCode.BALANCE_ALREADY_ZERO);
    }

    @Test
    void groupsStartAgainToo() {
        StudentGroup group = new StudentGroup();
        group.setName("مارجرجس");
        group = groups.save(group);
        TransactionResponse before = pointsService.manualForGroup(group.getId(),
                new ManualPointsRequest(ManualKind.ADD, 5, "مسابقة", null), adminId);
        backdate(before.id(), PARTY_FRIDAY);
        periods.create("بعد الحفلة", AFTER_PARTY, adminId);
        pointsService.manualForGroup(group.getId(), new ManualPointsRequest(ManualKind.ADD, 2, "مسابقة", null), adminId);
        entityManager.flush();

        Long id = group.getId();
        var now = dashboard.summary(null).groups().stream().filter(g -> g.id().equals(id)).findFirst().orElseThrow();
        assertThat(now.points()).isEqualTo(2);
        assertThat(now.totalPoints()).isEqualTo(7);
        var old = dashboard.summary(firstPeriodId).groups().stream().filter(g -> g.id().equals(id)).findFirst().orElseThrow();
        assertThat(old.points()).isEqualTo(5);
    }

    @Test
    void periodsMustMoveForwardAndOnlyTheLatestCanBeRemoved() {
        LocalDate firstStart = periods.current().from();
        assertThatThrownBy(() -> periods.create("قبل", firstStart, adminId))
                .extracting("code").isEqualTo(ErrorCode.PERIOD_START_INVALID);
        assertThatThrownBy(() -> periods.delete(firstPeriodId))
                .extracting("code").isEqualTo(ErrorCode.PERIOD_LAST_ONE);

        attendanceService.save(PARTY_FRIDAY, mina.getId(), true, true, adminId);
        var party = periods.create("بعد الحفلة", AFTER_PARTY, adminId);
        var later = periods.create("بعد العيد", LocalDate.of(2026, 12, 1), adminId); // scheduled ahead
        assertThat(later.upcoming()).isTrue();
        assertThat(periods.current().id()).isEqualTo(party.id());
        assertThat(periods.list()).extracting(PeriodDtos.PeriodResponse::endsOn)
                .containsExactly(LocalDate.of(2026, 10, 9), LocalDate.of(2026, 11, 30), null);

        assertThatThrownBy(() -> periods.delete(party.id()))
                .extracting("code").isEqualTo(ErrorCode.PERIOD_NOT_LATEST);
        periods.delete(later.id());
        periods.delete(party.id());

        // Started by mistake and removed: the points are back in one period, nothing was lost.
        assertThat(card(null).points()).isEqualTo(7);
    }

    // ---- helpers ----

    private void add(int amount, String reason) {
        pointsService.manualForStudent(mina.getId(), new ManualPointsRequest(ManualKind.ADD, amount, reason, null), adminId);
    }

    private StudentCard card(Long periodId) {
        entityManager.flush();
        return dashboard.student(mina.getId(), periodId).card();
    }

    private void backdate(Long transactionId, LocalDate day) {
        entityManager.flush();
        transactions.findById(transactionId).orElseThrow().setEffectiveDate(day);
        entityManager.flush();
    }
}
