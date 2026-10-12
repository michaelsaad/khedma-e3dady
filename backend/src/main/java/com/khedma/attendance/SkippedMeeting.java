package com.khedma.attendance;

import java.time.Instant;
import java.time.LocalDate;

import com.khedma.user.AppUser;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A meeting that didn't happen: no attendance points, excluded from statistics. */
@Entity
@Table(name = "skipped_meetings")
@Getter
@Setter
@NoArgsConstructor
public class SkippedMeeting {

    @Id
    private LocalDate meetingDate;

    @Column(nullable = false)
    private String reason = "";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id")
    private AppUser createdBy;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
