package com.resumatch.user;

import jakarta.persistence.*;

@Entity
@Table(name = "seeker_profiles")
public class SeekerProfileEntity {
    @Id @Column(name = "user_id")
    public Long userId;

    @Column(length = 200)
    public String headline;

    @Column(length = 100)
    public String location;

    @Column(name = "targeted_skills_text", length = 2000)
    public String targetedSkillsText;

    @Column(name = "profile_completion_pct", nullable = false)
    public int profileCompletionPct = 0;

    @Column(length = 2000)
    public String bio;
}
