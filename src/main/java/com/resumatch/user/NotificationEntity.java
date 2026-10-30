package com.resumatch.user;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "notifications")
public class NotificationEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "user_id", nullable = false)
    public Long userId;

    @Column(nullable = false, length = 32)
    public String type;

    @Column(nullable = false, length = 16)
    public String channel; // EMAIL, IN_APP

    @Column(length = 200)
    public String subject;

    @Column(length = 4000)
    public String body;

    @Column(name = "is_read", nullable = false)
    public boolean isRead = false;

    @Column(name = "send_status", nullable = false, length = 16)
    public String sendStatus = "PENDING";

    @Column(name = "created_at", nullable = false)
    public Instant createdAt = Instant.now();

    @Column(name = "sent_at")
    public Instant sentAt;
}
