package com.khedma.points.period;

import java.time.Instant;
import java.time.LocalDate;

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

/** A stretch of time whose points are counted on their own, e.g. "after the November party". */
@Entity
@Table(name = "point_periods")
@Getter
@Setter
@NoArgsConstructor
public class PointPeriod {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    /** Runs until the next period's start. */
    @Column(nullable = false, unique = true)
    private LocalDate startsOn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id")
    private AppUser createdBy;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
