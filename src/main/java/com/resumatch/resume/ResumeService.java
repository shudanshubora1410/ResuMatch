package com.resumatch.resume;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumatch.ats.*;
import com.resumatch.parsing.ResumeParser;
import com.resumatch.skills.AliasIndexService;
import com.resumatch.user.UserEntity;
import com.resumatch.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@Service
public class ResumeService {

    private final ResumeRepository resumeRepo;
    private final ResumeVersionRepository versionRepo;
    private final ResumeExtractionRepository extractionRepo;
    private final ResumeFormatFlagRepository flagRepo;
    private final GeneralScoreRepository generalScoreRepo;
    private final ResumeParser parser;
    private final AliasIndexService aliasIndexService;
    private final GeneralScoreService generalScoreService;
    private final UserRepository userRepo;
    private final ObjectMapper json = new ObjectMapper();

    @Value("${resumatch.storage.path}")
    private String storagePath;

    public ResumeService(ResumeRepository resumeRepo,
                         ResumeVersionRepository versionRepo,
                         ResumeExtractionRepository extractionRepo,
                         ResumeFormatFlagRepository flagRepo,
                         GeneralScoreRepository generalScoreRepo,
                         ResumeParser parser,
                         AliasIndexService aliasIndexService,
                         GeneralScoreService generalScoreService,
                         UserRepository userRepo) {
        this.resumeRepo = resumeRepo;
        this.versionRepo = versionRepo;
        this.extractionRepo = extractionRepo;
        this.flagRepo = flagRepo;
        this.generalScoreRepo = generalScoreRepo;
        this.parser = parser;
        this.aliasIndexService = aliasIndexService;
        this.generalScoreService = generalScoreService;
        this.userRepo = userRepo;
    }

    @Transactional
    public UploadResult upload(UserEntity user, MultipartFile file, String versionLabel) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("File is required");
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        if (!(name.endsWith(".pdf") || name.endsWith(".docx") || name.endsWith(".txt"))) {
            throw new IllegalArgumentException("Only PDF, DOCX, or TXT are supported");
        }
        long max = 10L * 1024 * 1024;
        if (file.getSize() > max) throw new IllegalArgumentException("File too large (max 10MB)");

        ResumeEntity resume = resumeRepo.findFirstBySeekerId(user.id).orElseGet(() -> {
            ResumeEntity r = new ResumeEntity();
            r.seekerId = user.id;
            return resumeRepo.save(r);
        });

        int nextVersion = versionRepo.findByResumeIdOrderByVersionNumberDesc(resume.id).stream()
                .findFirst().map(v -> v.versionNumber + 1).orElse(1);

        Path dir = Paths.get(storagePath, "resumes", String.valueOf(user.id));
        try { Files.createDirectories(dir); } catch (Exception e) { throw new RuntimeException(e); }
        String safeName = System.currentTimeMillis() + "_" + name.replaceAll("[^a-zA-Z0-9._-]", "_");
        Path filePath = dir.resolve(safeName);
        try { file.transferTo(filePath.toFile()); } catch (Exception e) {
            throw new RuntimeException("Failed to store file: " + e.getMessage());
        }

        ResumeVersionEntity ver = new ResumeVersionEntity();
        ver.resumeId = resume.id;
        ver.versionNumber = nextVersion;
        ver.versionLabel = (versionLabel == null || versionLabel.isBlank()) ? "Version " + nextVersion : versionLabel;
        ver.originalFileName = file.getOriginalFilename();
        ver.storagePath = filePath.toString();
        ver.mimeType = file.getContentType();
        ver.fileSize = file.getSize();
        ver = versionRepo.save(ver);

        resume.activeVersionId = ver.id;
        resumeRepo.save(resume);

        // extract
        ResumeParser.ParsedResult parsed = parser.parse(filePath, ver.mimeType, ver.originalFileName);
        if (parsed.error != null) {
            throw new IllegalArgumentException(parsed.error);
        }
        String text = parsed.text == null ? "" : parsed.text;

        ResumeExtractionEntity ex = new ResumeExtractionEntity();
        ex.resumeVersionId = ver.id;
        ex.extractedText = text;
        ex.extractedName = TextNormalizer.extractName(text).orElse(user.displayName);
        ex.extractedEmail = TextNormalizer.extractEmail(text).orElse(user.email);
        ex.extractedPhone = TextNormalizer.extractPhone(text).orElse(user.phone);
        ex.experienceYears = TextNormalizer.estimateYears(text);
        ex.parsingQualityScore = parsed.qualityScore;

        SectionDetector.SectionMap sm = SectionDetector.detect(text);
        AliasIndex.SkillScan scan = aliasIndexService.get().scan(text);
        ex.skillsJson = toJson(scan.canonicalNames);
        ex.sectionsJson = toJson(sm.found);
        ex = extractionRepo.save(ex);

        // format flags
        List<ResumeFormatFlagEntity> flags = FormattingChecker.check(ver.id, text, sm);
        if (!flags.isEmpty()) flagRepo.saveAll(flags);

        // general score
        GeneralScoreService.ScoreOutput out = generalScoreService.compute(ex, flags, scan);
        GeneralScoreEntity gs = new GeneralScoreEntity();
        gs.resumeVersionId = ver.id;
        gs.totalScore = out.total;
        gs.formattingScore = out.formatting;
        gs.completenessScore = out.completeness;
        gs.structureScore = out.structure;
        gs.skillsScore = out.skills;
        gs.experienceScore = out.experience;
        gs.explanationJson = out.explanationJson;
        gs = generalScoreRepo.save(gs);

        UploadResult r = new UploadResult();
        r.versionId = ver.id;
        r.versionNumber = ver.versionNumber;
        r.versionLabel = ver.versionLabel;
        r.extraction = ex;
        r.flags = flags;
        r.skillScan = scan;
        r.score = out;
        r.generalScoreId = gs.id;
        return r;
    }

    public List<ResumeVersionEntity> listVersions(Long seekerId) {
        ResumeEntity resume = resumeRepo.findFirstBySeekerId(seekerId).orElse(null);
        if (resume == null) return List.of();
        return versionRepo.findByResumeIdOrderByVersionNumberDesc(resume.id);
    }

    public GeneralScoreEntity latestScoreForVersion(Long versionId) {
        return generalScoreRepo.findFirstByResumeVersionIdOrderByScoredAtDesc(versionId).orElse(null);
    }

    public List<GeneralScoreEntity> historyForSeeker(Long seekerId) {
        ResumeEntity resume = resumeRepo.findFirstBySeekerId(seekerId).orElse(null);
        if (resume == null) return List.of();
        List<Long> versionIds = versionRepo.findByResumeIdOrderByVersionNumberDesc(resume.id)
                .stream().map(v -> v.id).toList();
        if (versionIds.isEmpty()) return List.of();
        return generalScoreRepo.findByResumeVersionIdInOrderByScoredAtAsc(versionIds);
    }

    private String toJson(Object o) {
        try { return json.writeValueAsString(o); } catch (Exception e) { return "{}"; }
    }

    public static class UploadResult {
        public Long versionId;
        public int versionNumber;
        public String versionLabel;
        public ResumeExtractionEntity extraction;
        public List<ResumeFormatFlagEntity> flags;
        public AliasIndex.SkillScan skillScan;
        public GeneralScoreService.ScoreOutput score;
        public Long generalScoreId;
    }
}
