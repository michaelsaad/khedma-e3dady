package com.khedma.attendance;

import java.time.Instant;
import java.time.LocalDate;

import com.khedma.student.Student;
import com.khedma.user.AppUser;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

@Entity
@Table(name = "attendance")
@Getter
@Setter
@NoArgsConstructor
public class Attendance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Column(nullable = false)
    private LocalDate meetingDate;

    @Column(nullable = false)
    private boolean service;

    @Column(nullable = false)
    private boolean mass;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by_id")
    private AppUser updatedBy;

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    public boolean isPresent(AttendanceKind kind) {
        return kind == AttendanceKind.MASS ? mass : service;
    }
}
