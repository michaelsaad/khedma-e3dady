package com.khedma.followup;

import java.time.Instant;

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

/** A servant's report about how a student is doing. Never exposed to the student's own account. */
@Entity
@Table(name = "followups")
@Getter
@Setter
@NoArgsConstructor
public class Followup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id")
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "type_id")
    private FollowupType type;

    @Column(nullable = false)
    private String typeLabel;

    @Column(nullable = false)
    private String note = "";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id")
    private AppUser author;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FollowupStatus status = FollowupStatus.OPEN;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_by_id")
    private AppUser resolvedBy;

    @Column(nullable = false)
    private String resolvedNote = "";

    private Instant resolvedAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
