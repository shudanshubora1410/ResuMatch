package com.resumatch.ats;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "general_scores")
public class GeneralScoreEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "resume_version_id", nullable = false)
    public Long resumeVersionId;

    @Column(name = "total_score", nullable = false)
    public double totalScore;

    @Column(name = "formatting_score", nullable = false) public double formattingScore;
    @Column(name = "completeness_score", nullable = false) public double completenessScore;
    @Column(name = "structure_score", nullable = false) public double structureScore;
    @Column(name = "skills_score", nullable = false) public double skillsScore;
    @Column(name = "experience_score", nullable = false) public double experienceScore;

    @Lob @Column(name = "explanation_json", columnDefinition = "LONGTEXT")
    public String explanationJson;

    @Column(name = "scored_at", nullable = false)
    public Instant scoredAt = Instant.now();
}
