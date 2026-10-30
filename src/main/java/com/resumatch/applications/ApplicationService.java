package com.resumatch.applications;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumatch.ats.*;
import com.resumatch.common.ApplicationStatus;
import com.resumatch.common.JobStatus;
import com.resumatch.jobs.JobPostingEntity;
import com.resumatch.jobs.JobPostingRepository;
import com.resumatch.jobs.JobSkillEntity;
import com.resumatch.jobs.JobSkillRepository;
import com.resumatch.notifications.EmailService;
import com.resumatch.resume.ResumeExtractionEntity;
import com.resumatch.resume.ResumeExtractionRepository;
import com.resumatch.resume.ResumeFormatFlagEntity;
import com.resumatch.resume.ResumeFormatFlagRepository;
import com.resumatch.skills.AliasIndexService;
import com.resumatch.user.NotificationRepository;
import com.resumatch.user.UserEntity;
import com.resumatch.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class ApplicationService {
    private final ApplicationRepository appRepo;
    private final ApplicationStatusHistoryRepository historyRepo;
    private final JobPostingRepository jobRepo;
    private final JobSkillRepository jobSkillRepo;
    private final ResumeExtractionRepository extractionRepo;
    private final ResumeFormatFlagRepository flagRepo;
    private final CompanyScoreRepository companyScoreRepo;
    private final GeneralScoreRepository generalScoreRepo;
    private final AliasIndexService aliasIndexService;
    private final CompanyScoreService companyScoreService;
    private final UserRepository userRepo;
    private final EmailService email;
    private final NotificationRepository notificationRepo;
    private final ObjectMapper json = new ObjectMapper();

    public ApplicationService(ApplicationRepository appRepo,
                              ApplicationStatusHistoryRepository historyRepo,
                              JobPostingRepository jobRepo,
                              JobSkillRepository jobSkillRepo,
                              ResumeExtractionRepository extractionRepo,
                              ResumeFormatFlagRepository flagRepo,
                              CompanyScoreRepository companyScoreRepo,
                              GeneralScoreRepository generalScoreRepo,
                              AliasIndexService aliasIndexService,
                              CompanyScoreService companyScoreService,
                              UserRepository userRepo,
                              EmailService email,
                              NotificationRepository notificationRepo) {
        this.appRepo = appRepo;
        this.historyRepo = historyRepo;
        this.jobRepo = jobRepo;
        this.jobSkillRepo = jobSkillRepo;
        this.extractionRepo = extractionRepo;
        this.flagRepo = flagRepo;
        this.companyScoreRepo = companyScoreRepo;
        this.generalScoreRepo = generalScoreRepo;
        this.aliasIndexService = aliasIndexService;
        this.companyScoreService = companyScoreService;
        this.userRepo = userRepo;
        this.email = email;
        this.notificationRepo = notificationRepo;
    }

    @Transactional
    public ApplicationEntity apply(UserEntity seeker, Long jobId, Long resumeVersionId) {
        JobPostingEntity job = jobRepo.findById(jobId).orElseThrow(() -> new java.util.NoSuchElementException("Job not found"));
        if (job.status != JobStatus.OPEN) throw new IllegalArgumentException("This job is not open for applications");
        if (appRepo.findByJobIdAndSeekerId(jobId, seeker.id).isPresent())
            throw new IllegalArgumentException("You have already applied to this job");

        ResumeExtractionEntity ex = extractionRepo.findFirstByResumeVersionIdOrderByCreatedAtDesc(resumeVersionId)
                .orElseThrow(() -> new IllegalArgumentException("Resume not parsed yet"));

        ApplicationEntity app = new ApplicationEntity();
        app.jobId = jobId;
        app.seekerId = seeker.id;
        app.resumeVersionId = resumeVersionId;
        app.status = ApplicationStatus.APPLIED;
        app = appRepo.save(app);

        ApplicationStatusHistoryEntity h = new ApplicationStatusHistoryEntity();
        h.applicationId = app.id;
        h.oldStatus = null;
        h.newStatus = ApplicationStatus.APPLIED.name();
        h.changedByUserId = seeker.id;
        h.notes = "Initial application";
        historyRepo.save(h);

        computeAndStoreCompanyScore(app, ex, null);

        // notify recruiter
        UserEntity recruiter = userRepo.findById(job.recruiterId).orElse(null);
        if (recruiter != null) {
            email.send(recruiter.id, "New applicant for " + job.title,
                    seeker.displayName + " just applied to " + job.title + ".");
        }
        return app;
    }

    @Transactional
    public CompanyScoreService.ScoreOutput computeAndStoreCompanyScore(ApplicationEntity app,
                                                                       ResumeExtractionEntity ex,
                                                                       List<String> hypotheticalSkills) {
        JobPostingEntity job = jobRepo.findById(app.jobId).orElseThrow();
        List<JobSkillEntity> jobSkills = jobSkillRepo.findByJobId(job.id);
        List<ResumeFormatFlagEntity> flags = flagRepo.findByResumeVersionId(app.resumeVersionId);
        AliasIndex.SkillScan scan = aliasIndexService.get().scan(ex.extractedText == null ? "" : ex.extractedText);
        AliasIndex.SkillScan hyp = aliasIndexService.get().scanHypothetical(hypotheticalSkills);
        GeneralScoreEntity gs = generalScoreRepo.findFirstByResumeVersionIdOrderByScoredAtDesc(app.resumeVersionId).orElse(null);

        CompanyScoreService.ScoreOutput out = companyScoreService.compute(ex, jobSkills, flags, scan, hyp,
                gs == null ? null : gs.totalScore);

        CompanyScoreEntity cs = new CompanyScoreEntity();
        cs.applicationId = app.id;
        cs.generalScoreId = gs == null ? null : gs.id;
        cs.totalScore = out.total;
        cs.mustHaveScore = out.mustHave;
        cs.niceToHaveScore = out.niceToHave;
        cs.experienceScore = out.experience;
        cs.educationScore = out.education;
        cs.formattingScore = out.formatting;
        cs.matchedSkillsJson = out.matchedSkillsJson;
        cs.missingSkillsJson = out.missingSkillsJson;
        cs.recommendationsJson = out.recommendationsJson;
        cs.explanationJson = out.explanationJson;
        companyScoreRepo.save(cs);
        return out;
    }

    @Transactional
    public ApplicationEntity updateStatus(UserEntity actor, Long applicationId, ApplicationStatus newStatus, String notes) {
        ApplicationEntity app = appRepo.findById(applicationId).orElseThrow(() -> new java.util.NoSuchElementException("Application not found"));
        JobPostingEntity job = jobRepo.findById(app.jobId).orElseThrow();
        if (!job.recruiterId.equals(actor.id) && !"ADMIN".equals(actor.role.name()))
            throw new SecurityException("Not your job's application");
        ApplicationStatus old = app.status;
        app.status = newStatus;
        app.statusUpdatedAt = Instant.now();
        appRepo.save(app);
        ApplicationStatusHistoryEntity h = new ApplicationStatusHistoryEntity();
        h.applicationId = app.id;
        h.oldStatus = old.name();
        h.newStatus = newStatus.name();
        h.changedByUserId = actor.id;
        h.notes = notes;
        historyRepo.save(h);

        UserEntity seeker = userRepo.findById(app.seekerId).orElse(null);
        if (seeker != null) {
            email.send(seeker.id, "Application status updated",
                    "Your application for '" + job.title + "' is now " + newStatus.name() + ".");
        }
        return app;
    }

    /**
     * Bulk update status for a job based on a score threshold. Returns the
     * number of applications that were actually transitioned (skips those
     * already in the target status to keep audit history clean).
     */
    @Transactional
    public Map<String, Object> bulkUpdateByScore(UserEntity actor, Long jobId, double minScore,
                                                 double maxScore, ApplicationStatus newStatus) {
        JobPostingEntity job = jobRepo.findById(jobId).orElseThrow();
        if (!job.recruiterId.equals(actor.id) && !"ADMIN".equals(actor.role.name()))
            throw new SecurityException("Not your job");
        var apps = appRepo.findByJobIdOrderByAppliedAtDesc(jobId);
        int updated = 0;
        for (var a : apps) {
            var cs = companyScoreRepo.findFirstByApplicationIdOrderByComputedAtDesc(a.id).orElse(null);
            if (cs == null) continue;
            if (cs.totalScore >= minScore && cs.totalScore <= maxScore && a.status != newStatus) {
                updateStatus(actor, a.id, newStatus, "Bulk action: score in [" + minScore + ", " + maxScore + "]");
                updated++;
            }
        }
        return Map.of("updated", updated, "target", newStatus.name(), "jobId", jobId);
    }
}
