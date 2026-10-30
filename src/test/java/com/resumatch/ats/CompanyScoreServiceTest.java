package com.resumatch.ats;

import com.resumatch.common.SkillImportance;
import com.resumatch.jobs.JobSkillEntity;
import com.resumatch.resume.ResumeExtractionEntity;
import com.resumatch.resume.ResumeFormatFlagEntity;
import com.resumatch.common.Severity;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CompanyScoreServiceTest {

    private final CompanyScoreService service = new CompanyScoreService();

    @Test
    void perfectMatchYieldsFullMustHaveScore() {
        ResumeExtractionEntity ex = new ResumeExtractionEntity();
        ex.extractedText = "I have Java, Spring Boot and MySQL skills. Education: B.Tech in CS.";
        ex.experienceYears = 3.0;
        JobSkillEntity j1 = skill("Java", SkillImportance.MUST_HAVE, "10");
        JobSkillEntity j2 = skill("Spring Boot", SkillImportance.MUST_HAVE, "8");
        JobSkillEntity j3 = skill("MySQL", SkillImportance.MUST_HAVE, "6");

        AliasIndex index = new AliasIndex();
        index.load(List.of(
                new AliasIndex.AliasRow(1L, "Java", "java"),
                new AliasIndex.AliasRow(2L, "Spring Boot", "spring boot"),
                new AliasIndex.AliasRow(3L, "MySQL", "mysql")));
        AliasIndex.SkillScan scan = index.scan(ex.extractedText);

        CompanyScoreService.ScoreOutput out = service.compute(
                ex, List.of(j1, j2, j3), List.of(), scan, null, 100.0);

        assertEquals(50.0, out.mustHave, 0.1);
        assertEquals(0.0, out.niceToHave, 0.1);
        assertTrue(out.missingSkills.isEmpty());
        assertTrue(out.total >= 70);
    }

    @Test
    void missingMustHaveReducesScore() {
        ResumeExtractionEntity ex = new ResumeExtractionEntity();
        ex.extractedText = "I know Java. b.tech computer science";
        ex.experienceYears = 0.5;
        JobSkillEntity j1 = skill("Java", SkillImportance.MUST_HAVE, "10");
        JobSkillEntity j2 = skill("Spring Boot", SkillImportance.MUST_HAVE, "8");

        AliasIndex index = new AliasIndex();
        index.load(List.of(
                new AliasIndex.AliasRow(1L, "Java", "java"),
                new AliasIndex.AliasRow(2L, "Spring Boot", "spring boot")));
        AliasIndex.SkillScan scan = index.scan(ex.extractedText);

        CompanyScoreService.ScoreOutput out = service.compute(
                ex, List.of(j1, j2), List.of(), scan, null, 50.0);

        assertEquals(50.0 * 10.0 / 18.0, out.mustHave, 0.5);
        assertEquals(1, out.missingSkills.size());
        assertTrue(out.missingSkills.contains("Spring Boot"));
    }

    @Test
    void hypotheticalSkillsAffectScore() {
        ResumeExtractionEntity ex = new ResumeExtractionEntity();
        ex.extractedText = "I know Java.";
        ex.experienceYears = 0.0;
        JobSkillEntity j1 = skill("Java", SkillImportance.MUST_HAVE, "10");
        JobSkillEntity j2 = skill("Docker", SkillImportance.NICE_TO_HAVE, "5");

        AliasIndex index = new AliasIndex();
        index.load(List.of(
                new AliasIndex.AliasRow(1L, "Java", "java"),
                new AliasIndex.AliasRow(2L, "Docker", "docker")));
        AliasIndex.SkillScan scan = index.scan(ex.extractedText);
        AliasIndex.SkillScan hypo = index.scanHypothetical(List.of("Docker"));

        CompanyScoreService.ScoreOutput with = service.compute(
                ex, List.of(j1, j2), List.of(), scan, hypo, 60.0);
        CompanyScoreService.ScoreOutput without = service.compute(
                ex, List.of(j1, j2), List.of(), scan, null, 60.0);

        assertTrue(with.total > without.total);
    }

    @Test
    void formattingFlagsReduceFormattingScore() {
        ResumeExtractionEntity ex = new ResumeExtractionEntity();
        ex.extractedText = "hello";
        ex.experienceYears = 0.0;
        JobSkillEntity j1 = skill("Java", SkillImportance.MUST_HAVE, "10");
        ResumeFormatFlagEntity f = new ResumeFormatFlagEntity();
        f.severity = Severity.HIGH;
        f.message = "Image-heavy PDF";

        AliasIndex index = new AliasIndex();
        index.load(List.of(new AliasIndex.AliasRow(1L, "Java", "java")));
        AliasIndex.SkillScan scan = index.scan("");

        CompanyScoreService.ScoreOutput out = service.compute(
                ex, List.of(j1), List.of(f), scan, null, 30.0);
        assertTrue(out.formatting < 10.0);
    }

    private JobSkillEntity skill(String name, SkillImportance imp, String w) {
        JobSkillEntity j = new JobSkillEntity();
        j.rawSkillText = name;
        j.normalizedSkillText = com.resumatch.ats.TextNormalizer.skillKey(name);
        j.importance = imp;
        j.weight = new BigDecimal(w);
        return j;
    }
}
