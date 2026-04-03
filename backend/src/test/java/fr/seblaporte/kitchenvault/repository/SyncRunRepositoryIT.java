package fr.seblaporte.kitchenvault.repository;

import fr.seblaporte.kitchenvault.entity.SyncRun;
import fr.seblaporte.kitchenvault.entity.SyncStatus;
import org.assertj.db.type.Table;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SyncRunRepositoryIT {

    @Autowired SyncRunRepository syncRunRepository;
    @Autowired DataSource dataSource;

    @BeforeEach
    void cleanUp() {
        syncRunRepository.deleteAll();
    }

    @Test
    void start_persistsRowWithRunningStatus() {
        syncRunRepository.save(SyncRun.start());

        org.assertj.db.api.Assertions.assertThat(new Table(dataSource, "sync_run"))
                .hasNumberOfRows(1)
                .row(0)
                    .value("status").isEqualTo("RUNNING")
                    .value("started_at").isNotNull()
                    .value("completed_at").isNull()
                    .value("collections_synced").isNull()
                    .value("recipes_synced").isNull()
                    .value("error_message").isNull();
    }

    @Test
    void complete_updatesStatusAndCounts() {
        SyncRun run = SyncRun.start();
        run.complete(3, 42);
        syncRunRepository.save(run);

        org.assertj.db.api.Assertions.assertThat(new Table(dataSource, "sync_run"))
                .hasNumberOfRows(1)
                .row(0)
                    .value("status").isEqualTo("SUCCESS")
                    .value("completed_at").isNotNull()
                    .value("collections_synced").isEqualTo(3)
                    .value("recipes_synced").isEqualTo(42)
                    .value("error_message").isNull();
    }

    @Test
    void fail_updatesStatusAndErrorMessage() {
        SyncRun run = SyncRun.start();
        run.fail("Network timeout");
        syncRunRepository.save(run);

        org.assertj.db.api.Assertions.assertThat(new Table(dataSource, "sync_run"))
                .hasNumberOfRows(1)
                .row(0)
                    .value("status").isEqualTo("FAILED")
                    .value("completed_at").isNotNull()
                    .value("error_message").isEqualTo("Network timeout");
    }

    @Test
    void partialSuccess_updatesStatusCountsAndMessage() {
        SyncRun run = SyncRun.start();
        run.partialSuccess(2, 15, "3 recipes skipped");
        syncRunRepository.save(run);

        org.assertj.db.api.Assertions.assertThat(new Table(dataSource, "sync_run"))
                .hasNumberOfRows(1)
                .row(0)
                    .value("status").isEqualTo("PARTIAL")
                    .value("collections_synced").isEqualTo(2)
                    .value("recipes_synced").isEqualTo(15)
                    .value("error_message").isEqualTo("3 recipes skipped");
    }

    @Test
    void findTopByOrderByStartedAtDesc_returnsLatestByStartedAt() {
        SyncRun run1 = SyncRun.start();
        run1.complete(2, 10);
        SyncRun run2 = SyncRun.start();
        run2.fail("Network error");

        syncRunRepository.save(run1);
        syncRunRepository.save(run2);

        org.assertj.db.api.Assertions.assertThat(new Table(dataSource, "sync_run"))
                .hasNumberOfRows(2);

        Optional<SyncRun> latest = syncRunRepository.findTopByOrderByStartedAtDesc();
        assertThat(latest).isPresent();
        assertThat(latest.get().getStatus()).isEqualTo(SyncStatus.FAILED);
    }

    @Test
    void findTopByStatus_SUCCESS_returnsLastSuccessful() {
        SyncRun failed = SyncRun.start();
        failed.fail("error");
        SyncRun success = SyncRun.start();
        success.complete(1, 5);

        syncRunRepository.save(failed);
        syncRunRepository.save(success);

        org.assertj.db.api.Assertions.assertThat(new Table(dataSource, "sync_run"))
                .hasNumberOfRows(2);

        Optional<SyncRun> result = syncRunRepository.findTopByStatusOrderByStartedAtDesc(SyncStatus.SUCCESS);
        assertThat(result).isPresent();
        assertThat(result.get().getCollectionsSynced()).isEqualTo(1);
        assertThat(result.get().getRecipesSynced()).isEqualTo(5);
    }

    @Test
    void existsByStatus_RUNNING_returnsTrueWhenRunningExists() {
        syncRunRepository.save(SyncRun.start());

        org.assertj.db.api.Assertions.assertThat(new Table(dataSource, "sync_run"))
                .hasNumberOfRows(1)
                .column("status")
                    .hasValues("RUNNING");

        assertThat(syncRunRepository.existsByStatus(SyncStatus.RUNNING)).isTrue();
        assertThat(syncRunRepository.existsByStatus(SyncStatus.SUCCESS)).isFalse();
    }

    @Test
    void findAllByOrderByStartedAtDesc_returnsPaginatedResults() {
        for (int i = 0; i < 5; i++) {
            SyncRun run = SyncRun.start();
            run.complete(1, i);
            syncRunRepository.save(run);
        }

        org.assertj.db.api.Assertions.assertThat(new Table(dataSource, "sync_run"))
                .hasNumberOfRows(5);

        Page<SyncRun> page = syncRunRepository.findAllByOrderByStartedAtDesc(PageRequest.of(0, 3));
        assertThat(page.getContent()).hasSize(3);
        assertThat(page.getTotalElements()).isEqualTo(5);
        assertThat(page.getTotalPages()).isEqualTo(2);
    }
}
