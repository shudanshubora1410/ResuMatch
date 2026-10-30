package com.resumatch.jobs;

import com.resumatch.common.JobStatus;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "job_postings")
public class JobPostingEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "recruiter_id", nullable = false)
    public Long recruiterId;

    @Column(name = "company_id")
    public Long companyId;

    @Column(nullable = false, length = 200)
    public String title;

    @Column(length = 4000)
    public String description;

    @Column(length = 100)
    public String location;

    @Column(name = "employment_type", length = 60)
    public String employmentType;

    @Column(name = "min_experience_years")
    public Integer minExperienceYears;

    @Column(name = "max_experience_years")
    public Integer maxExperienceYears;

    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16)
    public JobStatus status = JobStatus.OPEN;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt = Instant.now();
}
