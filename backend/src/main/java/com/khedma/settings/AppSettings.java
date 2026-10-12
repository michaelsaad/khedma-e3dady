package com.khedma.settings;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;

import com.khedma.user.AppUser;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Single-row table (id = 1) edited from the App Settings page. */
@Entity
@Table(name = "app_settings")
@Getter
@Setter
@NoArgsConstructor
public class AppSettings {

    public static final int SINGLETON_ID = 1;

    @Id
    private Integer id;

    @Column(nullable = false)
    private int massPoints;

    @Column(nullable = false)
    private int servicePoints;

    @Column(nullable = false)
    private LocalDate seasonStart;

    @Column(nullable = false)
    private LocalDate seasonEnd;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DayOfWeek meetingDay;

    /** Attendance on meetings before this date never awards points (e.g. imported history). */
    @Column(nullable = false)
    private LocalDate pointsStartDate;

    public boolean awardsPointsOn(LocalDate meetingDate) {
        return !meetingDate.isBefore(pointsStartDate);
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by_id")
    private AppUser updatedBy;

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();
}
