package com.resumatch.admin;

import com.resumatch.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Auto-seeds demo data on startup when the deployment says so.
 *
 * <p>Why this exists: on cloud platforms with an ephemeral filesystem
 * (Render free tier, Railway, Koyeb, Fly.io) the H2 file database is wiped on
 * every redeploy and every spin-down. A demo link that shows an empty app is
 * useless. Setting {@code RESUMATCH_AUTO_SEED=true} makes the app populate the
 * demo dataset automatically on boot, so the public URL is always useful.
 *
 * <p>It is OFF by default, so it never interferes with local development or
 * with a real MySQL deployment.
 *
 * <p>Why {@link ApplicationRunner} and not {@code CommandLineRunner}:
 * a CommandLineRunner executes <em>during</em> the application context refresh.
 * The seed routine touches JPA repositories, which caused a bean-creation
 * race ("Singleton bean creation not allowed while singletons of this factory
 * are in destruction") because the DataSource initializer was still starting.
 * ApplicationRunner beans run after the context is fully refreshed and the
 * web server is listening, so every bean is safely available.
 */
@Component
public class DemoAutoSeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoAutoSeedRunner.class);

    private final UserRepository userRepo;
    private final AdminSeedController seedController;
    private final boolean autoSeed;

    public DemoAutoSeedRunner(
            UserRepository userRepo,
            AdminSeedController seedController,
            @Value("${resumatch.auto-seed:false}") boolean autoSeed) {
        this.userRepo = userRepo;
        this.seedController = seedController;
        this.autoSeed = autoSeed;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!autoSeed) {
            log.info("[DemoAutoSeed] RESUMATCH_AUTO_SEED not set - skipping auto-seed");
            return;
        }

        boolean alreadySeeded = userRepo.findByEmail("priya@demo.com").isPresent()
                || userRepo.findByEmail("riya@technova.com").isPresent();

        if (alreadySeeded) {
            log.info("[DemoAutoSeed] Demo data already present - nothing to do");
            return;
        }

        try {
            log.info("[DemoAutoSeed] Empty database detected - seeding demo data...");
            seedController.seedDemoDataInternal();
            log.info("[DemoAutoSeed] Demo data seeded successfully. "
                            + "Log in as priya@demo.com / Demo123pass (seeker), "
                            + "riya@technova.com / Demo123pass (recruiter), "
                            + "admin@resumatch.local / Admin@123 (admin)");
        } catch (Exception e) {
            // Never fail the boot because demo data could not be created.
            log.warn("[DemoAutoSeed] Auto-seed failed (app will still run): {}", e.getMessage());
        }
    }
}
