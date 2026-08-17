package fr.seblaporte.kitchenvault.service;

import fr.seblaporte.kitchenvault.cookidoo.CookidooServiceClient;
import fr.seblaporte.kitchenvault.cookidoo.model.CookidooCollection;
import fr.seblaporte.kitchenvault.entity.Collection;
import fr.seblaporte.kitchenvault.entity.Recipe;
import fr.seblaporte.kitchenvault.entity.RecipeListRole;
import fr.seblaporte.kitchenvault.entity.RecipeListSettings;
import fr.seblaporte.kitchenvault.repository.CollectionRepository;
import fr.seblaporte.kitchenvault.repository.RecipeListSettingsRepository;
import fr.seblaporte.kitchenvault.repository.RecipeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecipeListServiceTest {

    @Mock RecipeListSettingsRepository recipeListSettingsRepository;
    @Mock CollectionRepository collectionRepository;
    @Mock RecipeRepository recipeRepository;
    @Mock CookidooServiceClient cookidooServiceClient;
    @Mock SyncService syncService;

    @InjectMocks RecipeListService recipeListService;

    @Test
    void getRoleOfRecipe_returnsRoleOfBoundCollectionContainingRecipe() {
        Collection collection = new Collection("col-1");
        RecipeListSettings discovery = new RecipeListSettings(RecipeListRole.DISCOVERY);
        discovery.setCollection(collection);
        RecipeListSettings favorites = new RecipeListSettings(RecipeListRole.FAVORITES); // unbound

        when(recipeListSettingsRepository.findAll()).thenReturn(List.of(favorites, discovery));
        when(recipeRepository.exists(any(Specification.class))).thenReturn(true);

        Optional<RecipeListRole> role = recipeListService.getRoleOfRecipe("r-1");

        assertThat(role).contains(RecipeListRole.DISCOVERY);
    }

    @Test
    void getRoleOfRecipe_returnsEmptyWhenNoBoundCollectionContainsRecipe() {
        when(recipeListSettingsRepository.findAll()).thenReturn(List.of());

        assertThat(recipeListService.getRoleOfRecipe("r-1")).isEmpty();
    }

    @Test
    void getRecipesForRole_returnsEmptyWhenNoCollectionBound() {
        when(recipeListSettingsRepository.findById(RecipeListRole.REJECTED))
                .thenReturn(Optional.of(new RecipeListSettings(RecipeListRole.REJECTED)));

        assertThat(recipeListService.getRecipesForRole(RecipeListRole.REJECTED)).isEmpty();
    }

    @Test
    void getRejectedRecipeIds_returnsIdsOfRecipesInRejectedCollection() {
        Collection rejectedCollection = new Collection("col-rejected");
        RecipeListSettings rejectedSettings = new RecipeListSettings(RecipeListRole.REJECTED);
        rejectedSettings.setCollection(rejectedCollection);
        when(recipeListSettingsRepository.findById(RecipeListRole.REJECTED)).thenReturn(Optional.of(rejectedSettings));
        when(recipeRepository.findAll(any(Specification.class)))
                .thenReturn(List.of(new Recipe("r-1"), new Recipe("r-2")));

        Set<String> ids = recipeListService.getRejectedRecipeIds();

        assertThat(ids).containsExactlyInAnyOrder("r-1", "r-2");
    }

    @Test
    void updateLabel_updatesExistingSettings() {
        RecipeListSettings settings = new RecipeListSettings(RecipeListRole.FAVORITES);
        settings.setDisplayLabel("Old");
        when(recipeListSettingsRepository.findById(RecipeListRole.FAVORITES)).thenReturn(Optional.of(settings));
        when(recipeListSettingsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RecipeListSettings result = recipeListService.updateLabel(RecipeListRole.FAVORITES, "Nouveau nom");

        assertThat(result.getDisplayLabel()).isEqualTo("Nouveau nom");
    }

    @Test
    void bindExistingCollection_bindsWhenCollectionUnbound() {
        Collection collection = new Collection("col-1");
        RecipeListSettings settings = new RecipeListSettings(RecipeListRole.FAVORITES);

        when(collectionRepository.findById("col-1")).thenReturn(Optional.of(collection));
        when(recipeListSettingsRepository.findByCollectionId("col-1")).thenReturn(Optional.empty());
        when(recipeListSettingsRepository.findById(RecipeListRole.FAVORITES)).thenReturn(Optional.of(settings));
        when(recipeListSettingsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RecipeListSettings result = recipeListService.bindExistingCollection(RecipeListRole.FAVORITES, "col-1");

        assertThat(result.getCollection()).isEqualTo(collection);
    }

    @Test
    void bindExistingCollection_collectionNotFound_throwsNoSuchElementException() {
        when(collectionRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> recipeListService.bindExistingCollection(RecipeListRole.FAVORITES, "missing"))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void bindExistingCollection_alreadyBoundToAnotherRole_throwsDuplicateBindingException() {
        Collection collection = new Collection("col-1");
        RecipeListSettings existingBinding = new RecipeListSettings(RecipeListRole.DISCOVERY);
        existingBinding.setCollection(collection);

        when(collectionRepository.findById("col-1")).thenReturn(Optional.of(collection));
        when(recipeListSettingsRepository.findByCollectionId("col-1")).thenReturn(Optional.of(existingBinding));

        assertThatThrownBy(() -> recipeListService.bindExistingCollection(RecipeListRole.FAVORITES, "col-1"))
                .isInstanceOf(RecipeListService.DuplicateBindingException.class);
    }

    @Test
    void createAndBindCollection_createsInCookidooSyncsLocallyThenBinds() {
        CookidooCollection created = new CookidooCollection("col-new", "Nouvelle", null, List.of());
        when(cookidooServiceClient.createCollection(any())).thenReturn(created);

        Collection localCollection = new Collection("col-new");
        when(collectionRepository.findById("col-new")).thenReturn(Optional.of(localCollection));
        when(recipeListSettingsRepository.findByCollectionId("col-new")).thenReturn(Optional.empty());
        when(recipeListSettingsRepository.findById(RecipeListRole.FAVORITES))
                .thenReturn(Optional.of(new RecipeListSettings(RecipeListRole.FAVORITES)));
        when(recipeListSettingsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RecipeListSettings result = recipeListService.createAndBindCollection(RecipeListRole.FAVORITES, "Nouvelle");

        verify(syncService).upsertCollection(created);
        assertThat(result.getCollection()).isEqualTo(localCollection);
    }

    @Test
    void moveRecipe_recipeNotFound_throwsNoSuchElementException() {
        when(recipeRepository.existsById("missing")).thenReturn(false);

        assertThatThrownBy(() -> recipeListService.moveRecipe("missing", RecipeListRole.FAVORITES))
                .isInstanceOf(NoSuchElementException.class);
        verifyNoInteractions(cookidooServiceClient);
    }

    @Test
    void moveRecipe_targetNotBound_throwsCollectionNotBoundException() {
        when(recipeRepository.existsById("r-1")).thenReturn(true);
        when(recipeListSettingsRepository.findById(RecipeListRole.FAVORITES))
                .thenReturn(Optional.of(new RecipeListSettings(RecipeListRole.FAVORITES)));

        assertThatThrownBy(() -> recipeListService.moveRecipe("r-1", RecipeListRole.FAVORITES))
                .isInstanceOf(RecipeListService.CollectionNotBoundException.class);
        verifyNoInteractions(cookidooServiceClient);
    }

    @Test
    void moveRecipe_alreadyInTargetRole_isIdempotentAndSkipsCookidooCalls() {
        Collection targetCollection = new Collection("col-fav");
        RecipeListSettings targetSettings = new RecipeListSettings(RecipeListRole.FAVORITES);
        targetSettings.setCollection(targetCollection);

        when(recipeRepository.existsById("r-1")).thenReturn(true);
        when(recipeListSettingsRepository.findById(RecipeListRole.FAVORITES)).thenReturn(Optional.of(targetSettings));
        when(recipeListSettingsRepository.findAll()).thenReturn(List.of(targetSettings));
        when(recipeRepository.exists(any(Specification.class))).thenReturn(true);

        RecipeListRole result = recipeListService.moveRecipe("r-1", RecipeListRole.FAVORITES);

        assertThat(result).isEqualTo(RecipeListRole.FAVORITES);
        verifyNoInteractions(cookidooServiceClient);
    }

    @Test
    void moveRecipe_fromDiscoveryToFavorites_removesFromSourceThenAddsToTarget() {
        Collection discoveryCollection = new Collection("col-discovery");
        Collection favoritesCollection = new Collection("col-fav");
        RecipeListSettings discoverySettings = new RecipeListSettings(RecipeListRole.DISCOVERY);
        discoverySettings.setCollection(discoveryCollection);
        RecipeListSettings favoritesSettings = new RecipeListSettings(RecipeListRole.FAVORITES);
        favoritesSettings.setCollection(favoritesCollection);

        when(recipeRepository.existsById("r-1")).thenReturn(true);
        when(recipeListSettingsRepository.findById(RecipeListRole.FAVORITES)).thenReturn(Optional.of(favoritesSettings));
        when(recipeListSettingsRepository.findById(RecipeListRole.DISCOVERY)).thenReturn(Optional.of(discoverySettings));
        // r-1 currently belongs to the DISCOVERY collection (first checked, exists() -> true)
        when(recipeListSettingsRepository.findAll()).thenReturn(List.of(discoverySettings, favoritesSettings));
        when(recipeRepository.exists(any(Specification.class))).thenReturn(true);

        CookidooCollection removeResult = new CookidooCollection("col-discovery", "Discovery", null, List.of());
        CookidooCollection addResult = new CookidooCollection("col-fav", "Favorites", null, List.of());
        when(cookidooServiceClient.removeRecipeFromCollection("col-discovery", "r-1")).thenReturn(removeResult);
        when(cookidooServiceClient.addRecipesToCollection(eq("col-fav"), any())).thenReturn(addResult);

        RecipeListRole result = recipeListService.moveRecipe("r-1", RecipeListRole.FAVORITES);

        assertThat(result).isEqualTo(RecipeListRole.FAVORITES);
        verify(cookidooServiceClient).removeRecipeFromCollection("col-discovery", "r-1");
        verify(cookidooServiceClient).addRecipesToCollection(eq("col-fav"), any());
        verify(syncService).upsertCollection(removeResult);
        verify(syncService).upsertCollection(addResult);
    }

    @Test
    void moveRecipe_addToTargetFails_revertsSourceRemovalInCookidoo() {
        Collection discoveryCollection = new Collection("col-discovery");
        Collection favoritesCollection = new Collection("col-fav");
        RecipeListSettings discoverySettings = new RecipeListSettings(RecipeListRole.DISCOVERY);
        discoverySettings.setCollection(discoveryCollection);
        RecipeListSettings favoritesSettings = new RecipeListSettings(RecipeListRole.FAVORITES);
        favoritesSettings.setCollection(favoritesCollection);

        when(recipeRepository.existsById("r-1")).thenReturn(true);
        when(recipeListSettingsRepository.findById(RecipeListRole.FAVORITES)).thenReturn(Optional.of(favoritesSettings));
        when(recipeListSettingsRepository.findById(RecipeListRole.DISCOVERY)).thenReturn(Optional.of(discoverySettings));
        when(recipeListSettingsRepository.findAll()).thenReturn(List.of(discoverySettings, favoritesSettings));
        when(recipeRepository.exists(any(Specification.class))).thenReturn(true);

        CookidooCollection removeResult = new CookidooCollection("col-discovery", "Discovery", null, List.of());
        CookidooCollection revertResult = new CookidooCollection("col-discovery", "Discovery (reverted)", null, List.of());
        when(cookidooServiceClient.removeRecipeFromCollection("col-discovery", "r-1")).thenReturn(removeResult);
        when(cookidooServiceClient.addRecipesToCollection(eq("col-fav"), any()))
                .thenThrow(new RestClientException("Cookidoo indisponible"));
        when(cookidooServiceClient.addRecipesToCollection(eq("col-discovery"), any())).thenReturn(revertResult);

        assertThatThrownBy(() -> recipeListService.moveRecipe("r-1", RecipeListRole.FAVORITES))
                .isInstanceOf(RestClientException.class);

        verify(cookidooServiceClient).removeRecipeFromCollection("col-discovery", "r-1");
        verify(cookidooServiceClient).addRecipesToCollection(eq("col-discovery"), any());
        verify(syncService).upsertCollection(removeResult);
        verify(syncService).upsertCollection(revertResult);
    }

    @Test
    void moveRecipe_addToTargetFails_compensationAlsoFails_doesNotSwallowOriginalException() {
        Collection discoveryCollection = new Collection("col-discovery");
        Collection favoritesCollection = new Collection("col-fav");
        RecipeListSettings discoverySettings = new RecipeListSettings(RecipeListRole.DISCOVERY);
        discoverySettings.setCollection(discoveryCollection);
        RecipeListSettings favoritesSettings = new RecipeListSettings(RecipeListRole.FAVORITES);
        favoritesSettings.setCollection(favoritesCollection);

        when(recipeRepository.existsById("r-1")).thenReturn(true);
        when(recipeListSettingsRepository.findById(RecipeListRole.FAVORITES)).thenReturn(Optional.of(favoritesSettings));
        when(recipeListSettingsRepository.findById(RecipeListRole.DISCOVERY)).thenReturn(Optional.of(discoverySettings));
        when(recipeListSettingsRepository.findAll()).thenReturn(List.of(discoverySettings, favoritesSettings));
        when(recipeRepository.exists(any(Specification.class))).thenReturn(true);

        when(cookidooServiceClient.removeRecipeFromCollection("col-discovery", "r-1"))
                .thenReturn(new CookidooCollection("col-discovery", "Discovery", null, List.of()));
        when(cookidooServiceClient.addRecipesToCollection(eq("col-fav"), any()))
                .thenThrow(new RestClientException("Cookidoo indisponible"));
        when(cookidooServiceClient.addRecipesToCollection(eq("col-discovery"), any()))
                .thenThrow(new RestClientException("Toujours indisponible"));

        assertThatThrownBy(() -> recipeListService.moveRecipe("r-1", RecipeListRole.FAVORITES))
                .isInstanceOf(RestClientException.class)
                .hasMessage("Cookidoo indisponible");
    }

    @Test
    void moveRecipe_fromUnclassifiedToFavorites_onlyCallsAdd() {
        Collection favoritesCollection = new Collection("col-fav");
        RecipeListSettings favoritesSettings = new RecipeListSettings(RecipeListRole.FAVORITES);
        favoritesSettings.setCollection(favoritesCollection);

        when(recipeRepository.existsById("r-1")).thenReturn(true);
        when(recipeListSettingsRepository.findById(RecipeListRole.FAVORITES)).thenReturn(Optional.of(favoritesSettings));
        when(recipeListSettingsRepository.findAll()).thenReturn(List.of(favoritesSettings));
        when(recipeRepository.exists(any(Specification.class))).thenReturn(false);

        CookidooCollection addResult = new CookidooCollection("col-fav", "Favorites", null, List.of());
        when(cookidooServiceClient.addRecipesToCollection(eq("col-fav"), any())).thenReturn(addResult);

        RecipeListRole result = recipeListService.moveRecipe("r-1", RecipeListRole.FAVORITES);

        assertThat(result).isEqualTo(RecipeListRole.FAVORITES);
        verify(cookidooServiceClient, never()).removeRecipeFromCollection(any(), any());
        verify(cookidooServiceClient).addRecipesToCollection(eq("col-fav"), any());
    }
}
