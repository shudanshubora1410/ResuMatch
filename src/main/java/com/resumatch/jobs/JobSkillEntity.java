package com.resumatch.jobs;

import com.resumatch.common.SkillImportance;
import jakarta.persistence.*;

@Entity
@Table(name = "job_skills")
public class JobSkillEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "job_id", nullable = false)
    public Long jobId;

    @Column(name = "skill_id")
    public Long skillId;

    @Column(name = "raw_skill_text", nullable = false, length = 200)
    public String rawSkillText;

    @Column(name = "normalized_skill_text", nullable = false, length = 200)
    public String normalizedSkillText;

    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16)
    public SkillImportance importance = SkillImportance.MUST_HAVE;

    @Column(nullable = false, precision = 5, scale = 2)
    public java.math.BigDecimal weight = new java.math.BigDecimal("1.00");
}
