package com.resumatch.jobs;

import com.resumatch.applications.ApplicationRepository;
import com.resumatch.ats.CompanyScoreEntity;
import com.resumatch.ats.CompanyScoreRepository;
import com.resumatch.auth.AuthService;
import com.resumatch.common.ApiResponse;
import com.resumatch.common.AuthContext;
import com.resumatch.common.JobStatus;
import com.resumatch.user.UserEntity;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/recruiter/jobs")
public class JobController {

    private final JobService jobService;
    private final JobPostingRepository jobRepo;
    private final JobSkillRepository skillRepo;
    private final AuthService authService;
    private final ApplicationRepository applicationRepo;
    private final CompanyScoreRepository companyScoreRepo;

    public JobController(JobService jobService,
                         JobPostingRepository jobRepo,
                         JobSkillRepository skillRepo,
                         AuthService authService,
                         ApplicationRepository applicationRepo,
                         CompanyScoreRepository companyScoreRepo) {
        this.jobService = jobService;
        this.jobRepo = jobRepo;
        this.skillRepo = skillRepo;
        this.authService = authService;
        this.applicationRepo = applicationRepo;
        this.companyScoreRepo = companyScoreRepo;
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> body, HttpSession session) {
        UserEntity u = authService.requireUser(session);
        AuthContext.requireRole(session, "RECRUITER");
        JobPostingEntity j = jobService.createJob(u, body);
        return ApiResponse.ok("Job created", toDto(j, skillRepo.findByJobId(j.id)));
    }

    @PutMapping("/{jobId}")
    public ApiResponse<Map<String, Object>> update(@PathVariable Long jobId,
                                                   @RequestBody Map<String, Object> body,
                                                   HttpSession session) {
        UserEntity u = authService.requireUser(session);
        AuthContext.requireRole(session, "RECRUITER");
        JobPostingEntity j = jobService.updateJob(u, jobId, body);
        return ApiResponse.ok("Job updated", toDto(j, skillRepo.findByJobId(j.id)));
    }

    @PatchMapping("/{jobId}/status")
    public ApiResponse<Map<String, Object>> status(@PathVariable Long jobId,
                                                  @RequestBody Map<String, String> body,
                                                  HttpSession session) {
        UserEntity u = authService.requireUser(session);
        AuthContext.requireRole(session, "RECRUITER");
        JobStatus s = JobStatus.valueOf(body.getOrDefault("status", "OPEN"));
        JobPostingEntity j = jobService.setStatus(u, jobId, s);
        return ApiResponse.ok("Status updated", toDto(j, skillRepo.findByJobId(j.id)));
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> myJobs(HttpSession session) {
        UserEntity u = authService.requireUser(session);
        AuthContext.requireRole(session, "RECRUITER");
        List<JobPostingEntity> jobs = jobRepo.findByRecruiterIdOrderByCreatedAtDesc(u.id);
        List<Map<String, Object>> out = new ArrayList<>();
        for (JobPostingEntity j : jobs) {
            Map<String, Object> m = toDto(j, skillRepo.findByJobId(j.id));
            long apps = applicationRepo.findByJobIdOrderByAppliedAtDesc(j.id).size();
            m.put("applicantCount", apps);
            out.add(m);
        }
        return ApiResponse.ok(out);
    }

    @GetMapping("/{jobId}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long jobId, HttpSession session) {
        UserEntity u = authService.requireUser(session);
        JobPostingEntity j = jobRepo.findById(jobId).orElseThrow(() -> new java.util.NoSuchElementException("Job not found"));
        if (!j.recruiterId.equals(u.id) && !"ADMIN".equals(u.role.name()))
            throw new SecurityException("Not your job");
        return ApiResponse.ok(toDto(j, skillRepo.findByJobId(j.id)));
    }

    @GetMapping("/{jobId}/applicants")
    public ApiResponse<List<Map<String, Object>>> applicants(@PathVariable Long jobId,
                                                             @RequestParam(required = false) String status,
                                                             @RequestParam(required = false) Double minScore,
                                                             @RequestParam(required = false) Double maxScore,
                                                             @RequestParam(required = false) String sort,
                                                             @RequestParam(required = false) String q,
                                                             HttpSession session) {
        UserEntity u = authService.requireUser(session);
        AuthContext.requireRole(session, "RECRUITER");
        JobPostingEntity j = jobRepo.findById(jobId).orElseThrow(() -> new java.util.NoSuchElementException("Job not found"));
        if (!j.recruiterId.equals(u.id)) throw new SecurityException("Not your job");

        var apps = applicationRepo.findByJobIdOrderByAppliedAtDesc(jobId);
        var scores = companyScoreRepo.findByApplicationIdIn(apps.stream().map(a -> a.id).toList())
                .stream().collect(Collectors.toMap(s -> s.applicationId, s -> s));
        List<Map<String, Object>> out = new ArrayList<>();
        for (var a : apps) {
            CompanyScoreEntity cs = scores.get(a.id);
            // filters
            if (status != null && !status.isBlank() && !a.status.name().equalsIgnoreCase(status)) continue;
            Double ts = cs == null ? null : cs.totalScore;
            if (minScore != null && (ts == null || ts < minScore)) continue;
            if (maxScore != null && (ts == null || ts > maxScore)) continue;
            if (q != null && !q.isBlank() && !(j.title.toLowerCase().contains(q.toLowerCase()))) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("applicationId", a.id);
            m.put("seekerId", a.seekerId);
            m.put("status", a.status.name());
            m.put("appliedAt", a.appliedAt);
            m.put("resumeVersionId", a.resumeVersionId);
            m.put("totalScore", ts);
            m.put("matchedSkills", cs == null ? null : cs.getMatchedSkills());
            m.put("missingSkills", cs == null ? null : cs.getMissingSkills());
            m.put("recommendations", cs == null ? null : cs.getRecommendations());
            m.put("explanation", cs == null ? null : cs.explanationJson);
            out.add(m);
        }
        if ("oldest".equalsIgnoreCase(sort)) {
            out.sort(Comparator.comparing(m -> (java.time.Instant) m.get("appliedAt")));
        } else if ("score-asc".equalsIgnoreCase(sort)) {
            out.sort((x, y) -> {
                Double sx = (Double) x.get("totalScore");
                Double sy = (Double) y.get("totalScore");
                if (sx == null && sy == null) return 0;
                if (sx == null) return 1;
                if (sy == null) return -1;
                return Double.compare(sx, sy);
            });
        } else {
            out.sort((x, y) -> {
                Double sx = (Double) x.get("totalScore");
                Double sy = (Double) y.get("totalScore");
                if (sx == null && sy == null) return 0;
                if (sx == null) return 1;
                if (sy == null) return -1;
                return Double.compare(sy, sx);
            });
        }
        return ApiResponse.ok(out);
    }

    @GetMapping("/{jobId}/analytics")
    public ApiResponse<Map<String, Object>> analytics(@PathVariable Long jobId, HttpSession session) {
        UserEntity u = authService.requireUser(session);
        AuthContext.requireRole(session, "RECRUITER");
        JobPostingEntity j = jobRepo.findById(jobId).orElseThrow(() -> new java.util.NoSuchElementException("Job not found"));
        if (!j.recruiterId.equals(u.id)) throw new SecurityException("Not your job");
        var apps = applicationRepo.findByJobIdOrderByAppliedAtDesc(jobId);
        var scores = companyScoreRepo.findByApplicationIdIn(apps.stream().map(a -> a.id).toList());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("totalApplicants", apps.size());
        out.put("shortlisted", apps.stream().filter(a -> a.status == com.resumatch.common.ApplicationStatus.SHORTLISTED).count());
        out.put("rejected", apps.stream().filter(a -> a.status == com.resumatch.common.ApplicationStatus.REJECTED).count());
        out.put("averageScore", scores.isEmpty() ? 0 : scores.stream().mapToDouble(s -> s.totalScore).average().orElse(0));
        // missing-skill frequency
        Map<String, Integer> missCount = new HashMap<>();
        for (var s : scores) {
            try {
                var arr = new com.fasterxml.jackson.databind.ObjectMapper().readTree(s.missingSkillsJson);
                if (arr != null && arr.has("mustHave")) {
                    for (var n : arr.get("mustHave")) {
                        missCount.merge(n.asText(), 1, Integer::sum);
                    }
                }
            } catch (Exception ignored) {}
        }
        out.put("topMissingSkills", missCount.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .limit(10)
                .map(e -> Map.of("skill", e.getKey(), "count", e.getValue()))
                .toList());
        return ApiResponse.ok(out);
    }

    private Map<String, Object> toDto(JobPostingEntity j, List<JobSkillEntity> skills) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", j.id);
        m.put("title", j.title);
        m.put("description", j.description);
        m.put("location", j.location);
        m.put("employmentType", j.employmentType);
        m.put("minExperienceYears", j.minExperienceYears);
        m.put("maxExperienceYears", j.maxExperienceYears);
        m.put("status", j.status.name());
        m.put("companyId", j.companyId);
        m.put("createdAt", j.createdAt);
        m.put("updatedAt", j.updatedAt);
        m.put("skills", skills.stream().map(s -> Map.of(
                "id", s.id, "name", s.rawSkillText,
                "importance", s.importance.name(), "weight", s.weight)).toList());
        return m;
    }
}
