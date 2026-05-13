package fr.seblaporte.kitchenvault.service;

import fr.seblaporte.kitchenvault.ai.agent.ShoppingListConsolidationAgent;
import fr.seblaporte.kitchenvault.ai.agent.ShoppingListConsolidationResult;
import fr.seblaporte.kitchenvault.ai.agent.ShoppingListConsolidationResult.ConsolidatedItem;
import fr.seblaporte.kitchenvault.config.AiProperties;
import fr.seblaporte.kitchenvault.entity.*;
import fr.seblaporte.kitchenvault.repository.RecipeRepository;
import fr.seblaporte.kitchenvault.repository.ShoppingListItemRepository;
import fr.seblaporte.kitchenvault.repository.ShoppingListRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShoppingListServiceTest {

    @Mock ShoppingListRepository shoppingListRepository;
    @Mock ShoppingListItemRepository shoppingListItemRepository;
    @Mock RecipeRepository recipeRepository;
    @Mock ShoppingListConsolidationAgent consolidationAgent;
    @Mock AiProperties aiProperties;
    @Mock AiProperties.ShoppingListProperties shoppingListProperties;

    @InjectMocks ShoppingListService shoppingListService;

    private void stubAiProperties() {
        when(aiProperties.shoppingList()).thenReturn(shoppingListProperties);
        when(shoppingListProperties.basicNecessities()).thenReturn(List.of("sel", "poivre", "farine"));
    }

    // ─── getOrCreateActiveList ────────────────────────────────────────────────

    @Test
    void getOrCreateActiveList_whenNoList_createsAndSavesNewList() {
        when(shoppingListRepository.findTopByOrderByCreatedAtAsc()).thenReturn(Optional.empty());
        when(shoppingListRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ShoppingList result = shoppingListService.getOrCreateActiveList();

        assertThat(result).isNotNull();
        assertThat(result.getRecipes()).isEmpty();
        assertThat(result.getItems()).isEmpty();
        verify(shoppingListRepository).save(any(ShoppingList.class));
    }

    @Test
    void getOrCreateActiveList_whenListExists_returnsExistingWithoutSaving() {
        ShoppingList existing = new ShoppingList();
        when(shoppingListRepository.findTopByOrderByCreatedAtAsc()).thenReturn(Optional.of(existing));

        ShoppingList result = shoppingListService.getOrCreateActiveList();

        assertThat(result).isSameAs(existing);
        verify(shoppingListRepository, never()).save(any());
    }

    // ─── addRecipeToSelection ─────────────────────────────────────────────────

    @Test
    void addRecipeToSelection_whenRecipeExists_addsToList() {
        Recipe recipe = makeRecipe("r-1", "Tarte aux pommes");
        ShoppingList list = new ShoppingList();
        when(recipeRepository.findById("r-1")).thenReturn(Optional.of(recipe));
        when(shoppingListRepository.findTopByOrderByCreatedAtAsc()).thenReturn(Optional.of(list));
        when(shoppingListRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ShoppingListRecipe result = shoppingListService.addRecipeToSelection("r-1");

        assertThat(result.getRecipeNameSnapshot()).isEqualTo("Tarte aux pommes");
        assertThat(result.getRecipeIdSnapshot()).isEqualTo("r-1");
        assertThat(list.getRecipes()).hasSize(1);
        verify(shoppingListRepository).save(list);
    }

    @Test
    void addRecipeToSelection_whenRecipeNotFound_throwsNoSuchElement() {
        when(recipeRepository.findById("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> shoppingListService.addRecipeToSelection("unknown"))
                .isInstanceOf(NoSuchElementException.class);

        verify(shoppingListRepository, never()).save(any());
    }

    @Test
    void addRecipeToSelection_whenAlreadyInList_isIdempotent() {
        Recipe recipe = makeRecipe("r-1", "Tarte");
        ShoppingList list = new ShoppingList();
        ShoppingListRecipe existing = new ShoppingListRecipe(list, recipe);
        list.getRecipes().add(existing);

        when(recipeRepository.findById("r-1")).thenReturn(Optional.of(recipe));
        when(shoppingListRepository.findTopByOrderByCreatedAtAsc()).thenReturn(Optional.of(list));

        ShoppingListRecipe result = shoppingListService.addRecipeToSelection("r-1");

        assertThat(result).isSameAs(existing);
        assertThat(list.getRecipes()).hasSize(1);
        verify(shoppingListRepository, never()).save(any());
    }

    // ─── removeRecipeFromSelection ────────────────────────────────────────────

    @Test
    void removeRecipeFromSelection_whenInList_removesIt() {
        Recipe recipe = makeRecipe("r-1", "Tarte");
        ShoppingList list = new ShoppingList();
        list.getRecipes().add(new ShoppingListRecipe(list, recipe));
        when(shoppingListRepository.findTopByOrderByCreatedAtAsc()).thenReturn(Optional.of(list));
        when(shoppingListRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        shoppingListService.removeRecipeFromSelection("r-1");

        assertThat(list.getRecipes()).isEmpty();
        verify(shoppingListRepository).save(list);
    }

    @Test
    void removeRecipeFromSelection_whenNoActiveList_doesNothing() {
        when(shoppingListRepository.findTopByOrderByCreatedAtAsc()).thenReturn(Optional.empty());

        shoppingListService.removeRecipeFromSelection("r-1");

        verify(shoppingListRepository, never()).save(any());
    }

    // ─── consolidate ─────────────────────────────────────────────────────────

    @Test
    void consolidate_whenNoRecipes_throwsEmptySelectionException() {
        ShoppingList list = new ShoppingList();
        when(shoppingListRepository.findTopByOrderByCreatedAtAsc()).thenReturn(Optional.of(list));

        assertThatThrownBy(() -> shoppingListService.consolidate())
                .isInstanceOf(ShoppingListService.EmptySelectionException.class);

        verifyNoInteractions(consolidationAgent);
    }

    @Test
    void consolidate_callsAgentAndSavesItems() {
        stubAiProperties();
        ShoppingList list = new ShoppingList();
        Recipe recipe = makeRecipe("r-1", "Tarte");
        list.getRecipes().add(new ShoppingListRecipe(list, recipe));
        when(shoppingListRepository.findTopByOrderByCreatedAtAsc()).thenReturn(Optional.of(list));
        when(recipeRepository.findById("r-1")).thenReturn(Optional.of(recipe));
        when(consolidationAgent.consolidate(anyString())).thenReturn(
                new ShoppingListConsolidationResult(List.of(
                        new ConsolidatedItem("oeufs", "3 pièces", ShoppingCategory.DAIRY),
                        new ConsolidatedItem("lait", "500ml", ShoppingCategory.DAIRY)
                ))
        );
        when(shoppingListRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ShoppingList result = shoppingListService.consolidate();

        assertThat(result.getItems()).hasSize(2);
        assertThat(result.getItems()).extracting("name").containsExactlyInAnyOrder("oeufs", "lait");
        assertThat(result.getConsolidatedAt()).isNotNull();
        verify(consolidationAgent).consolidate(anyString());
    }

    @Test
    void consolidate_replacesConsolidatedItemsButKeepsCustom() {
        stubAiProperties();
        ShoppingList list = new ShoppingList();
        Recipe recipe = makeRecipe("r-1", "Tarte");
        list.getRecipes().add(new ShoppingListRecipe(list, recipe));

        ShoppingListItem customItem = new ShoppingListItem(list, "Pain de campagne", null, ShoppingCategory.BAKERY, List.of(), 0);
        customItem.setCustom(true);
        ShoppingListItem oldConsolidated = new ShoppingListItem(list, "Beurre", "100g", ShoppingCategory.DAIRY, List.of("r-1"), 1);
        list.getItems().addAll(List.of(customItem, oldConsolidated));

        when(shoppingListRepository.findTopByOrderByCreatedAtAsc()).thenReturn(Optional.of(list));
        when(recipeRepository.findById("r-1")).thenReturn(Optional.of(recipe));
        when(consolidationAgent.consolidate(anyString())).thenReturn(
                new ShoppingListConsolidationResult(List.of(
                        new ConsolidatedItem("oeufs", "2", ShoppingCategory.DAIRY)
                ))
        );
        when(shoppingListRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ShoppingList result = shoppingListService.consolidate();

        assertThat(result.getItems()).hasSize(2);
        assertThat(result.getItems()).anyMatch(i -> i.isCustom() && i.getName().equals("Pain de campagne"));
        assertThat(result.getItems()).anyMatch(i -> !i.isCustom() && i.getName().equals("oeufs"));
    }

    @Test
    void consolidate_skipsItemsWithNullOrBlankName() {
        stubAiProperties();
        ShoppingList list = new ShoppingList();
        Recipe recipe = makeRecipe("r-1", "Tarte");
        list.getRecipes().add(new ShoppingListRecipe(list, recipe));
        when(shoppingListRepository.findTopByOrderByCreatedAtAsc()).thenReturn(Optional.of(list));
        when(recipeRepository.findById("r-1")).thenReturn(Optional.of(recipe));
        when(consolidationAgent.consolidate(anyString())).thenReturn(
                new ShoppingListConsolidationResult(List.of(
                        new ConsolidatedItem(null, "100g", ShoppingCategory.PRODUCE),
                        new ConsolidatedItem("  ", "1", ShoppingCategory.PRODUCE),
                        new ConsolidatedItem("carottes", "300g", ShoppingCategory.PRODUCE)
                ))
        );
        when(shoppingListRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ShoppingList result = shoppingListService.consolidate();

        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().iterator().next().getName()).isEqualTo("carottes");
    }

    @Test
    void consolidate_marksAllRecipesAsConsolidated() {
        stubAiProperties();
        ShoppingList list = new ShoppingList();
        Recipe r1 = makeRecipe("r-1", "Tarte");
        Recipe r2 = makeRecipe("r-2", "Quiche");
        ShoppingListRecipe sel1 = new ShoppingListRecipe(list, r1);
        ShoppingListRecipe sel2 = new ShoppingListRecipe(list, r2);
        list.getRecipes().addAll(List.of(sel1, sel2));

        when(shoppingListRepository.findTopByOrderByCreatedAtAsc()).thenReturn(Optional.of(list));
        when(recipeRepository.findById("r-1")).thenReturn(Optional.of(r1));
        when(recipeRepository.findById("r-2")).thenReturn(Optional.of(r2));
        when(consolidationAgent.consolidate(anyString())).thenReturn(
                new ShoppingListConsolidationResult(List.of()));
        when(shoppingListRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        shoppingListService.consolidate();

        assertThat(sel1.isConsolidated()).isTrue();
        assertThat(sel2.isConsolidated()).isTrue();
    }

    @Test
    void consolidate_ingredientsTextIncludesExcludedList() {
        stubAiProperties();
        ShoppingList list = new ShoppingList();
        Recipe recipe = makeRecipe("r-1", "Tarte");
        list.getRecipes().add(new ShoppingListRecipe(list, recipe));
        when(shoppingListRepository.findTopByOrderByCreatedAtAsc()).thenReturn(Optional.of(list));
        when(recipeRepository.findById("r-1")).thenReturn(Optional.of(recipe));
        when(consolidationAgent.consolidate(anyString())).thenReturn(
                new ShoppingListConsolidationResult(List.of()));
        when(shoppingListRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        shoppingListService.consolidate();

        verify(consolidationAgent).consolidate(argThat(text ->
                text.contains("sel") && text.contains("poivre") && text.contains("farine")
        ));
    }

    // ─── addCustomItem ────────────────────────────────────────────────────────

    @Test
    void addCustomItem_setsCustomFlagAndCategory() {
        ShoppingList list = new ShoppingList();
        when(shoppingListRepository.findTopByOrderByCreatedAtAsc()).thenReturn(Optional.of(list));
        when(shoppingListRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ShoppingListItem result = shoppingListService.addCustomItem("Pain bio", "1 baguette");

        assertThat(result.isCustom()).isTrue();
        assertThat(result.getName()).isEqualTo("Pain bio");
        assertThat(result.getQuantity()).isEqualTo("1 baguette");
        assertThat(result.getCategory()).isEqualTo(ShoppingCategory.OTHER);
    }

    @Test
    void addCustomItem_sortOrderFollowsExistingItems() {
        ShoppingList list = new ShoppingList();
        ShoppingListItem existing = new ShoppingListItem(list, "Oeufs", "6", ShoppingCategory.DAIRY, List.of(), 5);
        list.getItems().add(existing);
        when(shoppingListRepository.findTopByOrderByCreatedAtAsc()).thenReturn(Optional.of(list));
        when(shoppingListRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ShoppingListItem result = shoppingListService.addCustomItem("Lait", "1L");

        assertThat(result.getSortOrder()).isEqualTo(6);
    }

    // ─── toggleItem ───────────────────────────────────────────────────────────

    @Test
    void toggleItem_whenNotChecked_setsCheckedTrue() {
        ShoppingList list = new ShoppingList();
        UUID itemId = UUID.randomUUID();
        ShoppingListItem item = new ShoppingListItem(list, "Carottes", "500g", ShoppingCategory.PRODUCE, List.of(), 0);
        when(shoppingListItemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(shoppingListItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ShoppingListItem result = shoppingListService.toggleItem(itemId);

        assertThat(result.isChecked()).isTrue();
    }

    @Test
    void toggleItem_whenChecked_setsCheckedFalse() {
        ShoppingList list = new ShoppingList();
        UUID itemId = UUID.randomUUID();
        ShoppingListItem item = new ShoppingListItem(list, "Carottes", "500g", ShoppingCategory.PRODUCE, List.of(), 0);
        item.setChecked(true);
        when(shoppingListItemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(shoppingListItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ShoppingListItem result = shoppingListService.toggleItem(itemId);

        assertThat(result.isChecked()).isFalse();
    }

    @Test
    void toggleItem_whenItemNotFound_throwsNoSuchElement() {
        UUID itemId = UUID.randomUUID();
        when(shoppingListItemRepository.findById(itemId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> shoppingListService.toggleItem(itemId))
                .isInstanceOf(NoSuchElementException.class);
    }

    // ─── deleteItem ───────────────────────────────────────────────────────────

    @Test
    void deleteItem_delegatesToRepository() {
        UUID itemId = UUID.randomUUID();

        shoppingListService.deleteItem(itemId);

        verify(shoppingListItemRepository).deleteById(itemId);
    }

    // ─── clearList ────────────────────────────────────────────────────────────

    @Test
    void clearList_removesAllRecipesAndItemsAndResetsDates() {
        ShoppingList list = new ShoppingList();
        Recipe recipe = makeRecipe("r-1", "Tarte");
        list.getRecipes().add(new ShoppingListRecipe(list, recipe));
        ShoppingListItem item = new ShoppingListItem(list, "Oeufs", "3", ShoppingCategory.DAIRY, List.of(), 0);
        list.getItems().add(item);
        list.setConsolidatedAt(Instant.now());

        when(shoppingListRepository.findTopByOrderByCreatedAtAsc()).thenReturn(Optional.of(list));
        when(shoppingListRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        shoppingListService.clearList();

        assertThat(list.getRecipes()).isEmpty();
        assertThat(list.getItems()).isEmpty();
        assertThat(list.getConsolidatedAt()).isNull();
        verify(shoppingListRepository).save(list);
    }

    @Test
    void clearList_whenNoActiveList_doesNothing() {
        when(shoppingListRepository.findTopByOrderByCreatedAtAsc()).thenReturn(Optional.empty());

        shoppingListService.clearList();

        verify(shoppingListRepository, never()).save(any());
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private Recipe makeRecipe(String id, String name) {
        Recipe recipe = new Recipe(id);
        recipe.setName(name);
        return recipe;
    }
}
