package fr.seblaporte.kitchenvault.repository;

import fr.seblaporte.kitchenvault.entity.*;
import org.assertj.db.type.Table;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ShoppingListItemRepositoryIT {

    @Autowired ShoppingListRepository shoppingListRepository;
    @Autowired ShoppingListItemRepository shoppingListItemRepository;
    @Autowired RecipeRepository recipeRepository;
    @Autowired DataSource dataSource;

    @BeforeEach
    void cleanUp() {
        shoppingListItemRepository.deleteAllInBatch();
        shoppingListRepository.deleteAllInBatch();
        recipeRepository.deleteAllInBatch();
    }

    @Test
    void save_persistsShoppingListWithTimestamps() {
        ShoppingList list = shoppingListRepository.save(new ShoppingList());

        org.assertj.db.api.Assertions.assertThat(new Table(dataSource, "shopping_list"))
                .hasNumberOfRows(1)
                .row(0)
                    .value("id").isEqualTo(list.getId().toString())
                    .value("consolidated_at").isNull();
    }

    @Test
    void findTopByOrderByCreatedAtAsc_returnsExistingList() {
        shoppingListRepository.save(new ShoppingList());

        var found = shoppingListRepository.findTopByOrderByCreatedAtAsc();

        assertThat(found).isPresent();
    }

    @Test
    void findTopByOrderByCreatedAtAsc_whenEmpty_returnsEmpty() {
        var found = shoppingListRepository.findTopByOrderByCreatedAtAsc();

        assertThat(found).isEmpty();
    }

    @Test
    void addItem_persistsItemWithCategory() {
        ShoppingList list = shoppingListRepository.save(new ShoppingList());
        ShoppingListItem item = new ShoppingListItem(list, "Carottes", "500g", ShoppingCategory.PRODUCE, List.of("r-1"), Map.of(), 0);

        shoppingListItemRepository.save(item);

        org.assertj.db.api.Assertions.assertThat(new Table(dataSource, "shopping_list_item"))
                .hasNumberOfRows(1)
                .row(0)
                    .value("name").isEqualTo("Carottes")
                    .value("quantity").isEqualTo("500g")
                    .value("category").isEqualTo("PRODUCE")
                    .value("checked").isEqualTo(false)
                    .value("custom").isEqualTo(false);
    }

    @Test
    void toggle_updatesCheckedStatus() {
        ShoppingList list = shoppingListRepository.save(new ShoppingList());
        ShoppingListItem item = shoppingListItemRepository.save(
                new ShoppingListItem(list, "Oeufs", "6", ShoppingCategory.DAIRY, List.of(), Map.of(), 0));

        item.setChecked(true);
        item.setUpdatedAt(Instant.now());
        shoppingListItemRepository.save(item);

        ShoppingListItem reloaded = shoppingListItemRepository.findById(item.getId()).orElseThrow();
        assertThat(reloaded.isChecked()).isTrue();
    }

    @Test
    void addRecipeToSelection_onDeleteRecipe_setsRecipeIdToNull() {
        Recipe recipe = saveRecipe("r-del", "Recette à supprimer");
        ShoppingList list = new ShoppingList();
        ShoppingListRecipe selection = new ShoppingListRecipe(list, recipe);
        list.getRecipes().add(selection);
        shoppingListRepository.save(list);

        recipeRepository.deleteById("r-del");

        org.assertj.db.api.Assertions.assertThat(new Table(dataSource, "shopping_list_recipe"))
                .hasNumberOfRows(1)
                .row(0)
                    .value("recipe_id").isNull()
                    .value("recipe_name_snapshot").isEqualTo("Recette à supprimer")
                    .value("recipe_id_snapshot").isEqualTo("r-del");
    }

    @Test
    void deleteShoppingList_cascadesToRecipesAndItems() {
        ShoppingList list = new ShoppingList();
        ShoppingListItem item = new ShoppingListItem(list, "Tomates", "1kg", ShoppingCategory.PRODUCE, List.of(), Map.of(), 0);
        list.getItems().add(item);
        ShoppingList saved = shoppingListRepository.save(list);

        shoppingListRepository.deleteById(saved.getId());

        assertThat(shoppingListItemRepository.findAll()).isEmpty();
    }

    @Test
    void customItem_isFlaggedAndPersisted() {
        ShoppingList list = shoppingListRepository.save(new ShoppingList());
        ShoppingListItem item = new ShoppingListItem(list, "Pain bio", null, ShoppingCategory.OTHER, List.of(), Map.of(), 0);
        item.setCustom(true);

        shoppingListItemRepository.save(item);

        org.assertj.db.api.Assertions.assertThat(new Table(dataSource, "shopping_list_item"))
                .hasNumberOfRows(1)
                .row(0)
                    .value("custom").isEqualTo(true)
                    .value("name").isEqualTo("Pain bio")
                    .value("quantity").isNull();
    }

    private Recipe saveRecipe(String id, String name) {
        Recipe recipe = new Recipe(id);
        recipe.setName(name);
        recipe.setLastSyncedAt(Instant.now());
        return recipeRepository.save(recipe);
    }
}
