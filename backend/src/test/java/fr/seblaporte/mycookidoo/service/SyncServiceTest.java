package fr.seblaporte.mycookidoo.service;

import fr.seblaporte.mycookidoo.config.CookidooProperties;
import fr.seblaporte.mycookidoo.cookidoo.CookidooServiceClient;
import fr.seblaporte.mycookidoo.cookidoo.model.*;
import fr.seblaporte.mycookidoo.entity.*;
import fr.seblaporte.mycookidoo.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SyncServiceTest {

    @Mock CookidooServiceClient cookidooServiceClient;
    @Mock RecipeRepository recipeRepository;
    @Mock CollectionRepository collectionRepository;
    @Mock CategoryRepository categoryRepository;
    @Mock SyncRunRepository syncRunRepository;

    @Mock CookidooProperties properties;
    @Mock CookidooProperties.ServiceProperties serviceProperties;
    @Mock CookidooProperties.SyncProperties syncProperties;

    @InjectMocks SyncService syncService;

    private SyncRun savedRun;

    @BeforeEach
    void setUp() {
        savedRun = SyncRun.start();
        when(properties.sync()).thenReturn(syncProperties);
        when(syncProperties.resyncAfterHours()).thenReturn(24);
        when(syncRunRepository.save(any(SyncRun.class))).thenAnswer(inv -> {
            savedRun = inv.getArgument(0);
            return savedRun;
        });
    }

    @Test
    void triggerSync_whenNoSyncRunning_savesRunningAndReturnsIt() {
        when(syncRunRepository.existsByStatus(SyncStatus.RUNNING)).thenReturn(false);
        when(cookidooServiceClient.getCollections()).thenReturn(List.of());

        SyncRun run = syncService.triggerSync();

        assertThat(run).isNotNull();
        assertThat(run.getStatus()).isEqualTo(SyncStatus.RUNNING);
        verify(syncRunRepository, atLeastOnce()).save(any());
    }

    @Test
    void triggerSync_whenSyncAlreadyRunning_throwsIllegalStateException() {
        when(syncRunRepository.existsByStatus(SyncStatus.RUNNING)).thenReturn(true);

        assertThatThrownBy(() -> syncService.triggerSync())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already running");
    }

    @Test
    void executeSync_withNoCollections_completesWithZeroCounts() {
        when(cookidooServiceClient.getCollections()).thenReturn(List.of());
        SyncRun run = SyncRun.start();

        syncService.executeSync(run);

        assertThat(run.getStatus()).isEqualTo(SyncStatus.SUCCESS);
        assertThat(run.getCollectionsSynced()).isEqualTo(0);
        assertThat(run.getRecipesSynced()).isEqualTo(0);
    }

    @Test
    void executeSync_newRecipe_fetchesAndPersists() {
        CookidooChapterRecipe chapterRecipe = new CookidooChapterRecipe("r-1", "Tarte", 3600);
        CookidooChapter chapter = new CookidooChapter("Ch1", List.of(chapterRecipe));
        CookidooCollection collection = new CookidooCollection("col-1", "Ma Collection", null, List.of(chapter));
        CookidooRecipeDetails details = makeRecipeDetails("r-1");

        when(cookidooServiceClient.getCollections()).thenReturn(List.of(collection));
        when(cookidooServiceClient.getRecipeById("r-1")).thenReturn(details);
        when(recipeRepository.findById("r-1")).thenReturn(Optional.empty());
        when(collectionRepository.findById("col-1")).thenReturn(Optional.empty());
        when(collectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(recipeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(categoryRepository.findById(any())).thenReturn(Optional.empty());
        when(categoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SyncRun run = SyncRun.start();
        syncService.executeSync(run);

        assertThat(run.getStatus()).isEqualTo(SyncStatus.SUCCESS);
        assertThat(run.getRecipesSynced()).isEqualTo(1);
        assertThat(run.getCollectionsSynced()).isEqualTo(1);
        verify(cookidooServiceClient).getRecipeById("r-1");
        verify(recipeRepository).save(any(Recipe.class));
    }

    @Test
    void executeSync_existingUpToDateRecipe_skipsRefetch() {
        CookidooChapterRecipe chapterRecipe = new CookidooChapterRecipe("r-1", "Tarte", 3600);
        CookidooChapter chapter = new CookidooChapter("Ch1", List.of(chapterRecipe));
        CookidooCollection collection = new CookidooCollection("col-1", "Ma Collection", null, List.of(chapter));

        Recipe existingRecipe = new Recipe("r-1");
        existingRecipe.setName("Tarte");
        existingRecipe.setLastSyncedAt(Instant.now()); // fresh

        when(cookidooServiceClient.getCollections()).thenReturn(List.of(collection));
        when(recipeRepository.findById("r-1")).thenReturn(Optional.of(existingRecipe));
        when(collectionRepository.findById("col-1")).thenReturn(Optional.empty());
        when(collectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SyncRun run = SyncRun.start();
        syncService.executeSync(run);

        verify(cookidooServiceClient, never()).getRecipeById(any());
        assertThat(run.getRecipesSynced()).isEqualTo(0);
    }

    @Test
    void executeSync_whenCookidooServiceFails_marksRunAsFailed() {
        when(cookidooServiceClient.getCollections())
                .thenThrow(new RestClientException("Connection refused"));

        SyncRun run = SyncRun.start();
        syncService.executeSync(run);

        assertThat(run.getStatus()).isEqualTo(SyncStatus.FAILED);
        assertThat(run.getErrorMessage()).contains("Connection refused");
    }

    @Test
    void executeSync_whenSingleRecipeFails_continuesAndCompletes() {
        CookidooChapterRecipe cr1 = new CookidooChapterRecipe("r-1", "Recette 1", 1800);
        CookidooChapterRecipe cr2 = new CookidooChapterRecipe("r-2", "Recette 2", 3600);
        CookidooChapter chapter = new CookidooChapter("Ch", List.of(cr1, cr2));
        CookidooCollection collection = new CookidooCollection("col-1", "Col", null, List.of(chapter));

        when(cookidooServiceClient.getCollections()).thenReturn(List.of(collection));
        when(recipeRepository.findById(any())).thenReturn(Optional.empty());
        when(cookidooServiceClient.getRecipeById("r-1")).thenThrow(new RestClientException("Timeout"));
        when(cookidooServiceClient.getRecipeById("r-2")).thenReturn(makeRecipeDetails("r-2"));
        when(recipeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(categoryRepository.findById(any())).thenReturn(Optional.empty());
        when(categoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(collectionRepository.findById(any())).thenReturn(Optional.empty());
        when(collectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SyncRun run = SyncRun.start();
        syncService.executeSync(run);

        // r-2 succeeded, r-1 failed but was logged and skipped
        assertThat(run.getStatus()).isEqualTo(SyncStatus.SUCCESS);
        assertThat(run.getRecipesSynced()).isEqualTo(1);
    }

    private CookidooRecipeDetails makeRecipeDetails(String id) {
        return new CookidooRecipeDetails(
                id, "Recette Test", "thumb.jpg", "image.jpg",
                "https://cookidoo.ch/recipe/" + id,
                "easy", 4, 900, 3600,
                List.of(), List.of(),
                List.of(new CookidooIngredient("ing-1", "Farine", "200g")),
                List.of(new CookidooCategory("cat-1", "Desserts", "")),
                List.of()
        );
    }
}
