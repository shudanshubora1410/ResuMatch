package com.resumatch.applications;

import com.resumatch.ats.CompanyScoreEntity;
import com.resumatch.ats.CompanyScoreRepository;
import com.resumatch.auth.AuthService;
import com.resumatch.common.ApiResponse;
import com.resumatch.common.ApplicationStatus;
import com.resumatch.common.AuthContext;
import com.resumatch.jobs.JobPostingRepository;
import com.resumatch.jobs.JobSkillRepository;
import com.resumatch.resume.ResumeExtractionEntity;
import com.resumatch.resume.ResumeExtractionRepository;
import com.resumatch.user.UserEntity;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
public class ApplicationController {

    private final ApplicationService appService;
    private final ApplicationRepository appRepo;
    private final ApplicationStatusHistoryRepository historyRepo;
    private final CompanyScoreRepository companyScoreRepo;
    private final AuthService authService;
    private final JobPostingRepository jobRepo;
    private final JobSkillRepository jobSkillRepo;
    private final ResumeExtractionRepository extractionRepo;

    public ApplicationController(ApplicationService appService,
                                 ApplicationRepository appRepo,
                                 ApplicationStatusHistoryRepository historyRepo,
                                 CompanyScoreRepository companyScoreRepo,
                                 AuthService authService,
                                 JobPostingRepository jobRepo,
                                 JobSkillRepository jobSkillRepo,
                                 ResumeExtractionRepository extractionRepo) {
        this.appService = appService;
        this.appRepo = appRepo;
        this.historyRepo = historyRepo;
        this.companyScoreRepo = companyScoreRepo;
        this.authService = authService;
        this.jobRepo = jobRepo;
        this.jobSkillRepo = jobSkillRepo;
        this.extractionRepo = extractionRepo;
    }

    @PostMapping("/api/seeker/jobs/{jobId}/apply")
    public ApiResponse<Map<String, Object>> apply(@PathVariable Long jobId,
                                                  @RequestBody Map<String, Object> body,
                                                  HttpSession session) {
        UserEntity u = authService.requireUser(session);
        AuthContext.requireRole(session, "SEEKER");
        Long resumeVersionId = ((Number) body.get("resumeVersionId")).longValue();
        ApplicationEntity app = appService.apply(u, jobId, resumeVersionId);
        CompanyScoreEntity cs = companyScoreRepo.findFirstByApplicationIdOrderByComputedAtDesc(app.id).orElse(null);
        return ApiResponse.ok("Application submitted", Map.of(
                "applicationId", app.id,
                "status", app.status.name(),
                "companyScore", cs == null ? null : cs.totalScore));
    }

    @GetMapping("/api/seeker/applications")
    public ApiResponse<List<Map<String, Object>>> myApplications(HttpSession session) {
        UserEntity u = authService.requireUser(session);
        AuthContext.requireRole(session, "SEEKER");
        var apps = appRepo.findBySeekerIdOrderByAppliedAtDesc(u.id);
        List<Map<String, Object>> out = new ArrayList<>();
        for (var a : apps) {
            var job = jobRepo.findById(a.jobId).orElse(null);
            var cs = companyScoreRepo.findFirstByApplicationIdOrderByComputedAtDesc(a.id).orElse(null);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("applicationId", a.id);
            m.put("jobId", a.jobId);
            m.put("jobTitle", job == null ? null : job.title);
            m.put("status", a.status.name());
            m.put("appliedAt", a.appliedAt);
            m.put("statusUpdatedAt", a.statusUpdatedAt);
            m.put("totalScore", cs == null ? null : cs.totalScore);
            m.put("matchedSkills", cs == null ? null : cs.getMatchedSkills());
            m.put("missingSkills", cs == null ? null : cs.getMissingSkills());
            m.put("recommendations", cs == null ? null : cs.getRecommendations());
            out.add(m);
        }
        return ApiResponse.ok(out);
    }

    @GetMapping("/api/seeker/company-scores")
    public ApiResponse<List<Map<String, Object>>> companyScores(HttpSession session) {
        UserEntity u = authService.requireUser(session);
        AuthContext.requireRole(session, "SEEKER");
        var apps = appRepo.findBySeekerIdOrderByAppliedAtDesc(u.id);
        List<Map<String, Object>> out = new ArrayList<>();
        for (var a : apps) {
            var job = jobRepo.findById(a.jobId).orElse(null);
            var cs = companyScoreRepo.findFirstByApplicationIdOrderByComputedAtDesc(a.id).orElse(null);
            if (cs == null) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("applicationId", a.id);
            m.put("jobId", a.jobId);
            m.put("jobTitle", job == null ? null : job.title);
            m.put("totalScore", cs.totalScore);
            m.put("mustHaveScore", cs.mustHaveScore);
            m.put("niceToHaveScore", cs.niceToHaveScore);
            m.put("experienceScore", cs.experienceScore);
            m.put("educationScore", cs.educationScore);
            m.put("formattingScore", cs.formattingScore);
            m.put("matchedSkills", cs.getMatchedSkills());
            m.put("missingSkills", cs.getMissingSkills());
            m.put("recommendations", cs.getRecommendations());
            m.put("explanation", cs.explanationJson);
            out.add(m);
        }
        out.sort((a, b) -> Double.compare((Double) b.get("totalScore"), (Double) a.get("totalScore")));
        return ApiResponse.ok(out);
    }

    @PostMapping("/api/seeker/simulate-score")
    public ApiResponse<Map<String, Object>> simulate(@RequestBody Map<String, Object> body, HttpSession session) {
        UserEntity u = authService.requireUser(session);
        AuthContext.requireRole(session, "SEEKER");
        Long resumeVersionId = ((Number) body.get("resumeVersionId")).longValue();
        Long jobId = ((Number) body.get("jobId")).longValue();
        @SuppressWarnings("unchecked")
        List<String> hypothetical = (List<String>) body.getOrDefault("hypotheticalSkills", List.of());

        // apply rules but never persist a fake application
        var app = appRepo.findByJobIdAndSeekerId(jobId, u.id).orElse(null);
        var job = jobRepo.findById(jobId).orElseThrow();
        var ex = extractionRepo.findFirstByResumeVersionIdOrderByCreatedAtDesc(resumeVersionId).orElseThrow();
        if (app == null) {
            // compute transient score without persisting
            var skills = jobSkillRepo.findByJobId(job.id);
            var out = appService.computeAndStoreCompanyScore(
                    persistTransientApplication(u.id, jobId, resumeVersionId), ex, hypothetical);
            return ApiResponse.ok(toSimDto(out, job.title));
        }
        var out = appService.computeAndStoreCompanyScore(app, ex, hypothetical);
        return ApiResponse.ok(toSimDto(out, job.title));
    }

    private ApplicationEntity persistTransientApplication(Long seekerId, Long jobId, Long resumeVersionId) {
        ApplicationEntity a = new ApplicationEntity();
        a.seekerId = seekerId;
        a.jobId = jobId;
        a.resumeVersionId = resumeVersionId;
        a.status = ApplicationStatus.APPLIED;
        return appRepo.save(a);
    }

    private Map<String, Object> toSimDto(com.resumatch.ats.CompanyScoreService.ScoreOutput out, String jobTitle) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("jobTitle", jobTitle);
        m.put("total", out.total);
        m.put("mustHave", out.mustHave);
        m.put("niceToHave", out.niceToHave);
        m.put("experience", out.experience);
        m.put("education", out.education);
        m.put("formatting", out.formatting);
        m.put("matchedMust", out.matchedSkills);
        m.put("matchedNice", out.matchedNice);
        m.put("missingMust", out.missingSkills);
        m.put("missingNice", out.missingNice);
        m.put("recommendations", out.recommendations);
        return m;
    }

    @PatchMapping("/api/recruiter/applications/{applicationId}/status")
    public ApiResponse<Map<String, Object>> updateStatus(@PathVariable Long applicationId,
                                                          @RequestBody Map<String, Object> body,
                                                          HttpSession session) {
        UserEntity u = authService.requireUser(session);
        AuthContext.requireRole(session, "RECRUITER");
        ApplicationStatus s = ApplicationStatus.valueOf((String) body.get("status"));
        String notes = (String) body.get("notes");
        ApplicationEntity a = appService.updateStatus(u, applicationId, s, notes);
        return ApiResponse.ok("Status updated", Map.of(
                "applicationId", a.id, "status", a.status.name(),
                "statusUpdatedAt", a.statusUpdatedAt));
    }

    @GetMapping("/api/recruiter/applications/{applicationId}/history")
    public ApiResponse<List<ApplicationStatusHistoryEntity>> history(@PathVariable Long applicationId, HttpSession session) {
        UserEntity u = authService.requireUser(session);
        AuthContext.requireRole(session, "RECRUITER");
        var app = appRepo.findById(applicationId).orElseThrow(() -> new java.util.NoSuchElementException("Application not found"));
        var job = jobRepo.findById(app.jobId).orElseThrow();
        if (!job.recruiterId.equals(u.id)) throw new SecurityException("Not your job");
        return ApiResponse.ok(historyRepo.findByApplicationIdOrderByChangedAtAsc(applicationId));
    }

    @PostMapping("/api/recruiter/jobs/{jobId}/bulk-status")
    public ApiResponse<Map<String, Object>> bulkStatus(@PathVariable Long jobId,
                                                        @RequestBody Map<String, Object> body,
                                                        HttpSession session) {
        UserEntity u = authService.requireUser(session);
        AuthContext.requireRole(session, "RECRUITER");
        double min = body.get("minScore") == null ? 0 : ((Number) body.get("minScore")).doubleValue();
        double max = body.get("maxScore") == null ? 100 : ((Number) body.get("maxScore")).doubleValue();
        ApplicationStatus s = ApplicationStatus.valueOf((String) body.get("status"));
        return ApiResponse.ok("Bulk update complete", appService.bulkUpdateByScore(u, jobId, min, max, s));
    }
}
