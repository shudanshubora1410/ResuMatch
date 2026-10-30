package com.resumatch.skills;

import com.resumatch.ats.TextNormalizer;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Seeds canonical skills and common aliases on first boot.
 */
@Component
@Order(1)
public class SkillSeed implements CommandLineRunner {
    private final SkillRepository skillRepo;
    private final SkillAliasRepository aliasRepo;
    private final AliasIndexService aliasIndexService;

    public SkillSeed(SkillRepository skillRepo, SkillAliasRepository aliasRepo, AliasIndexService aliasIndexService) {
        this.skillRepo = skillRepo;
        this.aliasRepo = aliasRepo;
        this.aliasIndexService = aliasIndexService;
    }

    @Override
    public void run(String... args) {
        if (skillRepo.count() > 0) return;

        record Seed(String canonical, String category, List<String> aliases) {}
        List<Seed> seeds = List.of(
                new Seed("Java", "language", List.of("java", "core java", "java se", "java ee", "jdk")),
                new Seed("Spring Boot", "framework", List.of("spring boot", "springboot", "spring-boot", "spring framework", "spring")),
                new Seed("Hibernate", "framework", List.of("hibernate", "jpa", "spring data jpa")),
                new Seed("MySQL", "database", List.of("mysql", "mysql server", "my sql")),
                new Seed("PostgreSQL", "database", List.of("postgresql", "postgres", "psql")),
                new Seed("MongoDB", "database", List.of("mongodb", "mongo")),
                new Seed("Redis", "database", List.of("redis")),
                new Seed("HTML", "frontend", List.of("html", "html5")),
                new Seed("CSS", "frontend", List.of("css", "css3")),
                new Seed("JavaScript", "frontend", List.of("javascript", "js", "es6", "ecmascript")),
                new Seed("TypeScript", "frontend", List.of("typescript", "ts")),
                new Seed("React", "frontend", List.of("react", "reactjs", "react.js")),
                new Seed("Angular", "frontend", List.of("angular", "angularjs")),
                new Seed("Vue", "frontend", List.of("vue", "vuejs", "vue.js")),
                new Seed("Node.js", "backend", List.of("nodejs", "node js", "node.js", "node")),
                new Seed("Express", "backend", List.of("express", "expressjs", "express.js")),
                new Seed("Python", "language", List.of("python", "py")),
                new Seed("Django", "framework", List.of("django")),
                new Seed("Flask", "framework", List.of("flask")),
                new Seed("C++", "language", List.of("c++", "cpp", "c plus plus")),
                new Seed("C#", "language", List.of("c#", "csharp", "c sharp")),
                new Seed(".NET", "framework", List.of(".net", "dotnet", "asp.net", "aspnet")),
                new Seed("Go", "language", List.of("golang", "go")),
                new Seed("Rust", "language", List.of("rust")),
                new Seed("Docker", "devops", List.of("docker", "dockerfile")),
                new Seed("Kubernetes", "devops", List.of("kubernetes", "k8s")),
                new Seed("AWS", "cloud", List.of("aws", "amazon web services")),
                new Seed("Azure", "cloud", List.of("azure", "microsoft azure")),
                new Seed("GCP", "cloud", List.of("gcp", "google cloud")),
                new Seed("Git", "tool", List.of("git", "github", "gitlab", "bitbucket")),
                new Seed("Linux", "os", List.of("linux", "ubuntu")),
                new Seed("REST API", "concept", List.of("rest", "rest api", "restful", "restful api")),
                new Seed("GraphQL", "concept", List.of("graphql")),
                new Seed("Microservices", "concept", List.of("microservices", "micro services", "microservice")),
                new Seed("Kafka", "messaging", List.of("kafka", "apache kafka")),
                new Seed("RabbitMQ", "messaging", List.of("rabbitmq", "rabbit mq")),
                new Seed("CI/CD", "devops", List.of("ci/cd", "cicd", "jenkins", "github actions")),
                new Seed("Maven", "tool", List.of("maven", "mvn")),
                new Seed("Gradle", "tool", List.of("gradle")),
                new Seed("JUnit", "testing", List.of("junit", "junit5")),
                new Seed("Selenium", "testing", List.of("selenium")),
                new Seed("TensorFlow", "ml", List.of("tensorflow", "tf")),
                new Seed("PyTorch", "ml", List.of("pytorch")),
                new Seed("Pandas", "data", List.of("pandas")),
                new Seed("NumPy", "data", List.of("numpy")),
                new Seed("Excel", "tool", List.of("excel", "ms excel", "microsoft excel")),
                new Seed("Tableau", "data", List.of("tableau")),
                new Seed("Power BI", "data", List.of("power bi", "powerbi"))
        );

        java.util.Set<String> globalSeen = new java.util.HashSet<>();
        for (Seed s : seeds) {
            SkillEntity entity = new SkillEntity();
            entity.canonicalName = s.canonical;
            entity.category = s.category;
            entity.isActive = true;
            SkillEntity saved = skillRepo.save(entity);
            // Dedupe on the normalized form so e.g. "c++", "cpp", "c plus plus"
            // (which all normalize to "cpp") don't violate the unique index.
            // Also dedupe across seeds in case two skills share a normalized
            // alias (e.g. C++ and C# both touched "c").
            java.util.Set<String> seen = new java.util.HashSet<>();
            for (String a : s.aliases) {
                String key = TextNormalizer.skillKey(a);
                if (key.isEmpty()) continue;
                if (!seen.add(key)) continue;
                if (!globalSeen.add(key)) continue;
                SkillAliasEntity al = new SkillAliasEntity();
                al.skillId = saved.id;
                al.aliasText = a;
                al.normalizedAliasText = key;
                try {
                    aliasRepo.save(al);
                } catch (org.springframework.dao.DataIntegrityViolationException ex) {
                    // Unique-index collision despite dedupe: skip and move on
                    // rather than failing the whole boot.
                    System.out.println("[SkillSeed] skipping duplicate alias: " + key);
                }
            }
        }
        aliasIndexService.reload();
    }
}
