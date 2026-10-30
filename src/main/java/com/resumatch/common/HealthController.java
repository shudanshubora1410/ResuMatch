package com.resumatch.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Liveness endpoint for cloud platforms (Render, Railway, Koyeb, Docker healthchecks).
 *
 * <p>This endpoint deliberately touches NO database, NO repository, and NO service bean.
 * It only returns static values plus the configured port. That is intentional:
 * a healthcheck must answer in milliseconds. If the healthcheck required JPA,
 * the very first request after a cold start would trigger the full Hibernate
 * bootstrap inside the healthcheck itself, blowing past the platform's
 * healthcheck timeout and producing a 502 Bad Gateway.
 *
 * <p>Tomcat starts this controller immediately because it has no dependencies
 * beyond the Environment, so it is always ready the moment the port is open.
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final String port;

    public HealthController(@Value("${server.port:8080}") String port) {
        this.port = port;
    }

    @GetMapping
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "app", "ResuMatch",
                "port", port
        );
    }

    /**
     * Alias so older healthcheck configurations pointing at /health still work.
     */
    @GetMapping("/live")
    public Map<String, Object> live() {
        return health();
    }
}
