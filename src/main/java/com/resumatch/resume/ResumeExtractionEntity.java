package com.resumatch.resume;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "resume_extractions")
public class ResumeExtractionEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "resume_version_id", nullable = false)
    public Long resumeVersionId;

    @Column(name = "extracted_name", length = 200)
    public String extractedName;

    @Column(name = "extracted_email", length = 200)
    public String extractedEmail;

    @Column(name = "extracted_phone", length = 40)
    public String extractedPhone;

    @Lob @Column(name = "extracted_text", columnDefinition = "LONGTEXT")
    public String extractedText;

    @Lob @Column(name = "skills_json", columnDefinition = "LONGTEXT")
    public String skillsJson;

    @Lob @Column(name = "sections_json", columnDefinition = "LONGTEXT")
    public String sectionsJson;

    @Column(name = "experience_years")
    public Double experienceYears;

    @Column(name = "parsing_quality_score")
    public Double parsingQualityScore;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt = Instant.now();
}
