package fr.seblaporte.kitchenvault.service;

import fr.seblaporte.kitchenvault.ai.agent.ShoppingListConsolidationAgent;
import fr.seblaporte.kitchenvault.ai.agent.ShoppingListConsolidationResult;
import fr.seblaporte.kitchenvault.config.AiProperties;
import fr.seblaporte.kitchenvault.entity.*;
import fr.seblaporte.kitchenvault.repository.RecipeRepository;
import fr.seblaporte.kitchenvault.repository.ShoppingListItemRepository;
import fr.seblaporte.kitchenvault.repository.ShoppingListRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class ShoppingListService {

    private static final Logger log = LoggerFactory.getLogger(ShoppingListService.class);

    private final ShoppingListRepository shoppingListRepository;
    private final ShoppingListItemRepository shoppingListItemRepository;
    private final RecipeRepository recipeRepository;
    private final ShoppingListConsolidationAgent consolidationAgent;
    private final AiProperties aiProperties;

    public ShoppingListService(ShoppingListRepository shoppingListRepository,
                               ShoppingListItemRepository shoppingListItemRepository,
                               RecipeRepository recipeRepository,
                               ShoppingListConsolidationAgent consolidationAgent,
                               AiProperties aiProperties) {
        this.shoppingListRepository = shoppingListRepository;
        this.shoppingListItemRepository = shoppingListItemRepository;
        this.recipeRepository = recipeRepository;
        this.consolidationAgent = consolidationAgent;
        this.aiProperties = aiProperties;
    }

    @Transactional
    public ShoppingList getOrCreateActiveList() {
        return shoppingListRepository.findTopByOrderByCreatedAtAsc()
                .orElseGet(() -> shoppingListRepository.save(new ShoppingList()));
    }

    @Transactional(readOnly = true)
    public ShoppingList getShoppingList() {
        return shoppingListRepository.findTopByOrderByCreatedAtAsc()
                .orElseGet(() -> {
                    ShoppingList empty = new ShoppingList();
                    // Return unsaved empty list so caller gets an empty response without DB write
                    return empty;
                });
    }

    @Transactional
    public ShoppingListRecipe addRecipeToSelection(String recipeId) {
        Recipe recipe = recipeRepository.findById(recipeId)
                .orElseThrow(() -> new NoSuchElementException("Recipe not found: " + recipeId));

        ShoppingList list = getOrCreateActiveList();

        // Idempotent: skip if recipe already in selection
        boolean alreadyAdded = list.getRecipes().stream()
                .anyMatch(r -> recipeId.equals(r.getRecipeIdSnapshot()));
        if (alreadyAdded) {
            return list.getRecipes().stream()
                    .filter(r -> recipeId.equals(r.getRecipeIdSnapshot()))
                    .findFirst()
                    .orElseThrow();
        }

        ShoppingListRecipe selection = new ShoppingListRecipe(list, recipe);
        list.getRecipes().add(selection);
        list.setUpdatedAt(Instant.now());
        shoppingListRepository.save(list);

        return selection;
    }

    @Transactional
    public void removeRecipeFromSelection(String recipeId) {
        shoppingListRepository.findTopByOrderByCreatedAtAsc().ifPresent(list -> {
            list.getRecipes().removeIf(r -> recipeId.equals(r.getRecipeIdSnapshot()));
            list.setUpdatedAt(Instant.now());
            shoppingListRepository.save(list);
        });
    }

    @Transactional
    public ShoppingList consolidate() {
        ShoppingList list = getOrCreateActiveList();

        if (list.getRecipes().isEmpty()) {
            throw new EmptySelectionException("Aucune recette dans la sélection. Ajoutez des recettes avant de consolider.");
        }

        String ingredientsText = buildIngredientsText(list);
        log.info("Consolidating shopping list with {} recipes", list.getRecipes().size());

        ShoppingListConsolidationResult result;
        try {
            result = consolidationAgent.consolidate(ingredientsText);
        } catch (Exception e) {
            log.error("Shopping list consolidation agent error: {}", e.getMessage(), e);
            throw e;
        }

        // Remove previously consolidated (non-custom) items, keep custom items
        list.getItems().removeIf(item -> !item.isCustom());

        // Add new consolidated items
        List<ShoppingListConsolidationResult.ConsolidatedItem> consolidatedItems =
                result.items() != null ? result.items() : List.of();

        int sortOrder = 0;
        for (ShoppingListConsolidationResult.ConsolidatedItem consolidated : consolidatedItems) {
            if (consolidated.name() == null || consolidated.name().isBlank()) {
                continue;
            }
            List<String> sourceIds = (consolidated.sourceRecipeIds() != null && !consolidated.sourceRecipeIds().isEmpty())
                    ? consolidated.sourceRecipeIds()
                    : list.getRecipes().stream().map(ShoppingListRecipe::getRecipeIdSnapshot).toList();
            Map<String, String> sourceNames = new HashMap<>();
            for (ShoppingListRecipe r : list.getRecipes()) {
                if (sourceIds.contains(r.getRecipeIdSnapshot())) {
                    sourceNames.put(r.getRecipeIdSnapshot(), r.getRecipeNameSnapshot());
                }
            }
            ShoppingListItem item = new ShoppingListItem(
                    list,
                    consolidated.name(),
                    consolidated.quantity(),
                    consolidated.category() != null ? consolidated.category() : ShoppingCategory.OTHER,
                    sourceIds,
                    sourceNames,
                    sortOrder++
            );
            list.getItems().add(item);
        }

        // Mark all recipes in selection as consolidated
        list.getRecipes().forEach(r -> r.setConsolidated(true));
        list.setConsolidatedAt(Instant.now());
        list.setUpdatedAt(Instant.now());

        return shoppingListRepository.save(list);
    }

    @Transactional
    public ShoppingListItem addCustomItem(String name, String quantity) {
        ShoppingList list = getOrCreateActiveList();

        int nextOrder = list.getItems().stream()
                .mapToInt(ShoppingListItem::getSortOrder)
                .max()
                .orElse(-1) + 1;

        ShoppingListItem item = new ShoppingListItem(list, name, quantity, ShoppingCategory.OTHER, List.of(), Map.of(), nextOrder);
        item.setCustom(true);
        list.getItems().add(item);
        list.setUpdatedAt(Instant.now());
        shoppingListRepository.save(list);

        return item;
    }

    @Transactional
    public ShoppingListItem toggleItem(UUID itemId) {
        ShoppingListItem item = shoppingListItemRepository.findById(itemId)
                .orElseThrow(() -> new NoSuchElementException("Shopping list item not found: " + itemId));
        item.setChecked(!item.isChecked());
        item.setUpdatedAt(Instant.now());
        return shoppingListItemRepository.save(item);
    }

    @Transactional
    public void deleteItem(UUID itemId) {
        shoppingListItemRepository.deleteById(itemId);
    }

    @Transactional
    public void clearList() {
        shoppingListRepository.findTopByOrderByCreatedAtAsc().ifPresent(list -> {
            list.getRecipes().clear();
            list.getItems().clear();
            list.setConsolidatedAt(null);
            list.setUpdatedAt(Instant.now());
            shoppingListRepository.save(list);
        });
    }

    private String buildIngredientsText(ShoppingList list) {
        List<String> excluded = aiProperties.shoppingList().basicNecessities();
        StringBuilder sb = new StringBuilder();
        sb.append("Produits de base à exclure absolument : ")
          .append(String.join(", ", excluded))
          .append("\n\n");
        sb.append("Ingrédients des recettes sélectionnées :\n\n");

        for (ShoppingListRecipe selection : list.getRecipes()) {
            String recipeId = selection.getRecipeIdSnapshot();
            sb.append("Recette : ").append(selection.getRecipeNameSnapshot())
              .append(" [ID: ").append(selection.getRecipeIdSnapshot()).append("]\n");

            recipeRepository.findById(recipeId).ifPresentOrElse(recipe -> {
                for (IngredientGroup group : recipe.getIngredientGroups()) {
                    for (Ingredient ingredient : group.getIngredients()) {
                        sb.append("- ").append(ingredient.getName());
                        if (ingredient.getDescription() != null && !ingredient.getDescription().isBlank()) {
                            sb.append(" (").append(ingredient.getDescription()).append(")");
                        }
                        sb.append("\n");
                    }
                }
            }, () -> log.warn("Recipe {} not found when building ingredients text", recipeId));

            sb.append("\n");
        }

        return sb.toString();
    }

    public static class EmptySelectionException extends RuntimeException {
        public EmptySelectionException(String message) {
            super(message);
        }
    }
}
