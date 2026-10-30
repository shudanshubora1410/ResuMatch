package com.resumatch.applications;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "application_status_history")
public class ApplicationStatusHistoryEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "application_id", nullable = false)
    public Long applicationId;

    @Column(name = "old_status", length = 16)
    public String oldStatus;

    @Column(name = "new_status", length = 16)
    public String newStatus;

    @Column(name = "changed_by_user_id", nullable = false)
    public Long changedByUserId;

    @Column(length = 1000)
    public String notes;

    @Column(name = "changed_at", nullable = false)
    public Instant changedAt = Instant.now();
}
