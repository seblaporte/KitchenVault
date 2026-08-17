package fr.seblaporte.kitchenvault.service;

import fr.seblaporte.kitchenvault.cookidoo.CookidooServiceClient;
import fr.seblaporte.kitchenvault.cookidoo.model.AddRecipesToCollectionRequest;
import fr.seblaporte.kitchenvault.cookidoo.model.CookidooCollection;
import fr.seblaporte.kitchenvault.cookidoo.model.CreateCollectionRequest;
import fr.seblaporte.kitchenvault.entity.Collection;
import fr.seblaporte.kitchenvault.entity.Recipe;
import fr.seblaporte.kitchenvault.entity.RecipeListRole;
import fr.seblaporte.kitchenvault.entity.RecipeListSettings;
import fr.seblaporte.kitchenvault.repository.CollectionRepository;
import fr.seblaporte.kitchenvault.repository.RecipeListSettingsRepository;
import fr.seblaporte.kitchenvault.repository.RecipeRepository;
import fr.seblaporte.kitchenvault.repository.RecipeSpecification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Manages the 3 role-based recipe lists (FAVORITES/DISCOVERY/REJECTED), each optionally
 * bound to a real Cookidoo custom collection. Every move writes to Cookidoo first and only
 * reflects the change locally once that write succeeds — the local {@link Collection}/
 * {@link fr.seblaporte.kitchenvault.entity.Chapter} state is never the source of truth.
 */
@Service
@Transactional(readOnly = true)
public class RecipeListService {

    private final RecipeListSettingsRepository recipeListSettingsRepository;
    private final CollectionRepository collectionRepository;
    private final RecipeRepository recipeRepository;
    private final CookidooServiceClient cookidooServiceClient;
    private final SyncService syncService;

    public RecipeListService(
            RecipeListSettingsRepository recipeListSettingsRepository,
            CollectionRepository collectionRepository,
            RecipeRepository recipeRepository,
            CookidooServiceClient cookidooServiceClient,
            SyncService syncService
    ) {
        this.recipeListSettingsRepository = recipeListSettingsRepository;
        this.collectionRepository = collectionRepository;
        this.recipeRepository = recipeRepository;
        this.cookidooServiceClient = cookidooServiceClient;
        this.syncService = syncService;
    }

    public List<RecipeListSettings> getSettings() {
        return recipeListSettingsRepository.findAll();
    }

    public RecipeListSettings getSettings(RecipeListRole role) {
        return getOrCreate(role);
    }

    @Transactional
    public RecipeListSettings updateLabel(RecipeListRole role, String label) {
        RecipeListSettings settings = getOrCreate(role);
        settings.setDisplayLabel(label);
        settings.setUpdatedAt(Instant.now());
        return recipeListSettingsRepository.save(settings);
    }

    @Transactional
    public RecipeListSettings bindExistingCollection(RecipeListRole role, String collectionId) {
        Collection collection = collectionRepository.findById(collectionId)
                .orElseThrow(() -> new NoSuchElementException("Collection not found: " + collectionId));

        recipeListSettingsRepository.findByCollectionId(collectionId)
                .filter(existing -> existing.getRole() != role)
                .ifPresent(existing -> {
                    throw new DuplicateBindingException(
                            "Cette collection est déjà rattachée au rôle " + existing.getRole());
                });

        RecipeListSettings settings = getOrCreate(role);
        settings.setCollection(collection);
        settings.setUpdatedAt(Instant.now());
        return recipeListSettingsRepository.save(settings);
    }

    @Transactional
    public RecipeListSettings createAndBindCollection(RecipeListRole role, String name) {
        CookidooCollection created = cookidooServiceClient.createCollection(new CreateCollectionRequest(name));
        syncService.upsertCollection(created);
        return bindExistingCollection(role, created.id());
    }

    public boolean recipeExists(String recipeId) {
        return recipeRepository.existsById(recipeId);
    }

    public Optional<RecipeListRole> getRoleOfRecipe(String recipeId) {
        for (RecipeListSettings settings : recipeListSettingsRepository.findAll()) {
            if (settings.getCollection() == null) {
                continue;
            }
            boolean inCollection = recipeRepository.exists(
                    RecipeSpecification.inCollections(List.of(settings.getCollection().getId()))
                            .and((root, query, cb) -> cb.equal(root.get("id"), recipeId)));
            if (inCollection) {
                return Optional.of(settings.getRole());
            }
        }
        return Optional.empty();
    }

    public List<Recipe> getRecipesForRole(RecipeListRole role) {
        return recipeListSettingsRepository.findById(role)
                .map(RecipeListSettings::getCollection)
                .map(collection -> recipeRepository.findAll(
                        RecipeSpecification.inCollections(List.of(collection.getId()))))
                .orElseGet(List::of);
    }

    public Set<String> getRejectedRecipeIds() {
        return getRecipesForRole(RecipeListRole.REJECTED).stream()
                .map(Recipe::getId)
                .collect(Collectors.toSet());
    }

    /**
     * Moves a recipe to {@code targetRole}. Writes to Cookidoo (remove from the current
     * collection, then add to the target one) before touching local state; if the Cookidoo
     * call fails, the exception propagates and no local write happens.
     */
    @Transactional
    public RecipeListRole moveRecipe(String recipeId, RecipeListRole targetRole) {
        if (!recipeRepository.existsById(recipeId)) {
            throw new NoSuchElementException("Recipe not found: " + recipeId);
        }

        RecipeListSettings targetSettings = recipeListSettingsRepository.findById(targetRole)
                .filter(settings -> settings.getCollection() != null)
                .orElseThrow(() -> new CollectionNotBoundException(
                        "Aucune collection Cookidoo n'est rattachée au rôle " + targetRole));

        Optional<RecipeListRole> currentRole = getRoleOfRecipe(recipeId);
        if (currentRole.isPresent() && currentRole.get() == targetRole) {
            return targetRole;
        }

        if (currentRole.isPresent()) {
            RecipeListSettings sourceSettings = recipeListSettingsRepository.findById(currentRole.get())
                    .orElseThrow();
            CookidooCollection sourceResult = cookidooServiceClient.removeRecipeFromCollection(
                    sourceSettings.getCollection().getId(), recipeId);
            syncService.upsertCollection(sourceResult);
        }

        CookidooCollection targetResult = cookidooServiceClient.addRecipesToCollection(
                targetSettings.getCollection().getId(), new AddRecipesToCollectionRequest(List.of(recipeId)));
        syncService.upsertCollection(targetResult);

        return targetRole;
    }

    private RecipeListSettings getOrCreate(RecipeListRole role) {
        return recipeListSettingsRepository.findById(role)
                .orElseGet(() -> {
                    RecipeListSettings settings = new RecipeListSettings(role);
                    settings.setDisplayLabel(role.name());
                    settings.setUpdatedAt(Instant.now());
                    return settings;
                });
    }

    public static class CollectionNotBoundException extends RuntimeException {
        public CollectionNotBoundException(String message) {
            super(message);
        }
    }

    public static class DuplicateBindingException extends RuntimeException {
        public DuplicateBindingException(String message) {
            super(message);
        }
    }
}
