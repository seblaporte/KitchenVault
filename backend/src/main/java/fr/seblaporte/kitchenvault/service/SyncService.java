package fr.seblaporte.kitchenvault.service;

import fr.seblaporte.kitchenvault.config.CookidooProperties;
import fr.seblaporte.kitchenvault.cookidoo.CookidooServiceClient;
import fr.seblaporte.kitchenvault.cookidoo.model.*;
import fr.seblaporte.kitchenvault.entity.*;
import fr.seblaporte.kitchenvault.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class SyncService {

    private static final Logger log = LoggerFactory.getLogger(SyncService.class);

    private final CookidooServiceClient cookidooServiceClient;
    private final RecipeRepository recipeRepository;
    private final CollectionRepository collectionRepository;
    private final CategoryRepository categoryRepository;
    private final SyncRunRepository syncRunRepository;
    private final CookidooProperties properties;

    public SyncService(
            CookidooServiceClient cookidooServiceClient,
            RecipeRepository recipeRepository,
            CollectionRepository collectionRepository,
            CategoryRepository categoryRepository,
            SyncRunRepository syncRunRepository,
            CookidooProperties properties
    ) {
        this.cookidooServiceClient = cookidooServiceClient;
        this.recipeRepository = recipeRepository;
        this.collectionRepository = collectionRepository;
        this.categoryRepository = categoryRepository;
        this.syncRunRepository = syncRunRepository;
        this.properties = properties;
    }

    /**
     * Triggers a sync asynchronously. Returns immediately with the created SyncRun.
     * Throws IllegalStateException if a sync is already running.
     */
    @Transactional
    public SyncRun triggerSync() {
        if (syncRunRepository.existsByStatus(SyncStatus.RUNNING)) {
            throw new IllegalStateException("A synchronization is already running");
        }
        SyncRun run = SyncRun.start();
        syncRunRepository.save(run);
        executeSyncAsync(run);
        return run;
    }

    @Async
    public void executeSyncAsync(SyncRun run) {
        executeSync(run);
    }

    @Scheduled(cron = "${cookidoo.sync.cron:0 0 3 * * *}")
    public void scheduledSync() {
        if (syncRunRepository.existsByStatus(SyncStatus.RUNNING)) {
            log.info("Scheduled sync skipped — a sync is already running");
            return;
        }
        log.info("Starting scheduled Cookidoo synchronization");
        SyncRun run = SyncRun.start();
        syncRunRepository.save(run);
        executeSync(run);
    }

    @Transactional
    public void executeSync(SyncRun run) {
        int collectionsSynced = 0;
        int recipesSynced = 0;

        try {
            log.info("Fetching collections from Cookidoo service");
            List<CookidooCollection> cookidooCollections = cookidooServiceClient.getCollections();

            Instant resyncThreshold = Instant.now()
                    .minus(properties.sync().resyncAfterHours(), ChronoUnit.HOURS);

            // Collect all unique recipe IDs from all collections
            Set<String> recipeIds = new HashSet<>();
            for (CookidooCollection cookidooCollection : cookidooCollections) {
                for (CookidooChapter chapter : cookidooCollection.chapters()) {
                    for (CookidooChapterRecipe chapterRecipe : chapter.recipes()) {
                        recipeIds.add(chapterRecipe.id());
                    }
                }
            }

            // Sync recipes (only if absent or outdated)
            for (String recipeId : recipeIds) {
                boolean needsSync = recipeRepository.findById(recipeId)
                        .map(r -> r.getLastSyncedAt().isBefore(resyncThreshold))
                        .orElse(true);

                if (needsSync) {
                    try {
                        CookidooRecipeDetails details = cookidooServiceClient.getRecipeById(recipeId);
                        upsertRecipe(details);
                        recipesSynced++;
                        log.debug("Synced recipe: {} ({})", details.name(), recipeId);
                    } catch (Exception e) {
                        log.warn("Failed to sync recipe {}: {}", recipeId, e.getMessage());
                    }
                }
            }

            // Sync collections
            for (CookidooCollection cookidooCollection : cookidooCollections) {
                upsertCollection(cookidooCollection);
                collectionsSynced++;
            }

            syncRunRepository.save(run);
            run.complete(collectionsSynced, recipesSynced);
            syncRunRepository.save(run);

            log.info("Sync completed: {} collections, {} recipes", collectionsSynced, recipesSynced);

        } catch (Exception e) {
            log.error("Sync failed: {}", e.getMessage(), e);
            run.fail(e.getMessage());
            syncRunRepository.save(run);
        }
    }

    private void upsertRecipe(CookidooRecipeDetails details) {
        Recipe recipe = recipeRepository.findById(details.id())
                .orElseGet(() -> new Recipe(details.id()));

        recipe.setName(details.name());
        recipe.setDifficulty(details.difficulty());
        recipe.setThumbnailUrl(details.thumbnail());
        recipe.setImageUrl(details.image());
        recipe.setUrl(details.url());
        recipe.setServingSize(details.servingSize());
        recipe.setActiveTimeMinutes(details.activeTime() / 60);
        recipe.setTotalTimeMinutes(details.totalTime() / 60);
        recipe.setNotes(new ArrayList<>(details.notes()));
        recipe.setUtensils(new ArrayList<>(details.utensils()));
        recipe.setLastSyncedAt(Instant.now());

        // Categories (upsert)
        Set<Category> categories = new HashSet<>();
        for (CookidooCategory cookidooCategory : details.categories()) {
            Category category = categoryRepository.findById(cookidooCategory.id())
                    .orElseGet(() -> new Category(cookidooCategory.id(), cookidooCategory.name()));
            category.setName(cookidooCategory.name());
            categoryRepository.save(category);
            categories.add(category);
        }
        recipe.setCategories(categories);

        // Ingredient groups (full replace)
        recipe.getIngredientGroups().clear();
        // The Python service returns a flat list of ingredients — we put them all in a single default group
        if (!details.ingredients().isEmpty()) {
            IngredientGroup group = new IngredientGroup(recipe, null, 0);
            List<Ingredient> ingredients = new ArrayList<>();
            int sortOrder = 0;
            for (CookidooIngredient ci : details.ingredients()) {
                ingredients.add(new Ingredient(ci.id(), group, ci.name(), ci.description(), sortOrder++));
            }
            group.setIngredients(ingredients);
            recipe.getIngredientGroups().add(group);
        }

        // Nutrition groups (full replace)
        recipe.getNutritionGroups().clear();
        for (CookidooNutritionGroup cng : details.nutritionGroups()) {
            for (CookidooRecipeNutrition rn : cng.recipeNutritions()) {
                NutritionGroup ng = new NutritionGroup(recipe, cng.name(), rn.quantity(), rn.unitNotation());
                List<Nutrition> nutritions = new ArrayList<>();
                for (CookidooNutrition cn : rn.nutritions()) {
                    nutritions.add(new Nutrition(ng, cn.type(), BigDecimal.valueOf(cn.number()), cn.unitType()));
                }
                ng.setNutritions(nutritions);
                recipe.getNutritionGroups().add(ng);
            }
        }

        recipeRepository.save(recipe);
    }

    private void upsertCollection(CookidooCollection cookidooCollection) {
        Collection collection = collectionRepository.findById(cookidooCollection.id())
                .orElseGet(() -> new Collection(cookidooCollection.id()));

        collection.setName(cookidooCollection.name());
        collection.setDescription(cookidooCollection.description());
        collection.setType(CollectionType.CUSTOM);
        collection.setLastSyncedAt(Instant.now());

        // Chapters (full replace)
        collection.getChapters().clear();
        int chapterOrder = 0;
        for (CookidooChapter cookidooChapter : cookidooCollection.chapters()) {
            Chapter chapter = new Chapter(collection, cookidooChapter.name(), chapterOrder++);
            List<Recipe> recipes = new ArrayList<>();
            for (CookidooChapterRecipe cr : cookidooChapter.recipes()) {
                recipeRepository.findById(cr.id()).ifPresent(recipes::add);
            }
            chapter.setRecipes(recipes);
            collection.getChapters().add(chapter);
        }

        collectionRepository.save(collection);
    }
}
