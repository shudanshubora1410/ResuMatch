package com.resumatch.resume;

import com.resumatch.common.Severity;
import jakarta.persistence.*;

@Entity
@Table(name = "resume_format_flags")
public class ResumeFormatFlagEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "resume_version_id", nullable = false)
    public Long resumeVersionId;

    @Column(name = "flag_type", nullable = false, length = 80)
    public String flagType;

    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16)
    public Severity severity;

    @Column(name = "section_name", length = 80)
    public String sectionName;

    @Column(length = 500)
    public String message;
}
