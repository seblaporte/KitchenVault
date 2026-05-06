package fr.seblaporte.kitchenvault.repository;

import fr.seblaporte.kitchenvault.entity.ShoppingListItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface ShoppingListItemRepository extends JpaRepository<ShoppingListItem, UUID> {

    List<ShoppingListItem> findAllByOrderByCreatedAtAsc();

    List<ShoppingListItem> findAllByCheckedTrueOrderByCreatedAtAsc();

    @Query(value = "SELECT * FROM shopping_list_item WHERE source_recipe_ids @> jsonb_build_array(CAST(:recipeId AS text))", nativeQuery = true)
    List<ShoppingListItem> findBySourceRecipeId(String recipeId);
}
