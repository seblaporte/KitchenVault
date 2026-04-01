package fr.seblaporte.kitchenvault.service;

import fr.seblaporte.kitchenvault.entity.SyncRun;
import fr.seblaporte.kitchenvault.entity.SyncStatus;
import fr.seblaporte.kitchenvault.repository.CollectionRepository;
import fr.seblaporte.kitchenvault.repository.RecipeRepository;
import fr.seblaporte.kitchenvault.repository.SyncRunRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatsServiceTest {

    @Mock RecipeRepository recipeRepository;
    @Mock CollectionRepository collectionRepository;
    @Mock SyncRunRepository syncRunRepository;
    @InjectMocks StatsService statsService;

    @Test
    void getAdminStats_returnsCorrectCounts() {
        when(recipeRepository.count()).thenReturn(42L);
        when(collectionRepository.count()).thenReturn(5L);
        when(syncRunRepository.findTopByStatusOrderByStartedAtDesc(SyncStatus.SUCCESS))
                .thenReturn(Optional.empty());

        StatsService.AdminStats stats = statsService.getAdminStats();

        assertThat(stats.recipeCount()).isEqualTo(42L);
        assertThat(stats.collectionCount()).isEqualTo(5L);
        assertThat(stats.lastSuccessfulSyncAt()).isNull();
    }

    @Test
    void getAdminStats_withLastSuccessfulSync_returnsCompletedAt() {
        SyncRun run = SyncRun.start();
        run.complete(3, 10);
        Instant expectedTime = run.getCompletedAt();

        when(recipeRepository.count()).thenReturn(10L);
        when(collectionRepository.count()).thenReturn(3L);
        when(syncRunRepository.findTopByStatusOrderByStartedAtDesc(SyncStatus.SUCCESS))
                .thenReturn(Optional.of(run));

        StatsService.AdminStats stats = statsService.getAdminStats();

        assertThat(stats.lastSuccessfulSyncAt()).isEqualTo(expectedTime);
    }
}
