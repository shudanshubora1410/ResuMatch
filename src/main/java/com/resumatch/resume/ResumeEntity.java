package com.resumatch.resume;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "resumes")
public class ResumeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "seeker_id", nullable = false)
    public Long seekerId;

    @Column(name = "active_version_id")
    public Long activeVersionId;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt = Instant.now();
}
