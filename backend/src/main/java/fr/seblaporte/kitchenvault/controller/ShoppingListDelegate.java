package fr.seblaporte.kitchenvault.controller;

import fr.seblaporte.kitchenvault.entity.ShoppingListItem;
import fr.seblaporte.kitchenvault.generated.api.ShoppingListApiDelegate;
import fr.seblaporte.kitchenvault.generated.model.AddShoppingListItemRequest;
import fr.seblaporte.kitchenvault.generated.model.ShoppingCategory;
import fr.seblaporte.kitchenvault.generated.model.ShoppingListDto;
import fr.seblaporte.kitchenvault.generated.model.ShoppingListItemDto;
import fr.seblaporte.kitchenvault.service.ShoppingListService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class ShoppingListDelegate implements ShoppingListApiDelegate {

    private static final Logger log = LoggerFactory.getLogger(ShoppingListDelegate.class);

    private final ShoppingListService shoppingListService;

    public ShoppingListDelegate(ShoppingListService shoppingListService) {
        this.shoppingListService = shoppingListService;
    }

    @Override
    public ResponseEntity<ShoppingListDto> getShoppingList() {
        List<ShoppingListItem> items = shoppingListService.getList();
        return ResponseEntity.ok(toDto(items));
    }

    @Override
    public ResponseEntity<ShoppingListDto> addRecipeToShoppingList(String recipeId) {
        try {
            List<ShoppingListItem> items = shoppingListService.addRecipe(recipeId);
            return ResponseEntity.ok(toDto(items));
        } catch (NoSuchElementException e) {
            log.warn("Recipe not found for shopping list addition: {}", recipeId);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Shopping list consolidation error for recipe {}: {}", recipeId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
    }

    @Override
    public ResponseEntity<ShoppingListDto> removeRecipeFromShoppingList(String recipeId) {
        try {
            List<ShoppingListItem> items = shoppingListService.removeRecipe(recipeId);
            return ResponseEntity.ok(toDto(items));
        } catch (NoSuchElementException e) {
            log.warn("Recipe not found in shopping list for removal: {}", recipeId);
            return ResponseEntity.notFound().build();
        }
    }

    @Override
    public ResponseEntity<ShoppingListItemDto> addCustomShoppingListItem(AddShoppingListItemRequest request) {
        ShoppingListItem item = shoppingListService.addCustomItem(request.getName(), request.getQuantity());
        return ResponseEntity.status(HttpStatus.CREATED).body(toItemDto(item));
    }

    @Override
    public ResponseEntity<ShoppingListItemDto> toggleShoppingListItem(UUID itemId) {
        try {
            ShoppingListItem item = shoppingListService.toggleItem(itemId);
            return ResponseEntity.ok(toItemDto(item));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @Override
    public ResponseEntity<Void> deleteShoppingListItem(UUID itemId) {
        try {
            shoppingListService.deleteItem(itemId);
            return ResponseEntity.noContent().build();
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @Override
    public ResponseEntity<Void> clearCheckedItems() {
        shoppingListService.clearChecked();
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> clearShoppingList() {
        shoppingListService.clearAll();
        return ResponseEntity.noContent().build();
    }

    // ─── Mapping helpers ────────────────────────────────────────────────────────

    private ShoppingListDto toDto(List<ShoppingListItem> items) {
        Set<String> addedRecipeIds = items.stream()
                .filter(i -> !i.isCustom())
                .flatMap(i -> parseSourceIds(i.getSourceRecipeIds()).stream())
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));

        ShoppingListDto dto = new ShoppingListDto();
        dto.setItems(items.stream().map(this::toItemDto).toList());
        dto.setAddedRecipeIds(new java.util.ArrayList<>(addedRecipeIds));
        return dto;
    }

    private ShoppingListItemDto toItemDto(ShoppingListItem item) {
        ShoppingListItemDto dto = new ShoppingListItemDto();
        dto.setId(item.getId());
        dto.setName(item.getName());
        dto.setQuantity(item.getQuantity());
        dto.setCategory(ShoppingCategory.fromValue(item.getCategory().getValue()));
        dto.setChecked(item.isChecked());
        dto.setSourceRecipeIds(parseSourceIds(item.getSourceRecipeIds()));
        dto.setCustom(item.isCustom());
        dto.setCreatedAt(item.getCreatedAt() != null ? item.getCreatedAt().atOffset(ZoneOffset.UTC) : OffsetDateTime.now(ZoneOffset.UTC));
        dto.setUpdatedAt(item.getUpdatedAt() != null ? item.getUpdatedAt().atOffset(ZoneOffset.UTC) : OffsetDateTime.now(ZoneOffset.UTC));
        return dto;
    }

    private List<String> parseSourceIds(String json) {
        if (json == null || json.isBlank() || json.equals("[]")) {
            return List.of();
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}
