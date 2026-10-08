package com.khedma.points;

import java.time.Instant;

import com.khedma.attendance.Attendance;
import com.khedma.attendance.AttendanceKind;
import com.khedma.group.StudentGroup;
import com.khedma.student.Student;
import com.khedma.user.AppUser;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Immutable ledger entry. Exactly one of {@code student} / {@code group} is set. */
@Entity
@Table(name = "point_transactions")
@Getter
@Setter
@NoArgsConstructor
public class PointTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id")
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    private StudentGroup group;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    /** Signed delta applied to the balance. */
    @Column(nullable = false)
    private int amount;

    @Column(nullable = false)
    private int balanceAfter;

    @Column(nullable = false)
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attendance_id")
    private Attendance attendance;

    @Enumerated(EnumType.STRING)
    private AttendanceKind attendanceKind;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reverses_id")
    private PointTransaction reverses;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id")
    private AppUser createdBy;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
