package com.resumatch.resume;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumatch.ats.GeneralScoreEntity;
import com.resumatch.ats.GeneralScoreRepository;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ResumeComparisonService {

    private final ResumeVersionRepository versionRepo;
    private final ResumeExtractionRepository extractionRepo;
    private final ResumeFormatFlagRepository flagRepo;
    private final GeneralScoreRepository generalScoreRepo;
    private final ObjectMapper json = new ObjectMapper();

    public ResumeComparisonService(ResumeVersionRepository versionRepo,
                                  ResumeExtractionRepository extractionRepo,
                                  ResumeFormatFlagRepository flagRepo,
                                  GeneralScoreRepository generalScoreRepo) {
        this.versionRepo = versionRepo;
        this.extractionRepo = extractionRepo;
        this.flagRepo = flagRepo;
        this.generalScoreRepo = generalScoreRepo;
    }

    public Map<String, Object> compare(Long versionAId, Long versionBId) {
        ResumeVersionEntity a = versionRepo.findById(versionAId).orElseThrow();
        ResumeVersionEntity b = versionRepo.findById(versionBId).orElseThrow();
        if (!a.resumeId.equals(b.resumeId)) {
            throw new IllegalArgumentException("Versions must belong to the same resume");
        }
        ResumeExtractionEntity exA = extractionRepo.findFirstByResumeVersionIdOrderByCreatedAtDesc(versionAId).orElse(null);
        ResumeExtractionEntity exB = extractionRepo.findFirstByResumeVersionIdOrderByCreatedAtDesc(versionBId).orElse(null);
        GeneralScoreEntity gsA = generalScoreRepo.findFirstByResumeVersionIdOrderByScoredAtDesc(versionAId).orElse(null);
        GeneralScoreEntity gsB = generalScoreRepo.findFirstByResumeVersionIdOrderByScoredAtDesc(versionBId).orElse(null);

        Set<String> skA = parseStringSet(exA == null ? null : exA.skillsJson);
        Set<String> skB = parseStringSet(exB == null ? null : exB.skillsJson);

        Set<String> added = new TreeSet<>(skB);
        added.removeAll(skA);
        Set<String> removed = new TreeSet<>(skA);
        removed.removeAll(skB);
        Set<String> common = new TreeSet<>(skA);
        common.retainAll(skB);

        List<ResumeFormatFlagEntity> flagsA = flagRepo.findByResumeVersionId(versionAId);
        List<ResumeFormatFlagEntity> flagsB = flagRepo.findByResumeVersionId(versionBId);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("versionA", Map.of("id", a.id, "number", a.versionNumber, "label", a.versionLabel, "uploadedAt", a.uploadedAt));
        out.put("versionB", Map.of("id", b.id, "number", b.versionNumber, "label", b.versionLabel, "uploadedAt", b.uploadedAt));
        out.put("scoreA", gsA);
        out.put("scoreB", gsB);
        out.put("scoreDelta", gsA == null || gsB == null ? null : round(gsB.totalScore - gsA.totalScore));
        out.put("addedSkills", added);
        out.put("removedSkills", removed);
        out.put("commonSkills", common);
        out.put("flagsA", flagsA.size());
        out.put("flagsB", flagsB.size());
        out.put("flagDelta", flagsB.size() - flagsA.size());
        out.put("experienceA", exA == null ? 0 : exA.experienceYears);
        out.put("experienceB", exB == null ? 0 : exB.experienceYears);
        out.put("experienceDelta", round((exB == null ? 0 : exB.experienceYears) - (exA == null ? 0 : exA.experienceYears)));
        return out;
    }

    private Set<String> parseStringSet(String s) {
        if (s == null || s.isBlank()) return Set.of();
        try {
            return new HashSet<>(json.readValue(s, new TypeReference<List<String>>() {}));
        } catch (Exception e) { return Set.of(); }
    }

    private double round(double v) { return Math.round(v * 10.0) / 10.0; }
}
