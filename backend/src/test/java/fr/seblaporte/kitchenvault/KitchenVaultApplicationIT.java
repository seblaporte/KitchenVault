package fr.seblaporte.kitchenvault;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Boots the full Spring context (all beans, including self-referencing @Lazy proxies like
 * SyncService, and the @Scheduled/@Async infrastructure) against a real Testcontainers Postgres.
 * A pure Mockito unit test cannot catch wiring issues such as circular bean dependencies —
 * this is the safety net for that class of bug.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class KitchenVaultApplicationIT {

    @Test
    void contextLoads() {
    }
}
