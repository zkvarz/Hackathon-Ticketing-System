package com.dataart.tickets.health;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public liveness endpoint (architecture.md §8). Stays unauthenticated once security lands
 * in HTS-013 (FR-A12 allows health/readiness endpoints to be public).
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    public record HealthStatus(String status) {
    }

    @GetMapping
    public HealthStatus health() {
        return new HealthStatus("UP");
    }
}
