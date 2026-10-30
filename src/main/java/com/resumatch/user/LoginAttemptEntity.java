package com.resumatch.user;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "login_attempts")
public class LoginAttemptEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "identifier_type", nullable = false, length = 16)
    public String identifierType; // EMAIL or NAME

    @Column(name = "identifier_value", nullable = false, length = 200)
    public String identifierValue;

    @Column(name = "ip_address", length = 64)
    public String ipAddress;

    @Column(nullable = false)
    public boolean success;

    @Column(name = "attempted_at", nullable = false)
    public Instant attemptedAt = Instant.now();
}
