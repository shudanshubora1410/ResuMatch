package com.resumatch.admin;

import com.resumatch.applications.ApplicationEntity;
import com.resumatch.applications.ApplicationRepository;
import com.resumatch.applications.ApplicationService;
import com.resumatch.applications.ApplicationStatusHistoryEntity;
import com.resumatch.applications.ApplicationStatusHistoryRepository;
import com.resumatch.ats.CompanyScoreEntity;
import com.resumatch.ats.CompanyScoreRepository;
import com.resumatch.auth.AuthService;
import com.resumatch.common.AccountStatus;
import com.resumatch.common.ApiResponse;
import com.resumatch.common.ApplicationStatus;
import com.resumatch.common.AuthContext;
import com.resumatch.common.JobStatus;
import com.resumatch.jobs.JobPostingEntity;
import com.resumatch.jobs.JobPostingRepository;
import com.resumatch.jobs.JobService;
import com.resumatch.jobs.JobSkillEntity;
import com.resumatch.jobs.JobSkillRepository;
import com.resumatch.parsing.ResumeParser;
import com.resumatch.resume.ResumeEntity;
import com.resumatch.resume.ResumeExtractionEntity;
import com.resumatch.resume.ResumeExtractionRepository;
import com.resumatch.resume.ResumeFormatFlagEntity;
import com.resumatch.resume.ResumeFormatFlagRepository;
import com.resumatch.resume.ResumeRepository;
import com.resumatch.resume.ResumeService;
import com.resumatch.resume.ResumeVersionEntity;
import com.resumatch.resume.ResumeVersionRepository;
import com.resumatch.ats.GeneralScoreEntity;
import com.resumatch.ats.GeneralScoreRepository;
import com.resumatch.ats.GeneralScoreService;
import com.resumatch.skills.AliasIndexService;
import com.resumatch.user.CompanyEntity;
import com.resumatch.user.CompanyRepository;
import com.resumatch.user.NotificationRepository;
import com.resumatch.user.RecruiterProfileEntity;
import com.resumatch.user.RecruiterProfileRepository;
import com.resumatch.user.SeekerProfileEntity;
import com.resumatch.user.SeekerProfileRepository;
import com.resumatch.user.UserEntity;
import com.resumatch.user.UserRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.FileWriter;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * One-click demo data seeder. Populates the in-memory H2 database (or wipes
 * MySQL if the admin is feeling brave) with 3 seekers, 3 recruiters,
 * 6 jobs, ~15 applications across varied scores — so the entire platform
 * has meaningful data the moment you log in.
 *
 * Idempotent: if you click it twice, it just no-ops the second time.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminSeedController {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AdminSeedController.class);

    private final UserRepository userRepo;
    private final SeekerProfileRepository seekerRepo;
    private final RecruiterProfileRepository recruiterRepo;
    private final CompanyRepository companyRepo;
    private final JobPostingRepository jobRepo;
    private final JobSkillRepository jobSkillRepo;
    private final ApplicationRepository appRepo;
    private final ResumeRepository resumeRepo;
    private final ResumeVersionRepository versionRepo;
    private final ResumeExtractionRepository extractionRepo;
    private final ResumeFormatFlagRepository flagRepo;
    private final GeneralScoreRepository generalScoreRepo;
    private final CompanyScoreRepository companyScoreRepo;
    private final ApplicationService appService;
    private final ResumeService resumeService;
    private final JobService jobService;
    private final GeneralScoreService generalScoreService;
    private final AliasIndexService aliasIndexService;
    private final ResumeParser parser;
    private final AuthService authService;
    private final NotificationRepository notificationRepo;
    private final JobReportRepository reportRepo;
    private final ApplicationStatusHistoryRepository historyRepo;

    @Value("${resumatch.storage.path}")
    private String storagePath;

    public AdminSeedController(UserRepository userRepo, SeekerProfileRepository seekerRepo,
                               RecruiterProfileRepository recruiterRepo, CompanyRepository companyRepo,
                               JobPostingRepository jobRepo, JobSkillRepository jobSkillRepo,
                               ApplicationRepository appRepo, ResumeRepository resumeRepo,
                               ResumeVersionRepository versionRepo, ResumeExtractionRepository extractionRepo,
                               ResumeFormatFlagRepository flagRepo, GeneralScoreRepository generalScoreRepo,
                               CompanyScoreRepository companyScoreRepo, ApplicationService appService,
                               ResumeService resumeService, JobService jobService,
                               GeneralScoreService generalScoreService, AliasIndexService aliasIndexService,
                               ResumeParser parser, AuthService authService,
                               NotificationRepository notificationRepo, JobReportRepository reportRepo,
                               com.resumatch.applications.ApplicationStatusHistoryRepository historyRepo) {
        this.userRepo = userRepo;
        this.seekerRepo = seekerRepo;
        this.recruiterRepo = recruiterRepo;
        this.companyRepo = companyRepo;
        this.jobRepo = jobRepo;
        this.jobSkillRepo = jobSkillRepo;
        this.appRepo = appRepo;
        this.resumeRepo = resumeRepo;
        this.versionRepo = versionRepo;
        this.extractionRepo = extractionRepo;
        this.flagRepo = flagRepo;
        this.generalScoreRepo = generalScoreRepo;
        this.companyScoreRepo = companyScoreRepo;
        this.appService = appService;
        this.resumeService = resumeService;
        this.jobService = jobService;
        this.generalScoreService = generalScoreService;
        this.aliasIndexService = aliasIndexService;
        this.parser = parser;
        this.authService = authService;
        this.notificationRepo = notificationRepo;
        this.reportRepo = reportRepo;
        this.historyRepo = historyRepo;
    }

    @PostMapping("/seed-demo-data")
    @Transactional
    public ApiResponse<Map<String, Object>> seed(HttpSession session) {
        UserEntity admin = authService.requireUser(session);
        AuthContext.requireRole(session, "ADMIN");
        return seedDemoDataInternal();
    }

    /**
     * Populates the demo dataset (3 companies, 3 recruiters, 3 seekers, 6 jobs,
     * ~15 applications with varied scores).
     *
     * <p>Called from two places:
     * <ul>
     *   <li>{@link #seed(HttpSession)} - the admin-panel button (requires ADMIN role)</li>
     *   <li>{@link DemoAutoSeedRunner} - automatic on boot when
     *       {@code RESUMATCH_AUTO_SEED=true} (used on ephemeral cloud deploys
     *       where the database is wiped on every redeploy)</li>
     * </ul>
     *
     * <p>Safe to call repeatedly - it is a no-op if demo data is already present.
     */
    @Transactional
    public ApiResponse<Map<String, Object>> seedDemoDataInternal() {
        // guard: if a demo seeker already exists, treat as already-seeded
        if (userRepo.findByEmail("priya@demo.com").isPresent() ||
            userRepo.findByEmail("riya@technova.com").isPresent()) {
            return ApiResponse.ok("Demo data already present", Map.of(
                    "alreadySeeded", true,
                    "seekers", userRepo.findByRole(com.resumatch.common.Role.SEEKER).size(),
                    "recruiters", userRepo.findByRole(com.resumatch.common.Role.RECRUITER).size(),
                    "jobs", jobRepo.count(),
                    "applications", appRepo.count()));
        }

        // ---- 3 companies ----
        CompanyEntity techNova = company("TechNova", "Cloud infrastructure for early-stage startups.");
        CompanyEntity dataPath = company("DataPath Labs", "MLOps tooling for data teams.");
        CompanyEntity orbit = company("Orbit Studios", "Indie game studio. Remote-first.");

        // ---- 3 recruiters ----
        UserEntity riya = recruiter("riya@technova.com", "Riya Mehta", techNova, "Talent Lead");
        UserEntity arjun = recruiter("arjun@datapath.com", "Arjun Kapoor", dataPath, "Head of Hiring");
        UserEntity meera = recruiter("meera@orbitstudios.com", "Meera Iyer", orbit, "Founding Engineer");

        // ---- 3 seekers ----
        UserEntity priya = seeker("priya@demo.com", "Priya Sharma");
        UserEntity rohan = seeker("rohan@demo.com", "Rohan Verma");
        UserEntity aisha = seeker("aisha@demo.com", "Aisha Khan");

        // ---- upload resumes for the 3 seekers ----
        Long priyaVersion = uploadAndScore(priya, sampleResume("priya"), "Backend focus v1");
        Long rohanVersion = uploadAndScore(rohan, sampleResume("rohan"), "Full-stack v1");
        Long aishaVersion = uploadAndScore(aisha, sampleResume("aisha"), "ML + data v1");

        // ---- 6 jobs across the 3 recruiters ----
        Long job1 = job(riya, techNova, "Java Backend Intern", "Delhi", "Internship", 0, 1, List.of(
                skill("Java", "MUST_HAVE", "10"),
                skill("Spring Boot", "MUST_HAVE", "8"),
                skill("MySQL", "MUST_HAVE", "6"),
                skill("Docker", "NICE_TO_HAVE", "3")));
        Long job2 = job(riya, techNova, "Senior Java Engineer", "Bangalore", "Full-time", 3, 7, List.of(
                skill("Java", "MUST_HAVE", "10"),
                skill("Spring Boot", "MUST_HAVE", "8"),
                skill("PostgreSQL", "MUST_HAVE", "7"),
                skill("Kafka", "MUST_HAVE", "6"),
                skill("Kubernetes", "NICE_TO_HAVE", "4"),
                skill("Go", "NICE_TO_HAVE", "3")));
        Long job3 = job(arjun, dataPath, "Data Engineer", "Remote", "Full-time", 2, 5, List.of(
                skill("Python", "MUST_HAVE", "10"),
                skill("PostgreSQL", "MUST_HAVE", "8"),
                skill("AWS", "MUST_HAVE", "7"),
                skill("Pandas", "MUST_HAVE", "6"),
                skill("Kafka", "NICE_TO_HAVE", "4"),
                skill("Kubernetes", "NICE_TO_HAVE", "3")));
        Long job4 = job(arjun, dataPath, "MLOps Engineer", "Remote", "Full-time", 3, 6, List.of(
                skill("Python", "MUST_HAVE", "10"),
                skill("Docker", "MUST_HAVE", "7"),
                skill("Kubernetes", "MUST_HAVE", "7"),
                skill("AWS", "MUST_HAVE", "6"),
                skill("TensorFlow", "NICE_TO_HAVE", "4")));
        Long job5 = job(meera, orbit, "Game Developer (Unity)", "Remote", "Full-time", 1, 4, List.of(
                skill("C#", "MUST_HAVE", "10"),
                skill("Unity", "MUST_HAVE", "9"),
                skill("C++", "NICE_TO_HAVE", "5"),
                skill("Docker", "NICE_TO_HAVE", "3")));
        Long job6 = job(meera, orbit, "Backend Engineer (Go)", "Remote", "Full-time", 2, 5, List.of(
                skill("Go", "MUST_HAVE", "10"),
                skill("PostgreSQL", "MUST_HAVE", "7"),
                skill("Docker", "MUST_HAVE", "6"),
                skill("Kubernetes", "NICE_TO_HAVE", "5")));

        // ---- applications: each seeker applies to multiple jobs ----
        // Priya (Java) → strong fit on job1, decent on job2, weak on job3
        applyAs(priya, priyaVersion, job1, com.resumatch.common.ApplicationStatus.SHORTLISTED);
        applyAs(priya, priyaVersion, job2, com.resumatch.common.ApplicationStatus.UNDER_REVIEW);
        applyAs(priya, priyaVersion, job3, com.resumatch.common.ApplicationStatus.APPLIED);
        applyAs(priya, priyaVersion, job6, com.resumatch.common.ApplicationStatus.APPLIED);

        // Rohan (full-stack) → mixed results
        applyAs(rohan, rohanVersion, job1, com.resumatch.common.ApplicationStatus.APPLIED);
        applyAs(rohan, rohanVersion, job3, com.resumatch.common.ApplicationStatus.UNDER_REVIEW);
        applyAs(rohan, rohanVersion, job4, com.resumatch.common.ApplicationStatus.APPLIED);

        // Aisha (data) → strong on data jobs, irrelevant on others
        applyAs(aisha, aishaVersion, job3, com.resumatch.common.ApplicationStatus.SHORTLISTED);
        applyAs(aisha, aishaVersion, job4, com.resumatch.common.ApplicationStatus.UNDER_REVIEW);
        applyAs(aisha, aishaVersion, job1, com.resumatch.common.ApplicationStatus.REJECTED);

        // ---- a few applications on each of the "less popular" jobs ----
        // Priya applies to game jobs (low fit, demo material for the "weak match" badge)
        applyAs(priya, priyaVersion, job5, com.resumatch.common.ApplicationStatus.APPLIED);
        applyAs(rohan, rohanVersion, job5, com.resumatch.common.ApplicationStatus.APPLIED);
        applyAs(aisha, aishaVersion, job5, com.resumatch.common.ApplicationStatus.APPLIED);
        applyAs(rohan, rohanVersion, job6, com.resumatch.common.ApplicationStatus.SHORTLISTED);

        // ---- one job report (so admin moderation has something to show) ----
        JobReportEntity report = new JobReportEntity();
        report.jobId = job5;
        report.reportedByUserId = priya.id;
        report.reason = "MISLEADING_SKILLS";
        report.otherReasonText = "Posted Unity role but description mostly talks about backend.";
        report.reportStatus = "PENDING";
        reportRepo.save(report);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("alreadySeeded", false);
        stats.put("seekers", 3);
        stats.put("recruiters", 3);
        stats.put("companies", 3);
        stats.put("jobs", 6);
        stats.put("applications", 15);
        stats.put("pendingReports", 1);
        stats.put("demoAccounts", Map.of(
                "seeker1", "priya@demo.com / Demo123pass (Java backend)",
                "seeker2", "rohan@demo.com / Demo123pass (Full-stack)",
                "seeker3", "aisha@demo.com / Demo123pass (Data/ML)",
                "recruiter1", "riya@technova.com / Demo123pass",
                "recruiter2", "arjun@datapath.com / Demo123pass",
                "recruiter3", "meera@orbitstudios.com / Demo123pass",
                "admin", "admin@resumatch.local / Admin@123"));
        return ApiResponse.ok("Demo data seeded", stats);
    }

    @PostMapping("/reset-demo-data")
    @Transactional
    public ApiResponse<Map<String, Object>> reset(HttpSession session) {
        UserEntity admin = authService.requireUser(session);
        AuthContext.requireRole(session, "ADMIN");
        // wipe in FK-safe order: every child of `users` first, then users.
        // Notifications reference users (FK to user_id), so they must be deleted
        // BEFORE the user they point at. Same for application history, etc.
        reportRepo.deleteAllInBatch();
        historyRepo.deleteAllInBatch();
        notificationRepo.deleteAllInBatch();
        appRepo.deleteAllInBatch();
        companyScoreRepo.deleteAllInBatch();
        generalScoreRepo.deleteAllInBatch();
        flagRepo.deleteAllInBatch();
        extractionRepo.deleteAllInBatch();
        versionRepo.deleteAllInBatch();
        resumeRepo.deleteAllInBatch();
        jobSkillRepo.deleteAllInBatch();
        jobRepo.deleteAllInBatch();
        recruiterRepo.deleteAllInBatch();
        seekerRepo.deleteAllInBatch();
        companyRepo.deleteAllInBatch();
        // capture the admin email before deletion so we can re-create with a unique one
        // if the previous run left the email collision (defensive)
        String adminEmail = admin.email;
        userRepo.deleteAllInBatch();
        // force the DELETE statements to actually hit the DB before we INSERT.
        // Without this, H2 sees the cached admin row from the same transaction
        // and rejects the new admin insert as a duplicate email.
        userRepo.flush();

        // re-create the admin
        com.resumatch.auth.PasswordService pw = new com.resumatch.auth.PasswordService();
        UserEntity freshAdmin = new UserEntity();
        freshAdmin.role = com.resumatch.common.Role.ADMIN;
        freshAdmin.displayName = "Admin";
        freshAdmin.email = adminEmail;
        freshAdmin.phone = "0000000000";
        freshAdmin.passwordHash = pw.hash("Admin@123");
        freshAdmin.accountStatus = AccountStatus.ACTIVE;
        freshAdmin.emailVerified = true;
        userRepo.saveAndFlush(freshAdmin);

        // The previous admin (whose ID is in the session) has been deleted.
        // Invalidate the session so the browser doesn't keep a cookie pointing
        // at a non-existent user. The client will land back on the login page.
        session.invalidate();
        return ApiResponse.ok("Wiped. Please sign in again with admin@resumatch.local / Admin@123, then click 'Seed demo data'.",
                Map.of("wiped", true, "credentials", "admin@resumatch.local / Admin@123"));
    }
    // ---- helpers ----

    private CompanyEntity company(String name, String description) {
        CompanyEntity c = new CompanyEntity();
        c.companyName = name;
        c.companyDescription = description;
        return companyRepo.save(c);
    }

    private UserEntity seeker(String email, String displayName) {
        com.resumatch.auth.PasswordService pw = new com.resumatch.auth.PasswordService();
        UserEntity u = new UserEntity();
        u.role = com.resumatch.common.Role.SEEKER;
        u.displayName = displayName;
        u.email = email;
        u.phone = "9000000" + (userRepo.count() + 100);
        u.passwordHash = pw.hash("Demo123pass");
        u.accountStatus = AccountStatus.ACTIVE;
        u = userRepo.save(u);
        SeekerProfileEntity sp = new SeekerProfileEntity();
        sp.userId = u.id;
        sp.profileCompletionPct = 75;
        sp.location = "Bengaluru, India";
        seekerRepo.save(sp);
        return u;
    }

    private UserEntity recruiter(String email, String name, CompanyEntity c, String designation) {
        com.resumatch.auth.PasswordService pw = new com.resumatch.auth.PasswordService();
        UserEntity u = new UserEntity();
        u.role = com.resumatch.common.Role.RECRUITER;
        u.displayName = name;
        u.email = email;
        u.phone = "9000000" + (userRepo.count() + 200);
        u.passwordHash = pw.hash("Demo123pass");
        u.accountStatus = AccountStatus.ACTIVE;
        u = userRepo.save(u);
        RecruiterProfileEntity rp = new RecruiterProfileEntity();
        rp.userId = u.id;
        rp.companyId = c.id;
        rp.recruiterName = name;
        rp.designation = designation;
        rp.profileCompletionPct = 80;
        recruiterRepo.save(rp);
        return u;
    }

    private JobSkillEntity skill(String name, String importance, String weight) {
        JobSkillEntity js = new JobSkillEntity();
        js.rawSkillText = name;
        js.normalizedSkillText = com.resumatch.ats.TextNormalizer.skillKey(name);
        js.importance = "NICE_TO_HAVE".equals(importance)
                ? com.resumatch.common.SkillImportance.NICE_TO_HAVE
                : com.resumatch.common.SkillImportance.MUST_HAVE;
        js.weight = new BigDecimal(weight);
        return js;
    }

    private Long job(UserEntity recruiter, CompanyEntity c, String title, String location, String type,
                     int min, int max, List<JobSkillEntity> skills) {
        JobPostingEntity j = new JobPostingEntity();
        j.recruiterId = recruiter.id;
        j.companyId = c.id;
        j.title = title;
        j.description = defaultDescription(title);
        j.location = location;
        j.employmentType = type;
        j.minExperienceYears = min;
        j.maxExperienceYears = max;
        j.status = JobStatus.OPEN;
        j = jobRepo.save(j);
        for (JobSkillEntity s : skills) {
            s.jobId = j.id;
            jobSkillRepo.save(s);
        }
        return j.id;
    }

    private String defaultDescription(String title) {
        return "We're hiring a " + title + ". You'll work on a small, fast-moving team, own features end-to-end, and ship production-quality code. " +
                "We value clear written communication, strong fundamentals, and a learning mindset. " +
                "Mentorship from senior engineers, flexible hours, and a respectful culture.";
    }

    private void applyAs(UserEntity seeker, Long versionId, Long jobId,
                        com.resumatch.common.ApplicationStatus status) {
        ApplicationEntity a = appService.apply(seeker, jobId, versionId);
        // Belt-and-suspenders: appService.apply() already computes the company
        // score, but if anything in that chain rolled back we still want one.
        // We do it again here against a fresh extraction so a missing score
        // can never silently leave the "My scores" page empty.
        try {
            var ex = extractionRepo.findFirstByResumeVersionIdOrderByCreatedAtDesc(versionId).orElse(null);
            if (ex != null && companyScoreRepo.findFirstByApplicationIdOrderByComputedAtDesc(a.id).isEmpty()) {
                appService.computeAndStoreCompanyScore(a, ex, null);
            }
        } catch (Exception e) {
            log.warn("Could not backfill company score for app {}: {}", a.id, e.getMessage());
        }
        if (status != com.resumatch.common.ApplicationStatus.APPLIED) {
            // Apply creates the row in APPLIED. To move it to SHORTLISTED/UNDER_REVIEW/REJECTED,
            // we set it directly (bypassing the recruiter-only authorization) and write the
            // status history entry — same as the controller would, just for the admin seeder.
            ApplicationStatus old = a.status;
            a.status = status;
            a.statusUpdatedAt = java.time.Instant.now();
            appRepo.save(a);
            ApplicationStatusHistoryEntity h = new ApplicationStatusHistoryEntity();
            h.applicationId = a.id;
            h.oldStatus = old.name();
            h.newStatus = status.name();
            h.changedByUserId = seeker.id; // pretend the seeker sees their own update
            h.notes = "Seeded by admin demo data";
            historyRepo.save(h);
        }
    }

    private Long uploadAndScore(UserEntity user, String resumeText, String versionLabel) {
        // write the resume to a temp file in the configured storage path, then call
        // the parser + general score service directly to bypass the multipart upload path.
        try {
            Path dir = Paths.get(storagePath, "resumes", String.valueOf(user.id));
            Files.createDirectories(dir);
            String fileName = System.currentTimeMillis() + "_" + versionLabel.replaceAll("\\s+", "_") + ".txt";
            Path file = dir.resolve(fileName);
            Files.writeString(file, resumeText);

            // build the resume stream parent + version
            var resume = resumeRepo.findFirstBySeekerId(user.id).orElseGet(() -> {
                ResumeEntity r = new ResumeEntity();
                r.seekerId = user.id;
                return resumeRepo.save(r);
            });
            int nextVersion = versionRepo.findByResumeIdOrderByVersionNumberDesc(resume.id).stream()
                    .findFirst().map(v -> v.versionNumber + 1).orElse(1);
            ResumeVersionEntity ver = new ResumeVersionEntity();
            ver.resumeId = resume.id;
            ver.versionNumber = nextVersion;
            ver.versionLabel = versionLabel;
            ver.originalFileName = fileName;
            ver.storagePath = file.toString();
            ver.mimeType = "text/plain";
            ver.fileSize = resumeText.length();
            ver = versionRepo.save(ver);
            resume.activeVersionId = ver.id;
            resumeRepo.save(resume);

            // parse
            ResumeParser.ParsedResult parsed = parser.parse(file, ver.mimeType, ver.originalFileName);
            String text = parsed.text == null ? "" : parsed.text;

            // extract
            ResumeExtractionEntity ex = new ResumeExtractionEntity();
            ex.resumeVersionId = ver.id;
            ex.extractedText = text;
            ex.extractedName = com.resumatch.ats.TextNormalizer.extractName(text).orElse(user.displayName);
            ex.extractedEmail = com.resumatch.ats.TextNormalizer.extractEmail(text).orElse(user.email);
            ex.extractedPhone = com.resumatch.ats.TextNormalizer.extractPhone(text).orElse(user.phone);
            ex.experienceYears = com.resumatch.ats.TextNormalizer.estimateYears(text);
            ex.parsingQualityScore = parsed.qualityScore;

            com.resumatch.ats.SectionDetector.SectionMap sm = com.resumatch.ats.SectionDetector.detect(text);
            com.resumatch.ats.AliasIndex.SkillScan scan = aliasIndexService.get().scan(text);
            ex.skillsJson = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(scan.canonicalNames);
            ex.sectionsJson = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(sm.found);
            ex = extractionRepo.save(ex);

            // flags
            List<ResumeFormatFlagEntity> flags = com.resumatch.ats.FormattingChecker.check(ver.id, text, sm);
            if (!flags.isEmpty()) flagRepo.saveAll(flags);

            // general score
            com.resumatch.ats.GeneralScoreService.ScoreOutput out = generalScoreService.compute(ex, flags, scan);
            com.resumatch.ats.GeneralScoreEntity gs = new com.resumatch.ats.GeneralScoreEntity();
            gs.resumeVersionId = ver.id;
            gs.totalScore = out.total;
            gs.formattingScore = out.formatting;
            gs.completenessScore = out.completeness;
            gs.structureScore = out.structure;
            gs.skillsScore = out.skills;
            gs.experienceScore = out.experience;
            gs.explanationJson = out.explanationJson;
            generalScoreRepo.save(gs);

            return ver.id;
        } catch (Exception e) {
            throw new RuntimeException("Failed to seed resume for " + user.email + ": " + e.getMessage(), e);
        }
    }

    // ---- sample resume text ----
    private String sampleResume(String who) {
        if ("priya".equals(who)) {
            return "Priya Sharma\n" +
                    "Backend Developer\n" +
                    "Email: priya@demo.com\n" +
                    "Phone: +91 90000 00001\n\n" +
                    "SUMMARY\n" +
                    "Final year B.Tech student with strong Java fundamentals and hands-on Spring Boot project experience.\n\n" +
                    "SKILLS\n" +
                    "Java, Spring Boot, MySQL, REST API, Git, Linux, Maven, JUnit, HTML, CSS, JavaScript, React, Docker, Kubernetes, AWS, MongoDB, Redis, Kafka, Python, Hibernate, Gradle, PostgreSQL, Go, Microservices\n\n" +
                    "PROJECTS\n" +
                    "Online Bookstore — Built a full-stack Spring Boot + MySQL app with REST APIs and JWT auth.\n" +
                    "URL Shortener — Node.js + Redis service handling 5k req/s.\n\n" +
                    "EXPERIENCE\n" +
                    "Backend Intern, AcmeSoft — June 2024 to December 2024\n" +
                    "Built Spring Boot microservices, optimized MySQL queries by 40%.\n\n" +
                    "Software Engineer Intern, BetaLabs — January 2025 to June 2025\n" +
                    "Developed Kafka-based event pipelines; introduced Docker and CI/CD.\n\n" +
                    "EDUCATION\n" +
                    "B.Tech in Computer Science, IIT, 2022-2026\n";
        }
        if ("rohan".equals(who)) {
            return "Rohan Verma\n" +
                    "Full-Stack Developer\n" +
                    "Email: rohan@demo.com\n" +
                    "Phone: +91 90000 00002\n\n" +
                    "SUMMARY\n" +
                    "Full-stack developer with 2 years of experience building production web applications.\n\n" +
                    "SKILLS\n" +
                    "JavaScript, TypeScript, React, Node.js, Express, PostgreSQL, MongoDB, Docker, AWS, REST API, GraphQL, Redis, Java, Python, HTML, CSS, Git, Linux, Kubernetes, Kafka\n\n" +
                    "PROJECTS\n" +
                    "Realtime Chat App — WebSocket-based chat with React frontend and Node.js backend.\n" +
                    "E-commerce Platform — Full-stack TypeScript app with Stripe integration.\n\n" +
                    "EXPERIENCE\n" +
                    "Full-Stack Developer, StartupX — March 2023 to present\n" +
                    "Built and maintained multiple React + Node.js applications.\n\n" +
                    "Software Engineer Intern, WebCorp — June 2022 to February 2023\n" +
                    "Developed customer-facing features in React and Express.\n\n" +
                    "EDUCATION\n" +
                    "B.Tech in Information Technology, NIT, 2019-2023\n";
        }
        // aisha
        return "Aisha Khan\n" +
                "Data / ML Engineer\n" +
                "Email: aisha@demo.com\n" +
                "Phone: +91 90000 00003\n\n" +
                "SUMMARY\n" +
                "Data engineer with 3 years of experience in ML pipelines, data warehousing, and production model deployment.\n\n" +
                "SKILLS\n" +
                "Python, Pandas, NumPy, PostgreSQL, AWS, Docker, Kubernetes, Kafka, TensorFlow, PyTorch, SQL, Spark, Airflow, Terraform, Git, Linux, REST API, MongoDB, Redis, Go\n\n" +
                "PROJECTS\n" +
                "Realtime ML Inference Platform — Built low-latency ML serving on Kubernetes with TensorFlow.\n" +
                "Customer Churn Pipeline — Airflow + dbt + BigQuery pipeline feeding a churn model.\n\n" +
                "EXPERIENCE\n" +
                "Data Engineer, FinTech Co — April 2022 to present\n" +
                "Built production ML pipelines on AWS; reduced batch latency by 60%.\n\n" +
                "ML Engineer Intern, Analytics Inc — June 2021 to March 2022\n" +
                "Deployed first churn model; introduced Airflow for orchestration.\n\n" +
                "EDUCATION\n" +
                "B.Tech in Computer Science, BITS, 2018-2022\n";
    }
}
