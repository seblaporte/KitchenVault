package fr.seblaporte.kitchenvault.repository;

import fr.seblaporte.kitchenvault.entity.ShoppingList;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ShoppingListRepository extends JpaRepository<ShoppingList, UUID> {

    @EntityGraph(attributePaths = {"recipes", "recipes.recipe", "items"})
    Optional<ShoppingList> findTopByOrderByCreatedAtAsc();
}
