package fr.seblaporte.mycookidoo.repository;

import fr.seblaporte.mycookidoo.entity.SyncRun;
import fr.seblaporte.mycookidoo.entity.SyncStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers
class SyncRunRepositoryIT {

    @Autowired SyncRunRepository syncRunRepository;

    @Test
    void save_andFindLatest_returnsLastByStartedAt() {
        SyncRun run1 = SyncRun.start();
        run1.complete(2, 10);
        SyncRun run2 = SyncRun.start();
        run2.fail("Network error");

        syncRunRepository.save(run1);
        syncRunRepository.save(run2);

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

        Optional<SyncRun> result = syncRunRepository.findTopByStatusOrderByStartedAtDesc(SyncStatus.SUCCESS);
        assertThat(result).isPresent();
        assertThat(result.get().getCollectionsSynced()).isEqualTo(1);
        assertThat(result.get().getRecipesSynced()).isEqualTo(5);
    }

    @Test
    void existsByStatus_RUNNING_returnsTrueWhenExists() {
        SyncRun run = SyncRun.start();
        syncRunRepository.save(run);

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

        Page<SyncRun> page = syncRunRepository.findAllByOrderByStartedAtDesc(PageRequest.of(0, 3));
        assertThat(page.getContent()).hasSize(3);
        assertThat(page.getTotalElements()).isEqualTo(5);
        assertThat(page.getTotalPages()).isEqualTo(2);
    }
}
