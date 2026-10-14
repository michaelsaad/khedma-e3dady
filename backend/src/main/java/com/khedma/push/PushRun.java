package com.khedma.push;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "push_runs")
@Getter
@Setter
@NoArgsConstructor
public class PushRun {

    @Id
    private String runKey;

    @Column(nullable = false, insertable = false, updatable = false)
    private Instant ranAt;
}
