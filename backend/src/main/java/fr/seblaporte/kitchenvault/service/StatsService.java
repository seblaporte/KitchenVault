package fr.seblaporte.kitchenvault.service;

import fr.seblaporte.kitchenvault.entity.SyncStatus;
import fr.seblaporte.kitchenvault.repository.CollectionRepository;
import fr.seblaporte.kitchenvault.repository.RecipeRepository;
import fr.seblaporte.kitchenvault.repository.SyncRunRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@Transactional(readOnly = true)
public class StatsService {

    private final RecipeRepository recipeRepository;
    private final CollectionRepository collectionRepository;
    private final SyncRunRepository syncRunRepository;

    public StatsService(
            RecipeRepository recipeRepository,
            CollectionRepository collectionRepository,
            SyncRunRepository syncRunRepository
    ) {
        this.recipeRepository = recipeRepository;
        this.collectionRepository = collectionRepository;
        this.syncRunRepository = syncRunRepository;
    }

    public record AdminStats(long recipeCount, long collectionCount, Instant lastSuccessfulSyncAt) {}

    public AdminStats getAdminStats() {
        long recipeCount = recipeRepository.count();
        long collectionCount = collectionRepository.count();
        Instant lastSuccessfulSyncAt = syncRunRepository
                .findTopByStatusOrderByStartedAtDesc(SyncStatus.SUCCESS)
                .map(run -> run.getCompletedAt())
                .orElse(null);

        return new AdminStats(recipeCount, collectionCount, lastSuccessfulSyncAt);
    }
}
