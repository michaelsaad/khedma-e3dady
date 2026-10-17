package com.khedma.auth;

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

/** A browser that confirmed an emailed code, so email/password sign-ins from it skip the second step. */
@Entity
@Table(name = "trusted_devices")
@Getter
@Setter
@NoArgsConstructor
public class TrustedDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    /** SHA-256 of the random token the browser keeps; the token itself is never stored. */
    @Column(nullable = false, unique = true)
    private String tokenHash;

    private String userAgent;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant lastUsedAt;

    @Column(nullable = false)
    private Instant expiresAt;
}
