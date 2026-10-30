package com.resumatch.ats;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumatch.resume.ResumeExtractionEntity;
import com.resumatch.resume.ResumeFormatFlagEntity;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Computes the general ATS score (out of 100) with sub-scores and an
 * explainable JSON payload. Pure rule-based; no AI/ML.
 */
@Service
public class GeneralScoreService {

    private final ObjectMapper json = new ObjectMapper();

    public ScoreOutput compute(ResumeExtractionEntity ex, List<ResumeFormatFlagEntity> flags, AliasIndex.SkillScan scan) {
        String text = ex.extractedText == null ? "" : ex.extractedText;
        SectionDetector.SectionMap sm = SectionDetector.detect(text);

        // ---- formatting score ----
        double formatting = computeFormattingScore(flags, sm, text);

        // ---- completeness (contact quality) ----
        double completeness = computeCompletenessScore(sm, ex);

        // ---- structure (sections) ----
        double structure = computeStructureScore(sm);

        // ---- skills (coverage, distinct count) ----
        double skills = computeSkillsScore(scan, sm);

        // ---- experience (years + projects) ----
        double years = ex.experienceYears == null ? 0.0 : ex.experienceYears;
        double experience = computeExperienceScore(years, sm);

        double total = formatting + completeness + structure + skills + experience;
        total = Math.max(0, Math.min(100, total));

        Map<String, Object> explanation = new LinkedHashMap<>();
        explanation.put("formatting", Map.of(
                "score", round(formatting), "outOf", AtsWeights.GENERAL_FORMATTING,
                "notes", formattingNotes(flags, sm, text)));
        explanation.put("completeness", Map.of(
                "score", round(completeness), "outOf", AtsWeights.GENERAL_COMPLETENESS,
                "notes", completenessNotes(sm, ex)));
        explanation.put("structure", Map.of(
                "score", round(structure), "outOf", AtsWeights.GENERAL_STRUCTURE,
                "sections", sm.found));
        explanation.put("skills", Map.of(
                "score", round(skills), "outOf", AtsWeights.GENERAL_SKILLS,
                "matchedSkillCount", scan.skillIds.size(),
                "matchedSample", new ArrayList<>(scan.canonicalNames).stream().limit(8).toList()));
        explanation.put("experience", Map.of(
                "score", round(experience), "outOf", AtsWeights.GENERAL_EXPERIENCE,
                "estimatedYears", round(years),
                "hasProjects", sm.found.contains("projects")));

        List<String> recommendations = new ArrayList<>();
        if (flags.stream().anyMatch(f -> f.severity == com.resumatch.common.Severity.HIGH))
            recommendations.add("Fix HIGH severity formatting flags first; recruiters and ATS systems may skip your resume.");
        if (!sm.hasEmail) recommendations.add("Add a professional email address in the contact section.");
        if (!sm.hasPhone) recommendations.add("Add a reachable phone number.");
        if (!sm.found.contains("skills")) recommendations.add("Add a clear 'Skills' section listing your strongest technical skills.");
        if (scan.size() < 5) recommendations.add("Add more recognized technical skills; aim for at least 8-12 in your domain.");
        if (years < 1 && sm.found.contains("experience"))
            recommendations.add("Make experience entries more concrete with role, company and dates.");
        if (!sm.found.contains("projects")) recommendations.add("Add a Projects section to demonstrate applied skills.");
        if (recommendations.isEmpty()) recommendations.add("Resume looks strong. Keep updating it as you grow.");

        explanation.put("topRecommendations", recommendations);

        ScoreOutput out = new ScoreOutput();
        out.total = round(total);
        out.formatting = round(formatting);
        out.completeness = round(completeness);
        out.structure = round(structure);
        out.skills = round(skills);
        out.experience = round(experience);
        try {
            out.explanationJson = json.writeValueAsString(explanation);
        } catch (Exception e) {
            out.explanationJson = "{}";
        }
        out.recommendations = recommendations;
        return out;
    }

    private double computeFormattingScore(List<ResumeFormatFlagEntity> flags, SectionDetector.SectionMap sm, String text) {
        double score = AtsWeights.GENERAL_FORMATTING;
        for (ResumeFormatFlagEntity f : flags) {
            switch (f.severity) {
                case HIGH -> score -= 5;
                case MEDIUM -> score -= 2.5;
                case LOW -> score -= 1;
            }
        }
        return clamp(score, 0, AtsWeights.GENERAL_FORMATTING);
    }

    private double computeCompletenessScore(SectionDetector.SectionMap sm, ResumeExtractionEntity ex) {
        double score = 0;
        if (sm.hasEmail) score += 5;
        if (sm.hasPhone) score += 4;
        if (ex.extractedName != null && !ex.extractedName.isBlank()) score += 3;
        if (sm.found.contains("summary") || sm.found.contains("contact")) score += 3;
        return clamp(score, 0, AtsWeights.GENERAL_COMPLETENESS);
    }

    private double computeStructureScore(SectionDetector.SectionMap sm) {
        // 5 points for each major section
        int count = 0;
        for (String s : List.of("summary", "skills", "experience", "education", "projects", "certifications")) {
            if (sm.found.contains(s)) count++;
        }
        double per = AtsWeights.GENERAL_STRUCTURE / 6.0;
        return clamp(count * per, 0, AtsWeights.GENERAL_STRUCTURE);
    }

    private double computeSkillsScore(AliasIndex.SkillScan scan, SectionDetector.SectionMap sm) {
        double n = scan.size();
        if (!sm.found.contains("skills")) n *= 0.6; // penalty when no clear section
        // cap at 14 distinct skills for max
        double score = Math.min(n, 14) / 14.0 * AtsWeights.GENERAL_SKILLS;
        return clamp(score, 0, AtsWeights.GENERAL_SKILLS);
    }

    private double computeExperienceScore(double years, SectionDetector.SectionMap sm) {
        double y = Math.min(years, 8.0) / 8.0; // 0..1
        double base = y * (AtsWeights.GENERAL_EXPERIENCE * 0.7);
        if (sm.found.contains("projects")) base += AtsWeights.GENERAL_EXPERIENCE * 0.3;
        else if (sm.found.contains("experience")) base += AtsWeights.GENERAL_EXPERIENCE * 0.15;
        return clamp(base, 0, AtsWeights.GENERAL_EXPERIENCE);
    }

    private List<String> formattingNotes(List<ResumeFormatFlagEntity> flags, SectionDetector.SectionMap sm, String text) {
        List<String> out = new ArrayList<>();
        for (ResumeFormatFlagEntity f : flags) {
            out.add("[" + f.severity + "] " + f.sectionName + " — " + f.message);
        }
        if (out.isEmpty()) out.add("No formatting issues detected.");
        return out;
    }

    private List<String> completenessNotes(SectionDetector.SectionMap sm, ResumeExtractionEntity ex) {
        List<String> out = new ArrayList<>();
        out.add("Email: " + (sm.hasEmail ? "present" : "missing"));
        out.add("Phone: " + (sm.hasPhone ? "present" : "missing"));
        out.add("Name: " + (ex.extractedName == null ? "missing" : ex.extractedName));
        return out;
    }

    private static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }
    private static double round(double v) { return Math.round(v * 10.0) / 10.0; }

    public static class ScoreOutput {
        public double total, formatting, completeness, structure, skills, experience;
        public String explanationJson;
        public List<String> recommendations;
    }
}
