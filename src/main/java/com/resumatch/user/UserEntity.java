package com.resumatch.user;

import com.resumatch.common.AccountStatus;
import com.resumatch.common.Role;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "users", uniqueConstraints = {
        @UniqueConstraint(columnNames = "email"),
        @UniqueConstraint(columnNames = "phone")
})
public class UserEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16)
    public Role role;

    @Column(name = "display_name", nullable = false, length = 120)
    public String displayName;

    @Column(nullable = false, length = 180)
    public String email;

    @Column(length = 32)
    public String phone;

    @Column(name = "password_hash", nullable = false, length = 200)
    public String passwordHash;

    @Column(name = "email_verified", nullable = false)
    public boolean emailVerified = false;

    @Column(name = "two_factor_enabled", nullable = false)
    public boolean twoFactorEnabled = false;

    @Enumerated(EnumType.STRING) @Column(name = "account_status", nullable = false, length = 16)
    public AccountStatus accountStatus = AccountStatus.ACTIVE;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt = Instant.now();

    @Column(name = "last_login_at")
    public Instant lastLoginAt;
}
