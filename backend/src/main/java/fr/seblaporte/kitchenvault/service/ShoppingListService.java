package fr.seblaporte.kitchenvault.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.seblaporte.kitchenvault.ai.agent.ShoppingListConsolidationAgent;
import fr.seblaporte.kitchenvault.ai.agent.ShoppingListConsolidationResult;
import fr.seblaporte.kitchenvault.config.AiProperties;
import fr.seblaporte.kitchenvault.entity.Ingredient;
import fr.seblaporte.kitchenvault.entity.Recipe;
import fr.seblaporte.kitchenvault.entity.ShoppingCategory;
import fr.seblaporte.kitchenvault.entity.ShoppingListItem;
import fr.seblaporte.kitchenvault.repository.RecipeRepository;
import fr.seblaporte.kitchenvault.repository.ShoppingListItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ShoppingListService {

    private static final Logger log = LoggerFactory.getLogger(ShoppingListService.class);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

    private final ShoppingListItemRepository repository;
    private final RecipeRepository recipeRepository;
    private final ShoppingListConsolidationAgent consolidationAgent;
    private final AiProperties aiProperties;

    public ShoppingListService(ShoppingListItemRepository repository,
                               RecipeRepository recipeRepository,
                               ShoppingListConsolidationAgent consolidationAgent,
                               AiProperties aiProperties) {
        this.repository = repository;
        this.recipeRepository = recipeRepository;
        this.consolidationAgent = consolidationAgent;
        this.aiProperties = aiProperties;
    }

    public List<ShoppingListItem> getList() {
        return repository.findAllByOrderByCreatedAtAsc();
    }

    @Transactional
    public List<ShoppingListItem> addRecipe(String recipeId) {
        Recipe recipe = recipeRepository.findByIdWithIngredients(recipeId)
                .orElseThrow(() -> new NoSuchElementException("Recipe not found: " + recipeId));

        List<ShoppingListItem> allCurrent = repository.findAllByOrderByCreatedAtAsc();
        List<ShoppingListItem> currentNonCustom = allCurrent.stream().filter(i -> !i.isCustom()).toList();
        List<ShoppingListItem> customItems = allCurrent.stream().filter(ShoppingListItem::isCustom).toList();

        String currentListText = buildCurrentListText(currentNonCustom);
        String newIngredientsText = buildNewIngredientsText(recipe);
        String basicNecessitiesText = String.join(", ", aiProperties.shoppingList().basicNecessities());

        ShoppingListConsolidationResult result = consolidationAgent.consolidate(
                basicNecessitiesText,
                currentListText,
                recipe.getName(),
                newIngredientsText
        );

        // Replace all non-custom items with agent output, tracking sourceRecipeIds by name matching
        repository.deleteAll(currentNonCustom);

        List<ShoppingListItem> newItems = result.items().stream()
                .map(consolidated -> {
                    String normalizedName = normalize(consolidated.name());
                    List<String> previousSourceIds = currentNonCustom.stream()
                            .filter(prev -> normalize(prev.getName()).equals(normalizedName))
                            .findFirst()
                            .map(prev -> parseIds(prev.getSourceRecipeIds()))
                            .orElseGet(ArrayList::new);

                    Set<String> sourceIds = new LinkedHashSet<>(previousSourceIds);
                    sourceIds.add(recipeId);

                    ShoppingCategory category = ShoppingCategory.fromValue(consolidated.category());
                    return new ShoppingListItem(consolidated.name(), consolidated.quantity(), category, toJson(sourceIds));
                })
                .collect(Collectors.toList());

        repository.saveAll(newItems);

        return repository.findAllByOrderByCreatedAtAsc();
    }

    @Transactional
    public List<ShoppingListItem> removeRecipe(String recipeId) {
        List<ShoppingListItem> affected = repository.findBySourceRecipeId(recipeId);
        if (affected.isEmpty()) {
            throw new NoSuchElementException("Recipe not found in shopping list: " + recipeId);
        }

        List<ShoppingListItem> toDelete = new ArrayList<>();
        List<ShoppingListItem> toUpdate = new ArrayList<>();

        for (ShoppingListItem item : affected) {
            List<String> ids = parseIds(item.getSourceRecipeIds());
            ids.remove(recipeId);
            if (ids.isEmpty() && !item.isCustom()) {
                toDelete.add(item);
            } else {
                item.setSourceRecipeIds(toJson(ids));
                toUpdate.add(item);
            }
        }

        repository.deleteAll(toDelete);
        repository.saveAll(toUpdate);

        return repository.findAllByOrderByCreatedAtAsc();
    }

    @Transactional
    public ShoppingListItem addCustomItem(String name, String quantity) {
        ShoppingListItem item = ShoppingListItem.customItem(name, quantity);
        return repository.save(item);
    }

    @Transactional
    public ShoppingListItem toggleItem(UUID itemId) {
        ShoppingListItem item = repository.findById(itemId)
                .orElseThrow(() -> new NoSuchElementException("Shopping list item not found: " + itemId));
        item.setChecked(!item.isChecked());
        return repository.save(item);
    }

    @Transactional
    public void deleteItem(UUID itemId) {
        if (!repository.existsById(itemId)) {
            throw new NoSuchElementException("Shopping list item not found: " + itemId);
        }
        repository.deleteById(itemId);
    }

    @Transactional
    public void clearChecked() {
        List<ShoppingListItem> checked = repository.findAllByCheckedTrueOrderByCreatedAtAsc();
        repository.deleteAll(checked);
    }

    @Transactional
    public void clearAll() {
        repository.deleteAll();
    }

    // ─── Private helpers ────────────────────────────────────────────────────────

    private String buildCurrentListText(List<ShoppingListItem> items) {
        if (items.isEmpty()) {
            return "(liste vide)";
        }
        return items.stream()
                .map(item -> "- " + item.getName()
                        + (item.getQuantity() != null ? " : " + item.getQuantity() : "")
                        + " [" + item.getCategory().getValue() + "]")
                .collect(Collectors.joining("\n"));
    }

    private String buildNewIngredientsText(Recipe recipe) {
        return recipe.getIngredientGroups().stream()
                .flatMap(group -> group.getIngredients().stream())
                .map(this::formatIngredient)
                .collect(Collectors.joining("\n"));
    }

    private String formatIngredient(Ingredient ingredient) {
        String line = "- " + ingredient.getName();
        if (ingredient.getDescription() != null && !ingredient.getDescription().isBlank()) {
            line += " : " + ingredient.getDescription();
        }
        return line;
    }

    private static String normalize(String name) {
        if (name == null) return "";
        return name.trim().toLowerCase();
    }

    private List<String> parseIds(String json) {
        try {
            return new ArrayList<>(objectMapper.readValue(json, STRING_LIST));
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse sourceRecipeIds JSON: {}", json);
            return new ArrayList<>();
        }
    }

    private String toJson(Iterable<String> ids) {
        try {
            return objectMapper.writeValueAsString(ids);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }
}
