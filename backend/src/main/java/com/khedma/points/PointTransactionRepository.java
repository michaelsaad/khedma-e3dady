package com.khedma.points;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.khedma.attendance.AttendanceKind;

public interface PointTransactionRepository extends JpaRepository<PointTransaction, Long> {

    /** Attendance awards for this attendance row and kind that have not been reversed yet. */
    @Query("""
            select t from PointTransaction t
            where t.attendance.id = :attendanceId
              and t.attendanceKind = :kind
              and t.type = com.khedma.points.TransactionType.ATTENDANCE_AWARD
              and not exists (select r.id from PointTransaction r where r.reverses = t)
            """)
    List<PointTransaction> findActiveAttendanceAwards(Long attendanceId, AttendanceKind kind);

    /** Net manual points per student given on a meeting day, as rows of [studentId, sum]. */
    @Query("""
            select t.student.id, sum(t.amount) from PointTransaction t
            where t.meetingDate = :date
              and t.type in (com.khedma.points.TransactionType.MANUAL_ADD, com.khedma.points.TransactionType.MANUAL_DEDUCT)
            group by t.student.id
            """)
    List<Object[]> sumManualPointsByStudent(LocalDate date);

    /** Bonuses given to a student on a meeting day that have not been undone, oldest first. */
    @Query("""
            select t from PointTransaction t
            where t.student.id = :studentId
              and t.meetingDate = :date
              and t.type = com.khedma.points.TransactionType.MANUAL_ADD
              and not exists (select r.id from PointTransaction r where r.reverses = t)
            order by t.id
            """)
    List<PointTransaction> findActiveMeetingBonuses(Long studentId, LocalDate date);

    boolean existsByReversesId(Long reversesId);

    // ---- Point periods: sums over effective dates (two variants so no parameter is ever null) ----

    @Query("select t.student.id, sum(t.amount) from PointTransaction t where t.student is not null and t.effectiveDate >= :from group by t.student.id")
    List<Object[]> sumByStudentFrom(LocalDate from);

    @Query("select t.student.id, sum(t.amount) from PointTransaction t where t.student is not null and t.effectiveDate >= :from and t.effectiveDate < :to group by t.student.id")
    List<Object[]> sumByStudentBetween(LocalDate from, LocalDate to);

    @Query("select t.group.id, sum(t.amount) from PointTransaction t where t.group is not null and t.effectiveDate >= :from group by t.group.id")
    List<Object[]> sumByGroupFrom(LocalDate from);

    @Query("select t.group.id, sum(t.amount) from PointTransaction t where t.group is not null and t.effectiveDate >= :from and t.effectiveDate < :to group by t.group.id")
    List<Object[]> sumByGroupBetween(LocalDate from, LocalDate to);

    @Query("select sum(t.amount) from PointTransaction t where t.student.id = :studentId and t.effectiveDate >= :from")
    Long sumForStudentFrom(Long studentId, LocalDate from);

    @Query("select sum(t.amount) from PointTransaction t where t.student.id = :studentId and t.effectiveDate >= :from and t.effectiveDate < :to")
    Long sumForStudentBetween(Long studentId, LocalDate from, LocalDate to);

    @Query("select sum(t.amount) from PointTransaction t where t.group.id = :groupId and t.effectiveDate >= :from")
    Long sumForGroupFrom(Long groupId, LocalDate from);

    @Query("select sum(t.amount) from PointTransaction t where t.group.id = :groupId and t.effectiveDate >= :from and t.effectiveDate < :to")
    Long sumForGroupBetween(Long groupId, LocalDate from, LocalDate to);

    @EntityGraph(attributePaths = {"student", "group", "createdBy", "attendance"})
    Page<PointTransaction> findAllByStudentId(Long studentId, Pageable pageable);

    @EntityGraph(attributePaths = {"student", "group", "createdBy", "attendance"})
    Page<PointTransaction> findAllByGroupId(Long groupId, Pageable pageable);

    @EntityGraph(attributePaths = {"student", "group", "createdBy", "attendance"})
    Page<PointTransaction> findAllBy(Pageable pageable);
}
