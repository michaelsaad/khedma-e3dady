package com.khedma.followup;

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

/** An entry in the editable list a servant picks from when writing a follow-up (تعبان، شقاوة…). */
@Entity
@Table(name = "followup_types")
@Getter
@Setter
@NoArgsConstructor
public class FollowupType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String label;

    private int sortOrder;

    /** Inactive types stay on old reports but can't be picked for new ones. */
    private boolean active = true;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
