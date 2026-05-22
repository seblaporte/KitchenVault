package fr.seblaporte.kitchenvault.repository;

import fr.seblaporte.kitchenvault.entity.ShoppingListItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ShoppingListItemRepository extends JpaRepository<ShoppingListItem, UUID> {
}
