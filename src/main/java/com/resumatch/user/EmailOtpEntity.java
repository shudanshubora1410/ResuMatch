package com.resumatch.user;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "email_otps")
public class EmailOtpEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "user_id", nullable = false)
    public Long userId;

    @Column(nullable = false, length = 32)
    public String purpose; // LOGIN_2FA, PASSWORD_RESET, EMAIL_VERIFY

    @Column(name = "otp_hash", nullable = false, length = 200)
    public String otpHash;

    @Column(name = "expires_at", nullable = false)
    public Instant expiresAt;

    @Column(name = "consumed_at")
    public Instant consumedAt;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt = Instant.now();
}
