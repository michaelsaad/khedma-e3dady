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

    @EntityGraph(attributePaths = {"student", "group", "createdBy", "attendance"})
    Page<PointTransaction> findAllByStudentId(Long studentId, Pageable pageable);

    @EntityGraph(attributePaths = {"student", "group", "createdBy", "attendance"})
    Page<PointTransaction> findAllByGroupId(Long groupId, Pageable pageable);

    @EntityGraph(attributePaths = {"student", "group", "createdBy", "attendance"})
    Page<PointTransaction> findAllBy(Pageable pageable);
}
