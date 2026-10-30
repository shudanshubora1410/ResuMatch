package com.resumatch.dashboards;

import com.resumatch.applications.ApplicationEntity;
import com.resumatch.applications.ApplicationRepository;
import com.resumatch.ats.*;
import com.resumatch.common.JobStatus;
import com.resumatch.jobs.JobPostingEntity;
import com.resumatch.jobs.JobPostingRepository;
import com.resumatch.jobs.JobSkillEntity;
import com.resumatch.jobs.JobSkillRepository;
import com.resumatch.resume.ResumeExtractionEntity;
import com.resumatch.resume.ResumeExtractionRepository;
import com.resumatch.resume.ResumeRepository;
import com.resumatch.skills.AliasIndexService;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Powers the seeker-side "insights" surface:
 *  - recommended jobs feed (scored but not yet applied to)
 *  - skills-gap dashboard (most common missing skills across applications)
 */
@Service
public class InsightsService {

    private final JobPostingRepository jobRepo;
    private final JobSkillRepository jobSkillRepo;
    private final ApplicationRepository appRepo;
    private final CompanyScoreRepository companyScoreRepo;
    private final ResumeRepository resumeRepo;
    private final ResumeExtractionRepository extractionRepo;
    private final AliasIndexService aliasIndexService;
    private final CompanyScoreService companyScoreService;

    public InsightsService(JobPostingRepository jobRepo,
                           JobSkillRepository jobSkillRepo,
                           ApplicationRepository appRepo,
                           CompanyScoreRepository companyScoreRepo,
                           ResumeRepository resumeRepo,
                           ResumeExtractionRepository extractionRepo,
                           AliasIndexService aliasIndexService,
                           CompanyScoreService companyScoreService) {
        this.jobRepo = jobRepo;
        this.jobSkillRepo = jobSkillRepo;
        this.appRepo = appRepo;
        this.companyScoreRepo = companyScoreRepo;
        this.resumeRepo = resumeRepo;
        this.extractionRepo = extractionRepo;
        this.aliasIndexService = aliasIndexService;
        this.companyScoreService = companyScoreService;
    }

    public List<Map<String, Object>> recommendedJobs(Long seekerId) {
        var resume = resumeRepo.findFirstBySeekerId(seekerId).orElse(null);
        if (resume == null || resume.activeVersionId == null) return List.of();
        var ex = extractionRepo.findFirstByResumeVersionIdOrderByCreatedAtDesc(resume.activeVersionId).orElse(null);
        if (ex == null) return List.of();
        var allOpen = jobRepo.findByStatusOrderByCreatedAtDesc(JobStatus.OPEN);
        var applied = new HashSet<Long>();
        for (var a : appRepo.findBySeekerIdOrderByAppliedAtDesc(seekerId)) applied.add(a.jobId);

        List<Map<String, Object>> out = new ArrayList<>();
        for (var job : allOpen) {
            if (applied.contains(job.id)) continue;
            var skills = jobSkillRepo.findByJobId(job.id);
            if (skills.isEmpty()) continue;
            var flags = List.<com.resumatch.resume.ResumeFormatFlagEntity>of();
            var scan = aliasIndexService.get().scan(ex.extractedText == null ? "" : ex.extractedText);
            CompanyScoreService.ScoreOutput sc = companyScoreService.compute(ex, skills, flags, scan, null, null);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("jobId", job.id);
            m.put("title", job.title);
            m.put("location", job.location);
            m.put("employmentType", job.employmentType);
            m.put("totalScore", sc.total);
            m.put("mustHave", sc.mustHave);
            m.put("niceToHave", sc.niceToHave);
            m.put("matchedMust", sc.matchedSkills);
            m.put("missingMust", sc.missingSkills);
            out.add(m);
        }
        out.sort((a, b) -> Double.compare((Double) b.get("totalScore"), (Double) a.get("totalScore")));
        return out.size() > 8 ? out.subList(0, 8) : out;
    }

    public Map<String, Object> skillsGap(Long seekerId) {
        var apps = appRepo.findBySeekerIdOrderByAppliedAtDesc(seekerId);
        Map<String, Integer> missingCount = new HashMap<>();
        Map<String, Integer> appsWithSkill = new HashMap<>();
        int totalApps = 0;
        for (var app : apps) {
            var cs = companyScoreRepo.findFirstByApplicationIdOrderByComputedAtDesc(app.id).orElse(null);
            if (cs == null) continue;
            totalApps++;
            try {
                var node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(cs.missingSkillsJson);
                if (node != null && node.has("mustHave")) {
                    for (var n : node.get("mustHave")) {
                        String s = n.asText();
                        missingCount.merge(s, 1, Integer::sum);
                    }
                }
            } catch (Exception ignored) {}
        }
        // assemble top missing list with simple adoption estimate
        final int appsAnalyzed = totalApps;
        List<Map<String, Object>> top = new ArrayList<>();
        missingCount.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .limit(12)
                .forEach(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("skill", e.getKey());
                    m.put("missingCount", e.getValue());
                    m.put("appsAnalyzed", appsAnalyzed);
                    int pct = appsAnalyzed == 0 ? 0 : Math.round(100f * e.getValue() / appsAnalyzed);
                    m.put("missingPct", pct);
                    top.add(m);
                });
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("totalApplicationsAnalyzed", totalApps);
        out.put("topMissing", top);
        return out;
    }
}
