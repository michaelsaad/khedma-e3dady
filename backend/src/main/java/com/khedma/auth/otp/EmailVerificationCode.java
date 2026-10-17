package com.khedma.auth.otp;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The account's current one-time code, plus counters that survive new codes: how many codes were
 * sent in the last day and how many wrong guesses were made in the last hour.
 */
@Entity
@Table(name = "email_verification_codes")
@Getter
@Setter
@NoArgsConstructor
public class EmailVerificationCode {

    @Id
    private Long userId;

    @Column(nullable = false)
    private String codeHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CodePurpose purpose = CodePurpose.VERIFY_EMAIL;

    @Column(nullable = false)
    private Instant expiresAt;

    /** Wrong guesses against the current code. */
    @Column(nullable = false)
    private int attempts;

    @Column(nullable = false)
    private Instant lastSentAt;

    private Instant sendWindowStart;

    @Column(nullable = false)
    private int sendsInWindow;

    private Instant failWindowStart;

    @Column(nullable = false)
    private int failuresInWindow;
}
