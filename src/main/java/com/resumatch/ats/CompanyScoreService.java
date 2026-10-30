package com.resumatch.ats;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumatch.jobs.JobSkillEntity;
import com.resumatch.resume.ResumeExtractionEntity;
import com.resumatch.resume.ResumeFormatFlagEntity;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Computes company-specific ATS score (out of 100) using the recruiter's
 * weighted skill rows. Pure rule-based; produces explainable output.
 */
@Service
public class CompanyScoreService {

    private final ObjectMapper json = new ObjectMapper();

    public ScoreOutput compute(ResumeExtractionEntity ex,
                              List<JobSkillEntity> jobSkills,
                              List<ResumeFormatFlagEntity> flags,
                              AliasIndex.SkillScan resumeScan,
                              AliasIndex.SkillScan hypotheticalScan,
                              Double generalScore) {
        // merge hypothetical into resume scan for this run
        Set<Long> effectiveIds = new HashSet<>(resumeScan.skillIds);
        Set<String> effectiveNames = new HashSet<>(resumeScan.canonicalNames);
        if (hypotheticalScan != null) {
            effectiveIds.addAll(hypotheticalScan.skillIds);
            effectiveNames.addAll(hypotheticalScan.canonicalNames);
        }

        List<JobSkillEntity> must = jobSkills.stream()
                .filter(s -> s.importance == com.resumatch.common.SkillImportance.MUST_HAVE).toList();
        List<JobSkillEntity> nice = jobSkills.stream()
                .filter(s -> s.importance == com.resumatch.common.SkillImportance.NICE_TO_HAVE).toList();

        // ---- must-have scoring ----
        double mustTotalWeight = must.stream().map(s -> s.weight == null ? BigDecimal.ONE : s.weight)
                .reduce(BigDecimal.ZERO, BigDecimal::add).doubleValue();
        double mustEarned = 0.0;
        List<String> matchedMust = new ArrayList<>();
        List<String> missingMust = new ArrayList<>();
        for (JobSkillEntity s : must) {
            double w = s.weight == null ? 1.0 : s.weight.doubleValue();
            if (matches(s, effectiveIds, effectiveNames, ex.extractedText)) {
                mustEarned += w;
                matchedMust.add(s.rawSkillText);
            } else {
                missingMust.add(s.rawSkillText);
            }
        }
        double mustScore = mustTotalWeight <= 0
                ? AtsWeights.COMPANY_MUST_HAVE
                : (mustEarned / mustTotalWeight) * AtsWeights.COMPANY_MUST_HAVE;

        // ---- nice-to-have scoring ----
        double niceTotalWeight = nice.stream().map(s -> s.weight == null ? BigDecimal.ONE : s.weight)
                .reduce(BigDecimal.ZERO, BigDecimal::add).doubleValue();
        double niceEarned = 0.0;
        List<String> matchedNice = new ArrayList<>();
        List<String> missingNice = new ArrayList<>();
        for (JobSkillEntity s : nice) {
            double w = s.weight == null ? 1.0 : s.weight.doubleValue();
            if (matches(s, effectiveIds, effectiveNames, ex.extractedText)) {
                niceEarned += w;
                matchedNice.add(s.rawSkillText);
            } else {
                missingNice.add(s.rawSkillText);
            }
        }
        double niceScore = niceTotalWeight <= 0
                ? 0
                : (niceEarned / niceTotalWeight) * AtsWeights.COMPANY_NICE_TO_HAVE;

        // ---- experience ----
        double years = ex.experienceYears == null ? 0.0 : ex.experienceYears;
        double yMin = jobSkills.isEmpty() ? 0.0 : 0.0; // use job posting separately
        double expRatio = Math.min(years / 5.0, 1.0);
        double expScore = expRatio * AtsWeights.COMPANY_EXPERIENCE;

        // ---- education ----
        double eduScore = 0;
        if (ex.extractedText != null && ex.extractedText.toLowerCase().matches("(?s).*\\b(b\\.?tech|m\\.?tech|b\\.?e|m\\.?e|b\\.?sc|m\\.?sc|bachelor|master|phd|diploma)\\b.*")) {
            eduScore = AtsWeights.COMPANY_EDUCATION;
        } else if (ex.extractedText != null && ex.extractedText.toLowerCase().contains("education")) {
            eduScore = AtsWeights.COMPANY_EDUCATION * 0.6;
        }

        // ---- formatting multiplier ----
        long high = flags.stream().filter(f -> f.severity == com.resumatch.common.Severity.HIGH).count();
        long med = flags.stream().filter(f -> f.severity == com.resumatch.common.Severity.MEDIUM).count();
        double fmtScore = AtsWeights.COMPANY_FORMATTING;
        fmtScore -= high * 2.5;
        fmtScore -= med * 1.0;
        fmtScore = Math.max(0, fmtScore);
        if (generalScore != null) {
            // small additional nudge: if general score is poor, recruiter should know
            // we do not double count; just cap formatting contribution by general health
            if (generalScore < 40) fmtScore = Math.min(fmtScore, AtsWeights.COMPANY_FORMATTING * 0.5);
        }

        double total = mustScore + niceScore + expScore + eduScore + fmtScore;
        total = Math.max(0, Math.min(100, total));

        // ---- recommendations ordered by impact ----
        List<String> recs = new ArrayList<>();
        for (String m : missingMust) {
            recs.add("Critical: add " + m + " to skills or experience (must-have).");
        }
        for (String m : missingNice.subList(0, Math.min(3, missingNice.size()))) {
            recs.add("Nice to have: include " + m + " if you have it.");
        }
        if (high > 0) recs.add("Fix HIGH-severity formatting issues; some parsers may skip your resume.");
        if (eduScore < AtsWeights.COMPANY_EDUCATION) recs.add("List your degree explicitly under Education.");
        if (years < 1) recs.add("Highlight internships or substantial projects to strengthen experience signal.");
        if (recs.isEmpty()) recs.add("Excellent fit for this role based on the rules we checked.");

        Map<String, Object> explanation = new LinkedHashMap<>();
        explanation.put("mustHave", Map.of(
                "score", round(mustScore), "outOf", AtsWeights.COMPANY_MUST_HAVE,
                "earnedWeight", round(mustEarned), "totalWeight", round(mustTotalWeight),
                "matched", matchedMust, "missing", missingMust));
        explanation.put("niceToHave", Map.of(
                "score", round(niceScore), "outOf", AtsWeights.COMPANY_NICE_TO_HAVE,
                "earnedWeight", round(niceEarned), "totalWeight", round(niceTotalWeight),
                "matched", matchedNice, "missing", missingNice));
        explanation.put("experience", Map.of("score", round(expScore), "outOf", AtsWeights.COMPANY_EXPERIENCE,
                "estimatedYears", round(years)));
        explanation.put("education", Map.of("score", round(eduScore), "outOf", AtsWeights.COMPANY_EDUCATION));
        explanation.put("formatting", Map.of("score", round(fmtScore), "outOf", AtsWeights.COMPANY_FORMATTING,
                "highSeverityFlags", high, "mediumSeverityFlags", med));
        explanation.put("generalScoreReference", generalScore == null ? null : round(generalScore));
        explanation.put("recommendations", recs);

        ScoreOutput out = new ScoreOutput();
        out.total = round(total);
        out.mustHave = round(mustScore);
        out.niceToHave = round(niceScore);
        out.experience = round(expScore);
        out.education = round(eduScore);
        out.formatting = round(fmtScore);
        out.matchedSkills = matchedMust;
        out.matchedNice = matchedNice;
        out.missingSkills = missingMust;
        out.missingNice = missingNice;
        out.recommendations = recs;
        try {
            out.explanationJson = json.writeValueAsString(explanation);
            out.matchedSkillsJson = json.writeValueAsString(Map.of("mustHave", matchedMust, "niceToHave", matchedNice));
            out.missingSkillsJson = json.writeValueAsString(Map.of("mustHave", missingMust, "niceToHave", missingNice));
            out.recommendationsJson = json.writeValueAsString(recs);
        } catch (Exception e) {
            out.explanationJson = "{}";
        }
        return out;
    }

    private boolean matches(JobSkillEntity js, Set<Long> resumeSkillIds, Set<String> resumeCanonicalNames, String resumeText) {
        // 1. exact alias match via skillId
        if (js.skillId != null && resumeSkillIds.contains(js.skillId)) return true;
        // 2. canonical name token match
        String canonicalNorm = com.resumatch.ats.TextNormalizer.normalize(js.normalizedSkillText);
        if (!canonicalNorm.isBlank() && resumeCanonicalNames.stream()
                .anyMatch(n -> com.resumatch.ats.TextNormalizer.normalize(n).equals(canonicalNorm))) {
            return true;
        }
        // 3. raw text fallback: require each token of the canonical to appear in resume text
        if (resumeText == null || resumeText.isBlank()) return false;
        String text = " " + com.resumatch.ats.TextNormalizer.normalize(resumeText) + " ";
        for (String tok : canonicalNorm.split(" ")) {
            if (tok.length() < 2) continue;
            if (!text.contains(" " + tok + " ") && !text.contains(tok)) return false;
        }
        return !canonicalNorm.isBlank();
    }

    private static double round(double v) { return Math.round(v * 10.0) / 10.0; }

    public static class ScoreOutput {
        public double total, mustHave, niceToHave, experience, education, formatting;
        public List<String> matchedSkills, matchedNice, missingSkills, missingNice, recommendations;
        public String explanationJson, matchedSkillsJson, missingSkillsJson, recommendationsJson;
    }
}
