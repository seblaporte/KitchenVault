package fr.seblaporte.kitchenvault.ai.tool;

import fr.seblaporte.kitchenvault.ai.service.UnitNormalizationService;
import fr.seblaporte.kitchenvault.ai.tool.ShoppingListTool.ShoppingListResult;
import fr.seblaporte.kitchenvault.config.AiProperties;
import fr.seblaporte.kitchenvault.entity.*;
import fr.seblaporte.kitchenvault.service.MealPlanService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ShoppingListToolTest {

    @Mock MealPlanService mealPlanService;
    @Mock AiProperties aiProperties;
    @Mock AiProperties.ShoppingListProperties shoppingListProperties;

    UnitNormalizationService normalizationService = new UnitNormalizationService();

    ShoppingListTool tool;

    @BeforeEach
    void setUp() {
        when(aiProperties.shoppingList()).thenReturn(shoppingListProperties);
        when(shoppingListProperties.basicNecessities()).thenReturn(List.of("sel", "poivre", "huile"));
        tool = new ShoppingListTool(mealPlanService, normalizationService, aiProperties);
    }

    @Test
    void generateShoppingList_noEntries_returnsWarning() {
        when(mealPlanService.getWeekPlan(LocalDate.of(2024, 4, 1))).thenReturn(List.of());

        ShoppingListResult result = tool.generateShoppingList("2024-04-01", false);

        assertThat(result.categories()).isEmpty();
        assertThat(result.warnings()).anyMatch(w -> w.contains("Aucun repas planifié"));
    }

    @Test
    void generateShoppingList_excludesBasicNecessities() {
        MealPlanEntry entry = entryWithIngredients(List.of(
                ingredient("sel", "2 g"),
                ingredient("tomate", "200 g")
        ));
        when(mealPlanService.getWeekPlan(LocalDate.of(2024, 4, 1))).thenReturn(List.of(entry));

        ShoppingListResult result = tool.generateShoppingList("2024-04-01", false);

        assertThat(result.categories()).flatMap(c -> c.items())
                .extracting(ShoppingListTool.ShoppingItem::ingredientName)
                .doesNotContain("sel")
                .contains("tomate");
    }

    @Test
    void generateShoppingList_includesBasicsWhenRequested() {
        MealPlanEntry entry = entryWithIngredients(List.of(ingredient("sel", "2 g")));
        when(mealPlanService.getWeekPlan(LocalDate.of(2024, 4, 1))).thenReturn(List.of(entry));

        ShoppingListResult result = tool.generateShoppingList("2024-04-01", true);

        assertThat(result.categories()).flatMap(c -> c.items())
                .extracting(ShoppingListTool.ShoppingItem::ingredientName)
                .contains("sel");
    }

    @Test
    void generateShoppingList_aggregatesSameIngredient() {
        MealPlanEntry e1 = entryWithIngredients(List.of(ingredient("carotte", "200 g")));
        MealPlanEntry e2 = entryWithIngredients(List.of(ingredient("carotte", "300 g")));
        when(mealPlanService.getWeekPlan(LocalDate.of(2024, 4, 1))).thenReturn(List.of(e1, e2));

        ShoppingListResult result = tool.generateShoppingList("2024-04-01", false);

        var items = result.categories().stream()
                .flatMap(c -> c.items().stream())
                .filter(i -> i.ingredientName().equals("carotte"))
                .toList();
        assertThat(items).hasSize(1);
        assertThat(items.get(0).quantity()).isEqualTo("500 g");
    }

    @Test
    void generateShoppingList_categorizesVegetablesCorrectly() {
        MealPlanEntry entry = entryWithIngredients(List.of(ingredient("tomate", "2 tomates")));
        when(mealPlanService.getWeekPlan(LocalDate.of(2024, 4, 1))).thenReturn(List.of(entry));

        ShoppingListResult result = tool.generateShoppingList("2024-04-01", false);

        assertThat(result.categories())
                .anyMatch(c -> c.name().equals("Légumes et herbes")
                        && c.items().stream().anyMatch(i -> i.ingredientName().equals("tomate")));
    }

    private MealPlanEntry entryWithIngredients(List<Ingredient> ingredients) {
        Recipe recipe = new Recipe("recipe-1");
        recipe.setName("Test Recipe");
        IngredientGroup group = new IngredientGroup(recipe, null, 0);
        group.setIngredients(ingredients);
        recipe.getIngredientGroups().add(group);

        MealPlanEntry entry = mock(MealPlanEntry.class);
        when(entry.getRecipe()).thenReturn(recipe);
        return entry;
    }

    private MealPlanEntry mock(Class<MealPlanEntry> cls) {
        return org.mockito.Mockito.mock(cls);
    }

    private Ingredient ingredient(String name, String description) {
        IngredientGroup group = new IngredientGroup(null, null, 0);
        return new Ingredient(name + "-id", group, name, description, 0);
    }
}
