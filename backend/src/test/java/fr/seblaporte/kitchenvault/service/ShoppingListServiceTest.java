package fr.seblaporte.kitchenvault.service;

import fr.seblaporte.kitchenvault.ai.agent.ShoppingListConsolidationAgent;
import fr.seblaporte.kitchenvault.ai.agent.ShoppingListConsolidationResult;
import fr.seblaporte.kitchenvault.ai.agent.ShoppingListConsolidationResult.ConsolidatedItem;
import fr.seblaporte.kitchenvault.config.AiProperties;
import fr.seblaporte.kitchenvault.entity.Ingredient;
import fr.seblaporte.kitchenvault.entity.IngredientGroup;
import fr.seblaporte.kitchenvault.entity.Recipe;
import fr.seblaporte.kitchenvault.entity.ShoppingCategory;
import fr.seblaporte.kitchenvault.entity.ShoppingListItem;
import fr.seblaporte.kitchenvault.repository.RecipeRepository;
import fr.seblaporte.kitchenvault.repository.ShoppingListItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShoppingListServiceTest {

    @Mock ShoppingListItemRepository repository;
    @Mock RecipeRepository recipeRepository;
    @Mock ShoppingListConsolidationAgent consolidationAgent;
    @Mock AiProperties aiProperties;

    @InjectMocks ShoppingListService service;

    @BeforeEach
    void setUp() {
        lenient().when(aiProperties.shoppingList())
                .thenReturn(new AiProperties.ShoppingListProperties(List.of("eau", "sel")));
    }

    // ─── getList ──────────────────────────────────────────────────────────────

    @Test
    void getList_delegatesToRepository() {
        ShoppingListItem item = makeItem("carottes", "300g", ShoppingCategory.PRODUCE, "[]", false);
        when(repository.findAllByOrderByCreatedAtAsc()).thenReturn(List.of(item));

        List<ShoppingListItem> result = service.getList();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("carottes");
    }

    // ─── addRecipe ────────────────────────────────────────────────────────────

    @Test
    void addRecipe_recipeNotFound_throwsNoSuchElementException() {
        when(recipeRepository.findByIdWithIngredients("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addRecipe("unknown"))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void addRecipe_emptyCurrentList_agentCalledWithListeVidePlaceholder() {
        Recipe recipe = makeRecipe("r-1", "Tarte aux pommes", "pommes", "4 pièces");
        when(recipeRepository.findByIdWithIngredients("r-1")).thenReturn(Optional.of(recipe));
        when(repository.findAllByOrderByCreatedAtAsc()).thenReturn(List.of());
        when(consolidationAgent.consolidate(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new ShoppingListConsolidationResult(List.of()));
        when(repository.saveAll(any())).thenReturn(List.of());

        service.addRecipe("r-1");

        verify(consolidationAgent).consolidate(
                anyString(),
                argThat(s -> s.contains("(liste vide)")),
                eq("Tarte aux pommes"),
                anyString()
        );
    }

    @Test
    void addRecipe_agentResult_createsItemsWithRecipeIdInSourceRecipeIds() {
        Recipe recipe = makeRecipe("r-1", "Tarte", "pommes", "4 pièces");
        ShoppingListItem saved = makeItem("pommes", "4 pièces", ShoppingCategory.PRODUCE, "[\"r-1\"]", false);

        when(recipeRepository.findByIdWithIngredients("r-1")).thenReturn(Optional.of(recipe));
        when(repository.findAllByOrderByCreatedAtAsc())
                .thenReturn(List.of())
                .thenReturn(List.of(saved));
        when(consolidationAgent.consolidate(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new ShoppingListConsolidationResult(List.of(
                        new ConsolidatedItem("pommes", "4 pièces", "produce")
                )));
        when(repository.saveAll(any())).thenReturn(List.of(saved));

        List<ShoppingListItem> result = service.addRecipe("r-1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSourceRecipeIds()).contains("r-1");
    }

    @Test
    void addRecipe_existingNonCustomItem_accumulatesSourceRecipeIds() {
        Recipe recipe = makeRecipe("r-2", "Gratin", "carottes", "200g");
        ShoppingListItem existing = makeItem("carottes", "300g", ShoppingCategory.PRODUCE, "[\"r-1\"]", false);

        when(recipeRepository.findByIdWithIngredients("r-2")).thenReturn(Optional.of(recipe));
        when(repository.findAllByOrderByCreatedAtAsc())
                .thenReturn(List.of(existing))
                .thenReturn(List.of());
        when(consolidationAgent.consolidate(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new ShoppingListConsolidationResult(List.of(
                        new ConsolidatedItem("carottes", "500g", "produce")
                )));
        when(repository.saveAll(any())).thenReturn(List.of());

        service.addRecipe("r-2");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ShoppingListItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());

        List<ShoppingListItem> savedItems = captor.getValue();
        assertThat(savedItems).hasSize(1);
        assertThat(savedItems.get(0).getSourceRecipeIds()).contains("r-1").contains("r-2");
    }

    @Test
    void addRecipe_existingCustomItems_customItemsNotDeletedNorPassedToAgent() {
        Recipe recipe = makeRecipe("r-1", "Tarte", "pommes", "4 pièces");
        ShoppingListItem customItem = makeCustomItem("papier toilette", null);
        ShoppingListItem nonCustomItem = makeItem("farine", "200g", ShoppingCategory.GROCERY, "[\"r-0\"]", false);

        when(recipeRepository.findByIdWithIngredients("r-1")).thenReturn(Optional.of(recipe));
        when(repository.findAllByOrderByCreatedAtAsc())
                .thenReturn(List.of(customItem, nonCustomItem))
                .thenReturn(List.of(customItem));
        when(consolidationAgent.consolidate(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new ShoppingListConsolidationResult(List.of()));
        when(repository.saveAll(any())).thenReturn(List.of());

        service.addRecipe("r-1");

        // Only non-custom items are deleted
        verify(repository).deleteAll(argThat((List<ShoppingListItem> list) ->
                list.size() == 1 && !list.get(0).isCustom()));
    }

    // ─── removeRecipe ─────────────────────────────────────────────────────────

    @Test
    void removeRecipe_recipeNotInList_throwsNoSuchElementException() {
        when(repository.findBySourceRecipeId("r-unknown")).thenReturn(List.of());

        assertThatThrownBy(() -> service.removeRecipe("r-unknown"))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void removeRecipe_exclusiveItem_itemDeleted() {
        ShoppingListItem exclusive = makeItem("carottes", "300g", ShoppingCategory.PRODUCE, "[\"r-1\"]", false);
        when(repository.findBySourceRecipeId("r-1")).thenReturn(List.of(exclusive));
        when(repository.findAllByOrderByCreatedAtAsc()).thenReturn(List.of());

        service.removeRecipe("r-1");

        verify(repository).deleteAll(argThat((List<ShoppingListItem> list) ->
                list.size() == 1 && list.get(0).getName().equals("carottes")));
        verify(repository).saveAll(argThat((List<ShoppingListItem> list) -> list.isEmpty()));
    }

    @Test
    void removeRecipe_sharedItem_recipeIdRemovedAndItemSaved() {
        ShoppingListItem shared = makeItem("carottes", "500g", ShoppingCategory.PRODUCE, "[\"r-1\",\"r-2\"]", false);
        when(repository.findBySourceRecipeId("r-1")).thenReturn(List.of(shared));
        when(repository.findAllByOrderByCreatedAtAsc()).thenReturn(List.of(shared));

        service.removeRecipe("r-1");

        verify(repository).deleteAll(argThat((List<ShoppingListItem> list) -> list.isEmpty()));
        verify(repository).saveAll(argThat((List<ShoppingListItem> list) ->
                list.size() == 1 && list.get(0).getSourceRecipeIds().contains("r-2")
                        && !list.get(0).getSourceRecipeIds().contains("r-1")));
    }

    // ─── addCustomItem ────────────────────────────────────────────────────────

    @Test
    void addCustomItem_createsItemWithCustomTrueAndCategoryOther() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ShoppingListItem result = service.addCustomItem("papier toilette", null);

        assertThat(result.isCustom()).isTrue();
        assertThat(result.getCategory()).isEqualTo(ShoppingCategory.OTHER);
        assertThat(result.getSourceRecipeIds()).isEqualTo("[]");
        assertThat(result.getName()).isEqualTo("papier toilette");
    }

    // ─── toggleItem ───────────────────────────────────────────────────────────

    @Test
    void toggleItem_notFound_throwsNoSuchElementException() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.toggleItem(id))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void toggleItem_unchecked_togglesToTrue() {
        UUID id = UUID.randomUUID();
        ShoppingListItem item = makeItem("tomates", "400g", ShoppingCategory.PRODUCE, "[]", false);
        when(repository.findById(id)).thenReturn(Optional.of(item));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ShoppingListItem result = service.toggleItem(id);

        assertThat(result.isChecked()).isTrue();
    }

    // ─── deleteItem ───────────────────────────────────────────────────────────

    @Test
    void deleteItem_notFound_throwsNoSuchElementException() {
        UUID id = UUID.randomUUID();
        when(repository.existsById(id)).thenReturn(false);

        assertThatThrownBy(() -> service.deleteItem(id))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void deleteItem_callsDeleteById() {
        UUID id = UUID.randomUUID();
        when(repository.existsById(id)).thenReturn(true);

        service.deleteItem(id);

        verify(repository).deleteById(id);
    }

    // ─── clearChecked / clearAll ──────────────────────────────────────────────

    @Test
    void clearChecked_deletesAllCheckedItems() {
        ShoppingListItem checked = makeItem("oignons", "2 pièces", ShoppingCategory.PRODUCE, "[]", true);
        when(repository.findAllByCheckedTrueOrderByCreatedAtAsc()).thenReturn(List.of(checked));

        service.clearChecked();

        verify(repository).deleteAll(List.of(checked));
    }

    @Test
    void clearAll_deletesAllItems() {
        service.clearAll();

        verify(repository).deleteAll();
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private Recipe makeRecipe(String id, String name, String ingredientName, String ingredientDescription) {
        Recipe recipe = new Recipe(id);
        recipe.setName(name);
        recipe.setLastSyncedAt(java.time.Instant.now());
        IngredientGroup group = new IngredientGroup(recipe, null, 0);
        Ingredient ing = new Ingredient("cid-1", group, ingredientName, ingredientDescription, 0);
        group.setIngredients(List.of(ing));
        recipe.setIngredientGroups(List.of(group));
        return recipe;
    }

    private ShoppingListItem makeItem(String name, String quantity, ShoppingCategory category,
                                      String sourceRecipeIds, boolean checked) {
        ShoppingListItem item = new ShoppingListItem(name, quantity, category, sourceRecipeIds);
        item.setChecked(checked);
        return item;
    }

    private ShoppingListItem makeCustomItem(String name, String quantity) {
        return ShoppingListItem.customItem(name, quantity);
    }
}
