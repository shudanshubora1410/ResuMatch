package com.resumatch.user;

import jakarta.persistence.*;

@Entity
@Table(name = "recruiter_profiles")
public class RecruiterProfileEntity {
    @Id @Column(name = "user_id")
    public Long userId;

    @Column(name = "company_id")
    public Long companyId;

    @Column(name = "recruiter_name", length = 200)
    public String recruiterName;

    @Column(length = 100)
    public String designation;

    @Column(name = "profile_completion_pct", nullable = false)
    public int profileCompletionPct = 0;
}
