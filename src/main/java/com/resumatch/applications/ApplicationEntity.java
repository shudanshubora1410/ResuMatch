package com.resumatch.applications;

import com.resumatch.common.ApplicationStatus;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "applications", uniqueConstraints = @UniqueConstraint(columnNames = {"job_id", "seeker_id"}))
public class ApplicationEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "job_id", nullable = false)
    public Long jobId;

    @Column(name = "seeker_id", nullable = false)
    public Long seekerId;

    @Column(name = "resume_version_id", nullable = false)
    public Long resumeVersionId;

    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16)
    public ApplicationStatus status = ApplicationStatus.APPLIED;

    @Column(name = "applied_at", nullable = false)
    public Instant appliedAt = Instant.now();

    @Column(name = "status_updated_at", nullable = false)
    public Instant statusUpdatedAt = Instant.now();
}
