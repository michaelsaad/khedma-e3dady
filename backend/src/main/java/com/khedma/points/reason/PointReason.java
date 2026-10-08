package com.khedma.points.reason;

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
@Table(name = "point_reasons")
@Getter
@Setter
@NoArgsConstructor
public class PointReason {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String label;

    private Integer defaultAmount;

    /** How many manual transactions used this exact label; the list is sorted by it. */
    @Column(nullable = false)
    private int usageCount;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
