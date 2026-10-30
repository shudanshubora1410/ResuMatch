package com.resumatch.ats;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "company_scores")
public class CompanyScoreEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "application_id", nullable = false)
    public Long applicationId;

    @Column(name = "general_score_id")
    public Long generalScoreId;

    @Column(name = "total_score", nullable = false)
    public double totalScore;

    @Column(name = "must_have_score", nullable = false) public double mustHaveScore;
    @Column(name = "nice_to_have_score", nullable = false) public double niceToHaveScore;
    @Column(name = "experience_score", nullable = false) public double experienceScore;
    @Column(name = "education_score", nullable = false) public double educationScore;
    @Column(name = "formatting_score", nullable = false) public double formattingScore;

    @Lob @Column(name = "matched_skills_json", columnDefinition = "LONGTEXT")
    public String matchedSkillsJson;

    @Lob @Column(name = "missing_skills_json", columnDefinition = "LONGTEXT")
    public String missingSkillsJson;

    @Lob @Column(name = "recommendations_json", columnDefinition = "LONGTEXT")
    public String recommendationsJson;

    @jakarta.persistence.Transient
    public java.util.Map<String, Object> getMatchedSkills() {
        return parseMap(matchedSkillsJson);
    }
    @jakarta.persistence.Transient
    public java.util.Map<String, Object> getMissingSkills() {
        return parseMap(missingSkillsJson);
    }
    @jakarta.persistence.Transient
    public java.util.List<String> getRecommendations() {
        if (recommendationsJson == null || recommendationsJson.isBlank()) return java.util.List.of();
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(recommendationsJson,
                    new com.fasterxml.jackson.core.type.TypeReference<>() {});
        } catch (Exception e) { return java.util.List.of(); }
    }
    private java.util.Map<String, Object> parseMap(String json) {
        if (json == null || json.isBlank()) return java.util.Map.of();
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(json,
                    new com.fasterxml.jackson.core.type.TypeReference<>() {});
        } catch (Exception e) { return java.util.Map.of(); }
    }

    @Lob @Column(name = "explanation_json", columnDefinition = "LONGTEXT")
    public String explanationJson;

    @Column(name = "computed_at", nullable = false)
    public Instant computedAt = Instant.now();
}
