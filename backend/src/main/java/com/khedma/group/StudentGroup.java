package com.khedma.group;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "student_groups")
@Getter
@Setter
@NoArgsConstructor
public class StudentGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    /** Cached sum of this group's point transactions. Only change it through PointsService. */
    @Column(nullable = false)
    private int pointsBalance;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
