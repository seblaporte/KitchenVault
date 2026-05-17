package fr.seblaporte.kitchenvault.controller;

import fr.seblaporte.kitchenvault.entity.ShoppingList;
import fr.seblaporte.kitchenvault.entity.ShoppingListItem;
import fr.seblaporte.kitchenvault.entity.ShoppingListRecipe;
import fr.seblaporte.kitchenvault.generated.api.ShoppingListApiDelegate;
import fr.seblaporte.kitchenvault.generated.model.AddShoppingListItemRequest;
import fr.seblaporte.kitchenvault.generated.model.ShoppingListDto;
import fr.seblaporte.kitchenvault.generated.model.ShoppingListItemDto;
import fr.seblaporte.kitchenvault.generated.model.ShoppingListRecipeDto;
import fr.seblaporte.kitchenvault.mapper.ShoppingListMapper;
import fr.seblaporte.kitchenvault.service.ShoppingListService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.NoSuchElementException;
import java.util.UUID;

@Component
public class ShoppingListDelegate implements ShoppingListApiDelegate {

    private final ShoppingListService shoppingListService;
    private final ShoppingListMapper shoppingListMapper;

    public ShoppingListDelegate(ShoppingListService shoppingListService, ShoppingListMapper shoppingListMapper) {
        this.shoppingListService = shoppingListService;
        this.shoppingListMapper = shoppingListMapper;
    }

    @Override
    public ResponseEntity<ShoppingListDto> getShoppingList() {
        ShoppingList list = shoppingListService.getShoppingList();
        return ResponseEntity.ok(shoppingListMapper.toDto(list));
    }

    @Override
    public ResponseEntity<Void> clearShoppingList() {
        shoppingListService.clearList();
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<ShoppingListRecipeDto> addRecipeToSelection(String recipeId) {
        try {
            ShoppingListRecipe recipe = shoppingListService.addRecipeToSelection(recipeId);
            return ResponseEntity.ok(shoppingListMapper.toRecipeDto(recipe));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @Override
    public ResponseEntity<Void> removeRecipeFromSelection(String recipeId) {
        shoppingListService.removeRecipeFromSelection(recipeId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<ShoppingListDto> consolidateShoppingList() {
        ShoppingList list = shoppingListService.consolidate();
        return ResponseEntity.ok(shoppingListMapper.toDto(list));
    }

    @Override
    public ResponseEntity<ShoppingListItemDto> addShoppingListItem(AddShoppingListItemRequest request) {
        ShoppingListItem item = shoppingListService.addCustomItem(request.getName(), request.getQuantity());
        return ResponseEntity.status(201).body(shoppingListMapper.toItemDto(item));
    }

    @Override
    public ResponseEntity<ShoppingListItemDto> toggleShoppingListItem(UUID id) {
        try {
            ShoppingListItem item = shoppingListService.toggleItem(id);
            return ResponseEntity.ok(shoppingListMapper.toItemDto(item));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @Override
    public ResponseEntity<Void> deleteShoppingListItem(UUID id) {
        shoppingListService.deleteItem(id);
        return ResponseEntity.noContent().build();
    }
}
