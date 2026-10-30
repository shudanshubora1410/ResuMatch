package com.resumatch.resume;

import com.resumatch.ats.GeneralScoreEntity;
import com.resumatch.ats.GeneralScoreRepository;
import com.resumatch.auth.AuthService;
import com.resumatch.common.ApiResponse;
import com.resumatch.common.AuthContext;
import com.resumatch.user.UserEntity;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@RestController
@RequestMapping("/api/seeker/resumes")
public class ResumeController {

    private final ResumeService resumeService;
    private final AuthService authService;
    private final ResumeExtractionRepository extractionRepo;
    private final ResumeFormatFlagRepository flagRepo;
    private final GeneralScoreRepository scoreRepo;
    private final ResumeComparisonService comparisonService;

    public ResumeController(ResumeService resumeService,
                            AuthService authService,
                            ResumeExtractionRepository extractionRepo,
                            ResumeFormatFlagRepository flagRepo,
                            GeneralScoreRepository scoreRepo,
                            ResumeComparisonService comparisonService) {
        this.resumeService = resumeService;
        this.authService = authService;
        this.extractionRepo = extractionRepo;
        this.flagRepo = flagRepo;
        this.scoreRepo = scoreRepo;
        this.comparisonService = comparisonService;
    }

    @PostMapping("/upload")
    public ApiResponse<Map<String, Object>> upload(@RequestParam("file") MultipartFile file,
                                                   @RequestParam(value = "versionLabel", required = false) String label,
                                                   HttpSession session) throws Exception {
        UserEntity user = authService.requireUser(session);
        AuthContext.requireRole(session, "SEEKER");
        ResumeService.UploadResult r = resumeService.upload(user, file, label);
        return ApiResponse.ok("Resume uploaded and scored", toDto(r));
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list(HttpSession session) {
        UserEntity user = authService.requireUser(session);
        AuthContext.requireRole(session, "SEEKER");
        List<ResumeVersionEntity> versions = resumeService.listVersions(user.id);
        List<Map<String, Object>> out = new ArrayList<>();
        for (ResumeVersionEntity v : versions) {
            GeneralScoreEntity gs = scoreRepo.findFirstByResumeVersionIdOrderByScoredAtDesc(v.id).orElse(null);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("versionId", v.id);
            m.put("versionNumber", v.versionNumber);
            m.put("versionLabel", v.versionLabel);
            m.put("originalFileName", v.originalFileName);
            m.put("uploadedAt", v.uploadedAt);
            m.put("fileSize", v.fileSize);
            m.put("totalScore", gs == null ? null : gs.totalScore);
            out.add(m);
        }
        return ApiResponse.ok(out);
    }

    @GetMapping("/{versionId}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long versionId, HttpSession session) {
        UserEntity user = authService.requireUser(session);
        AuthContext.requireRole(session, "SEEKER");
        ResumeVersionEntity v = resumeService.listVersions(user.id).stream()
                .filter(x -> x.id.equals(versionId)).findFirst()
                .orElseThrow(() -> new java.util.NoSuchElementException("Version not found"));
        ResumeExtractionEntity ex = extractionRepo.findFirstByResumeVersionIdOrderByCreatedAtDesc(v.id).orElse(null);
        List<ResumeFormatFlagEntity> flags = flagRepo.findByResumeVersionId(v.id);
        GeneralScoreEntity gs = scoreRepo.findFirstByResumeVersionIdOrderByScoredAtDesc(v.id).orElse(null);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("version", v);
        out.put("extraction", ex);
        out.put("flags", flags);
        out.put("score", gs);
        return ApiResponse.ok(out);
    }

    @GetMapping("/{versionId}/general-score")
    public ApiResponse<Object> score(@PathVariable Long versionId, HttpSession session) {
        UserEntity user = authService.requireUser(session);
        AuthContext.requireRole(session, "SEEKER");
        GeneralScoreEntity gs = resumeService.latestScoreForVersion(versionId);
        if (gs == null) throw new java.util.NoSuchElementException("No score yet");
        return ApiResponse.ok(gs);
    }

    @GetMapping("/history")
    public ApiResponse<List<GeneralScoreEntity>> history(HttpSession session) {
        UserEntity user = authService.requireUser(session);
        AuthContext.requireRole(session, "SEEKER");
        return ApiResponse.ok(resumeService.historyForSeeker(user.id));
    }

    @GetMapping("/compare")
    public ApiResponse<java.util.Map<String, Object>> compare(@RequestParam("a") Long a,
                                                              @RequestParam("b") Long b,
                                                              HttpSession session) {
        UserEntity user = authService.requireUser(session);
        AuthContext.requireRole(session, "SEEKER");
        // ensure both versions belong to the seeker
        var versions = resumeService.listVersions(user.id);
        if (versions.stream().noneMatch(v -> v.id.equals(a)) ||
            versions.stream().noneMatch(v -> v.id.equals(b))) {
            throw new SecurityException("Version not owned by you");
        }
        return ApiResponse.ok(comparisonService.compare(a, b));
    }

    private Map<String, Object> toDto(ResumeService.UploadResult r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("versionId", r.versionId);
        m.put("versionNumber", r.versionNumber);
        m.put("versionLabel", r.versionLabel);
        Map<String, Object> extraction = new LinkedHashMap<>();
        extraction.put("name", r.extraction.extractedName);
        extraction.put("email", r.extraction.extractedEmail);
        extraction.put("phone", r.extraction.extractedPhone);
        extraction.put("estimatedYears", r.extraction.experienceYears);
        extraction.put("skills", r.skillScan.canonicalNames);
        m.put("extraction", extraction);
        List<Map<String, Object>> flags = new ArrayList<>();
        for (ResumeFormatFlagEntity f : r.flags) {
            Map<String, Object> fm = new LinkedHashMap<>();
            fm.put("type", f.flagType);
            fm.put("severity", f.severity.name());
            fm.put("section", f.sectionName);
            fm.put("message", f.message);
            flags.add(fm);
        }
        m.put("flags", flags);
        m.put("score", Map.of(
                "total", r.score.total,
                "formatting", r.score.formatting,
                "completeness", r.score.completeness,
                "structure", r.score.structure,
                "skills", r.score.skills,
                "experience", r.score.experience));
        m.put("recommendations", r.score.recommendations);
        m.put("explanation", r.score.explanationJson);
        return m;
    }
}
