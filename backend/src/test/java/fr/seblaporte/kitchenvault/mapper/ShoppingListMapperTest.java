package fr.seblaporte.kitchenvault.mapper;

import fr.seblaporte.kitchenvault.entity.Recipe;
import fr.seblaporte.kitchenvault.entity.ShoppingCategory;
import fr.seblaporte.kitchenvault.entity.ShoppingList;
import fr.seblaporte.kitchenvault.entity.ShoppingListItem;
import fr.seblaporte.kitchenvault.entity.ShoppingListRecipe;
import fr.seblaporte.kitchenvault.generated.model.ShoppingListDto;
import fr.seblaporte.kitchenvault.generated.model.ShoppingListItemDto;
import fr.seblaporte.kitchenvault.generated.model.ShoppingListRecipeDto;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ShoppingListMapperTest {

    private final ShoppingListMapper mapper = new ShoppingListMapperImpl();

    // ─── toRecipeDto ──────────────────────────────────────────────────────────

    @Test
    void toRecipeDto_mapsAllFields() {
        ShoppingList list = new ShoppingList();
        Recipe recipe = makeRecipe("r-1", "Tarte aux pommes");
        ShoppingListRecipe selection = new ShoppingListRecipe(list, recipe);

        ShoppingListRecipeDto dto = mapper.toRecipeDto(selection);

        assertThat(dto.getRecipeId()).isEqualTo("r-1");
        assertThat(dto.getRecipeName()).isEqualTo("Tarte aux pommes");
        assertThat(dto.getRecipeIdSnapshot()).isEqualTo("r-1");
        assertThat(dto.getConsolidated()).isFalse();
        assertThat(dto.getAddedAt()).isNotNull();
        assertThat(dto.getThumbnailUrl()).isNull();
    }

    @Test
    void toRecipeDto_withThumbnailUrl_mapsIt() {
        ShoppingList list = new ShoppingList();
        Recipe recipe = makeRecipe("r-1", "Tarte");
        recipe.setThumbnailUrl("https://example.com/img.jpg");
        ShoppingListRecipe selection = new ShoppingListRecipe(list, recipe);

        ShoppingListRecipeDto dto = mapper.toRecipeDto(selection);

        assertThat(dto.getThumbnailUrl()).isEqualTo("https://example.com/img.jpg");
    }

    @Test
    void toRecipeDto_whenRecipeIsNull_thumbnailUrlIsNull() {
        ShoppingList list = new ShoppingList();
        Recipe recipe = makeRecipe("r-deleted", "Recette supprimée");
        ShoppingListRecipe selection = new ShoppingListRecipe(list, recipe);
        selection.setRecipe(null);

        ShoppingListRecipeDto dto = mapper.toRecipeDto(selection);

        assertThat(dto.getThumbnailUrl()).isNull();
    }

    @Test
    void toRecipeDto_whenNull_returnsNull() {
        assertThat(mapper.toRecipeDto(null)).isNull();
    }

    @Test
    void toRecipeDto_whenAddedAtIsNull_addedAtIsNull() {
        ShoppingListRecipe selection = new ShoppingListRecipe();
        selection.setRecipeIdSnapshot("r-1");
        selection.setRecipeNameSnapshot("Tarte");

        ShoppingListRecipeDto dto = mapper.toRecipeDto(selection);

        assertThat(dto.getAddedAt()).isNull();
    }

    // ─── toItemDto ────────────────────────────────────────────────────────────

    @Test
    void toItemDto_mapsAllFields() {
        ShoppingList list = new ShoppingList();
        ShoppingListItem item = new ShoppingListItem(
                list, "Carottes", "500g", ShoppingCategory.PRODUCE, List.of("r-1"), Map.of("r-1", "Tarte"), 0);

        ShoppingListItemDto dto = mapper.toItemDto(item);

        assertThat(dto.getName()).isEqualTo("Carottes");
        assertThat(dto.getQuantity()).isEqualTo("500g");
        assertThat(dto.getCategory()).isEqualTo(fr.seblaporte.kitchenvault.generated.model.ShoppingCategory.PRODUCE);
        assertThat(dto.getChecked()).isFalse();
        assertThat(dto.getSourceRecipeIds()).containsExactly("r-1");
        assertThat(dto.getCustom()).isFalse();
        assertThat(dto.getCreatedAt()).isNotNull();
    }

    @Test
    void toItemDto_whenNull_returnsNull() {
        assertThat(mapper.toItemDto(null)).isNull();
    }

    @Test
    void toItemDto_whenCreatedAtIsNull_createdAtIsNull() {
        ShoppingListItem item = new ShoppingListItem();
        item.setName("Test");
        item.setCategory(ShoppingCategory.OTHER);

        ShoppingListItemDto dto = mapper.toItemDto(item);

        assertThat(dto.getCreatedAt()).isNull();
    }

    @Test
    void toItemDto_whenSourceRecipeIdsIsNull_sourceRecipeIdsNotSet() {
        ShoppingListItem item = new ShoppingListItem();
        item.setName("Test");
        item.setCategory(ShoppingCategory.OTHER);
        item.setSourceRecipeIds(null);
        item.setSourceRecipeNames(null);

        ShoppingListItemDto dto = mapper.toItemDto(item);

        assertThat(dto.getSourceRecipeIds()).isNullOrEmpty();
        assertThat(dto.getSourceRecipeNames()).isNullOrEmpty();
    }

    // ─── toDto ────────────────────────────────────────────────────────────────

    @Test
    void toDto_whenConsolidatedAtIsSet_mapsIt() {
        ShoppingList list = new ShoppingList();
        list.setConsolidatedAt(Instant.now());

        ShoppingListDto dto = mapper.toDto(list);

        assertThat(dto.getConsolidatedAt()).isNotNull();
        assertThat(dto.getRecipes()).isEmpty();
        assertThat(dto.getItems()).isEmpty();
    }

    @Test
    void toDto_whenConsolidatedAtIsNull_returnsNull() {
        ShoppingList list = new ShoppingList();

        ShoppingListDto dto = mapper.toDto(list);

        assertThat(dto.getConsolidatedAt()).isNull();
    }

    @Test
    void toDto_whenNull_returnsNull() {
        assertThat(mapper.toDto(null)).isNull();
    }

    @Test
    void toDto_withRecipesAndItems_mapsCollections() {
        ShoppingList list = new ShoppingList();
        Recipe recipe = makeRecipe("r-1", "Tarte");
        ShoppingListRecipe selection = new ShoppingListRecipe(list, recipe);
        list.getRecipes().add(selection);
        ShoppingListItem item = new ShoppingListItem(list, "Oeufs", "3", ShoppingCategory.DAIRY, List.of(), Map.of(), 0);
        list.getItems().add(item);

        ShoppingListDto dto = mapper.toDto(list);

        assertThat(dto.getRecipes()).hasSize(1);
        assertThat(dto.getItems()).hasSize(1);
    }

    // ─── toGeneratedCategory ─────────────────────────────────────────────────

    @Test
    void toGeneratedCategory_whenNull_returnsOther() {
        assertThat(mapper.toGeneratedCategory(null))
                .isEqualTo(fr.seblaporte.kitchenvault.generated.model.ShoppingCategory.OTHER);
    }

    @Test
    void toGeneratedCategory_mapsAllValues() {
        assertThat(mapper.toGeneratedCategory(ShoppingCategory.PRODUCE)).isEqualTo(fr.seblaporte.kitchenvault.generated.model.ShoppingCategory.PRODUCE);
        assertThat(mapper.toGeneratedCategory(ShoppingCategory.MEAT)).isEqualTo(fr.seblaporte.kitchenvault.generated.model.ShoppingCategory.MEAT);
        assertThat(mapper.toGeneratedCategory(ShoppingCategory.DAIRY)).isEqualTo(fr.seblaporte.kitchenvault.generated.model.ShoppingCategory.DAIRY);
        assertThat(mapper.toGeneratedCategory(ShoppingCategory.BAKERY)).isEqualTo(fr.seblaporte.kitchenvault.generated.model.ShoppingCategory.BAKERY);
        assertThat(mapper.toGeneratedCategory(ShoppingCategory.GROCERY)).isEqualTo(fr.seblaporte.kitchenvault.generated.model.ShoppingCategory.GROCERY);
        assertThat(mapper.toGeneratedCategory(ShoppingCategory.FROZEN)).isEqualTo(fr.seblaporte.kitchenvault.generated.model.ShoppingCategory.FROZEN);
        assertThat(mapper.toGeneratedCategory(ShoppingCategory.SPICES)).isEqualTo(fr.seblaporte.kitchenvault.generated.model.ShoppingCategory.SPICES);
        assertThat(mapper.toGeneratedCategory(ShoppingCategory.OTHER)).isEqualTo(fr.seblaporte.kitchenvault.generated.model.ShoppingCategory.OTHER);
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private Recipe makeRecipe(String id, String name) {
        Recipe recipe = new Recipe(id);
        recipe.setName(name);
        return recipe;
    }
}
